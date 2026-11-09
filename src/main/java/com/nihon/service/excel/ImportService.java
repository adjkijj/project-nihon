package com.nihon.service.excel;

import com.nihon.dto.excel.ImportCommitResult;
import com.nihon.dto.excel.ImportMode;
import com.nihon.dto.excel.ImportPreviewReport;
import com.nihon.dto.excel.ImportUndoPreview;
import com.nihon.dto.excel.ImportUndoResult;
import com.nihon.dto.excel.KanjiRow;
import com.nihon.dto.excel.KanjiWordRow;
import com.nihon.dto.excel.ParseResult;
import com.nihon.dto.excel.RowError;
import com.nihon.dto.excel.WordRow;
import com.nihon.entity.ImportAuditLog;
import com.nihon.entity.KanjiEntry;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.repository.KanjiEntryRepository;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VocabularyRepository;
import com.nihon.validation.KanjiLimits;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dịch vụ xử lý nhập dữ liệu (import) tệp tin Excel vào cơ sở dữ liệu.
 * <p>
 * Cung cấp hai chức năng chính:
 * <ul>
 *   <li>{@link #preview}: Kiểm tra an toàn, phân tích cú pháp và thống kê dữ liệu mới/trùng mà không ghi database.</li>
 *   <li>{@link #commit}: Chạy lại toàn bộ kiểm tra an toàn, phân tích và thực thi ghi dữ liệu trong một Transaction duy nhất.</li>
 * </ul>
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportService {

    public static final int MAX_EXPORT_ROWS_PER_SHEET = 50000;
    private static final int BATCH_SIZE = 50;
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern LAST_PARENTHESIS_PATTERN = Pattern.compile("[（(]([^()（）]*)[）)]\\s*$");

    private final ExcelUploadValidator validator;
    private final WordSheetParser wordSheetParser;
    private final KanjiSheetParser kanjiSheetParser;
    private final TopicRepository topicRepository;
    private final VocabularyRepository vocabularyRepository;
    private final KanjiEntryRepository kanjiEntryRepository;
    private final ImportAuditService auditService;

    /**
     * Lấy danh sách phân trang lịch sử các lần import đã thực hiện (mới nhất trước).
     */
    @Transactional(readOnly = true)
    public Page<ImportAuditLog> findHistory(Pageable pageable) {
        return auditService.findHistory(pageable);
    }

    /**
     * Xem trước kết quả import tệp Excel.
     * Chạy kiểm tra an toàn và phân tích dữ liệu, đối soát với database để đưa ra thống kê.
     * Hoàn toàn không ghi bất kỳ dữ liệu nào vào database (chế độ read-only).
     *
     * @param file tệp Excel tải lên
     * @return báo cáo xem trước {@link ImportPreviewReport}
     */
    @Transactional(readOnly = true)
    public ImportPreviewReport preview(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new UploadRejectedException("Tệp tin tải lên không hợp lệ hoặc rỗng.");
        }
        try {
            return preview(file.getBytes(), file.getOriginalFilename());
        } catch (IOException e) {
            log.error("Failed to read file bytes during preview for {}", sanitizeFilename(file.getOriginalFilename()), e);
            throw new UploadRejectedException("Không thể đọc nội dung tệp tin.", e);
        }
    }

    /**
     * Xem trước kết quả import từ mảng bytes trong bộ nhớ.
     *
     * @param bytes    dữ liệu nhị phân của tệp tin
     * @param filename tên tệp tin gốc
     * @return báo cáo xem trước {@link ImportPreviewReport}
     */
    @Transactional(readOnly = true)
    public ImportPreviewReport preview(byte[] bytes, String filename) {
        // 1. Chạy xác thực an toàn toàn diện
        validator.validateBytes(bytes, filename);

        // 2. Xác định các sheet có mặt trong workbook (hỗ trợ tệp có 1 trong 2 sheet hoặc cả hai)
        boolean hasWordSheet = false;
        boolean hasKanjiSheet = false;
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            hasWordSheet = ExcelCellUtils.findSheetCaseInsensitive(wb, "New Word") != null;
            hasKanjiSheet = ExcelCellUtils.findSheetCaseInsensitive(wb, "Kanji") != null;
        } catch (Exception e) {
            log.warn("Could not inspect sheet names in preview for {}", sanitizeFilename(filename));
        }

        List<RowError> allErrors = new ArrayList<>();
        if (!hasWordSheet && !hasKanjiSheet) {
            allErrors.add(new RowError(0, "Sheet", "Tệp tin phải chứa ít nhất một sheet 'New Word' hoặc 'Kanji'."));
        }

        // Chạy parser tương ứng với các sheet hiện diện
        ParseResult<WordRow> wordResult = hasWordSheet
                ? wordSheetParser.parse(new ByteArrayInputStream(bytes))
                : new ParseResult<>(java.util.Collections.emptyList(), java.util.Collections.emptyList());

        ParseResult<KanjiRow> kanjiResult = hasKanjiSheet
                ? kanjiSheetParser.parse(new ByteArrayInputStream(bytes))
                : new ParseResult<>(java.util.Collections.emptyList(), java.util.Collections.emptyList());

        // 3. Thu thập toàn bộ lỗi hàng nếu có và kiểm tra độ dài cột trước khi đối soát
        allErrors.addAll(wordResult.errors());
        allErrors.addAll(kanjiResult.errors());
        validateEntityColumnLimits(wordResult.rows(), kanjiResult.rows(), allErrors);

        // 4. Đối soát dữ liệu để thống kê số lượng mới và trùng lặp
        Set<String> encounteredTopics = new HashSet<>();
        int newTopicsCount = 0;

        // Thống kê chủ đề mới
        for (WordRow row : wordResult.rows()) {
            String normTopic = normalizeTopic(row.topic());
            String key = normTopic.toLowerCase(Locale.ROOT);
            if (encounteredTopics.add(key)) {
                if (topicRepository.findByNameIgnoreCase(normTopic).isEmpty()) {
                    newTopicsCount++;
                }
            }
        }
        for (KanjiRow row : kanjiResult.rows()) {
            String normTopic = normalizeTopic(row.topic());
            String key = normTopic.toLowerCase(Locale.ROOT);
            if (encounteredTopics.add(key)) {
                if (topicRepository.findByNameIgnoreCase(normTopic).isEmpty()) {
                    newTopicsCount++;
                }
            }
        }

        // Thống kê từ vựng New Word
        int newWordsCount = 0;
        int duplicateWordsCount = 0;
        Set<String> seenWordsInFile = new HashSet<>();
        for (WordRow row : wordResult.rows()) {
            String normTopic = normalizeTopic(row.topic());
            String wordKey = normTopic.toLowerCase(Locale.ROOT) + ":" + row.japanese().trim();

            if (!seenWordsInFile.add(wordKey)) {
                duplicateWordsCount++;
                continue;
            }

            Optional<Topic> topicOpt = topicRepository.findByNameIgnoreCase(normTopic);
            if (topicOpt.isPresent() && vocabularyRepository.existsByTopicIdAndJapaneseText(topicOpt.get().getId(), row.japanese())) {
                duplicateWordsCount++;
            } else {
                newWordsCount++;
            }
        }

        // Thống kê Kanji
        int newKanjiCount = 0;
        int duplicateKanjiCount = 0;
        Set<String> seenKanjiInFile = new HashSet<>();
        for (KanjiRow row : kanjiResult.rows()) {
            String kanjiChar = row.kanji().trim();
            if (!seenKanjiInFile.add(kanjiChar)) {
                duplicateKanjiCount++;
            } else if (kanjiEntryRepository.findByCharacter(kanjiChar).isPresent()) {
                duplicateKanjiCount++;
            } else {
                newKanjiCount++;
            }
        }

        // Thống kê từ cần nhớ của Kanji (được lưu vào bảng từ vựng chung vocabulary)
        int newKanjiWordsCount = 0;
        int duplicateKanjiWordsCount = 0;
        for (KanjiRow row : kanjiResult.rows()) {
            String normTopic = normalizeTopic(row.topic());
            Optional<Topic> topicOpt = topicRepository.findByNameIgnoreCase(normTopic);

            for (KanjiWordRow kwRow : row.words()) {
                String kwJapanese = kwRow.japanese().trim();
                String kwKey = normTopic.toLowerCase(Locale.ROOT) + ":" + kwJapanese;
                if (!seenWordsInFile.add(kwKey)) {
                    duplicateKanjiWordsCount++;
                } else if (topicOpt.isPresent() && vocabularyRepository.existsByTopicIdAndJapaneseText(topicOpt.get().getId(), kwJapanese)) {
                    duplicateKanjiWordsCount++;
                } else {
                    newKanjiWordsCount++;
                }
            }
        }

        int totalNewWordsCount = newWordsCount + newKanjiWordsCount;
        int totalDuplicateWordsCount = duplicateWordsCount + duplicateKanjiWordsCount;

        return new ImportPreviewReport(
                newTopicsCount,
                totalNewWordsCount,
                totalDuplicateWordsCount,
                newKanjiCount,
                duplicateKanjiCount,
                newKanjiWordsCount,
                duplicateKanjiWordsCount,
                allErrors
        );
    }

    /**
     * Thực hiện commit import tệp Excel vào cơ sở dữ liệu.
     * Chạy lại toàn bộ kiểm tra an toàn và parser, không tin cậy kết quả preview từ client.
     * Toàn bộ quá trình nằm trong một Transaction duy nhất; nếu có bất kỳ lỗi hàng nào sẽ hoàn tác toàn bộ.
     *
     * @param file   tệp Excel tải lên
     * @param mode   chế độ import (SKIP_DUPLICATES hoặc UPDATE_EXISTING)
     * @param userId mã định danh người dùng thực hiện commit
     * @return kết quả commit {@link ImportCommitResult}
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportCommitResult commit(MultipartFile file, ImportMode mode, String userId) {
        if (file == null || file.isEmpty()) {
            throw new UploadRejectedException("Tệp tin tải lên không hợp lệ hoặc rỗng.");
        }
        try {
            return commit(file.getBytes(), file.getOriginalFilename(), mode, userId);
        } catch (IOException e) {
            log.error("Failed to read file bytes during commit for {}", sanitizeFilename(file.getOriginalFilename()), e);
            throw new UploadRejectedException("Không thể đọc nội dung tệp tin.", e);
        }
    }

    /**
     * Thực hiện commit import từ mảng bytes trong bộ nhớ.
     *
     * @param bytes    dữ liệu nhị phân của tệp tin
     * @param filename tên tệp tin gốc
     * @param mode     chế độ import (mặc định SKIP_DUPLICATES nếu null)
     * @param userId   mã người dùng (mặc định "anonymous" nếu null)
     * @return kết quả commit {@link ImportCommitResult}
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportCommitResult commit(byte[] bytes, String filename, ImportMode mode, String userId) {
        ImportMode effectiveMode = (mode != null) ? mode : ImportMode.SKIP_DUPLICATES;
        String cleanFileName = sanitizeFilename(filename);
        String fileHash = calculateSha256(bytes);
        String effectiveUserId = (userId != null && !userId.isBlank()) ? userId : "anonymous";

        // 1. Chạy lại kiểm tra an toàn toàn diện (không tin client)
        try {
            validator.validateBytes(bytes, cleanFileName);
        } catch (UploadRejectedException e) {
            log.warn("Commit rejected by validator: file='{}', reason='{}'",
                    ExcelCellUtils.sanitizeForLog(cleanFileName),
                    ExcelCellUtils.sanitizeForLog(e.getMessage()));
            auditService.log(effectiveUserId, cleanFileName, fileHash, 0, 0, 0, "REJECTED", e.getMessage());
            throw e;
        }

        // 2. Xác định các sheet có mặt trong workbook
        boolean hasWordSheet = false;
        boolean hasKanjiSheet = false;
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            hasWordSheet = ExcelCellUtils.findSheetCaseInsensitive(wb, "New Word") != null;
            hasKanjiSheet = ExcelCellUtils.findSheetCaseInsensitive(wb, "Kanji") != null;
        } catch (Exception e) {
            log.warn("Could not inspect sheet names in commit for {}", cleanFileName);
        }

        List<RowError> allErrors = new ArrayList<>();
        if (!hasWordSheet && !hasKanjiSheet) {
            allErrors.add(new RowError(0, "Sheet", "Tệp tin phải chứa ít nhất một sheet 'New Word' hoặc 'Kanji'."));
        }

        // Chạy lại parser tương ứng
        ParseResult<WordRow> wordResult = hasWordSheet
                ? wordSheetParser.parse(new ByteArrayInputStream(bytes))
                : new ParseResult<>(java.util.Collections.emptyList(), java.util.Collections.emptyList());

        ParseResult<KanjiRow> kanjiResult = hasKanjiSheet
                ? kanjiSheetParser.parse(new ByteArrayInputStream(bytes))
                : new ParseResult<>(java.util.Collections.emptyList(), java.util.Collections.emptyList());

        // 3. Thu thập toàn bộ lỗi hàng và kiểm tra độ dài cột trước khi lưu vào DB
        allErrors.addAll(wordResult.errors());
        allErrors.addAll(kanjiResult.errors());
        validateEntityColumnLimits(wordResult.rows(), kanjiResult.rows(), allErrors);

        // 4. Nếu có bất kỳ lỗi hàng nào -> Ghi audit log thất bại và ném ngoại lệ để rollback toàn bộ transaction
        if (!allErrors.isEmpty()) {
            log.warn("Commit rejected: file '{}' contains {} row errors. Rolling back transaction.", cleanFileName, allErrors.size());
            auditService.log(effectiveUserId, cleanFileName, fileHash, 0, 0, 0, "FAILED",
                    "Tệp tin chứa " + allErrors.size() + " lỗi dòng dữ liệu.");
            throw new ImportRollbackException("Tệp tin chứa " + allErrors.size() + " lỗi dòng dữ liệu. Giao dịch đã được hoàn tác hoàn toàn.", allErrors);
        }

        try {
            // Bộ đệm chủ đề (Topic Cache) để tối ưu truy vấn và thống nhất tên theo chuẩn hóa NFC hoa thường
            Map<String, Topic> topicMap = new HashMap<>();
            int nextSortOrder = topicRepository.findMaxSortOrder() + 1;
            int topicsAdded = 0;
            List<Topic> newlyAddedTopics = new ArrayList<>();
            List<Vocabulary> newlyAddedVocabs = new ArrayList<>();
            List<KanjiEntry> newlyAddedKanjis = new ArrayList<>();

            // Xử lý nạp hoặc tạo mới Topic cho các từ vựng
            for (WordRow row : wordResult.rows()) {
                String normTopic = normalizeTopic(row.topic());
                String key = normTopic.toLowerCase(Locale.ROOT);
                if (!topicMap.containsKey(key)) {
                    Optional<Topic> existing = topicRepository.findByNameIgnoreCase(normTopic);
                    if (existing.isPresent()) {
                        topicMap.put(key, existing.get());
                    } else {
                        Topic newTopic = new Topic();
                        newTopic.setName(normTopic);
                        newTopic.setSortOrder(nextSortOrder++);
                        newTopic.setKanjiSection(false);
                        newTopic = topicRepository.save(newTopic);
                        newlyAddedTopics.add(newTopic);
                        topicsAdded++;
                        topicMap.put(key, newTopic);
                    }
                }
            }

            // Xử lý nạp hoặc tạo mới Topic cho Kanji
            for (KanjiRow row : kanjiResult.rows()) {
                String normTopic = normalizeTopic(row.topic());
                String key = normTopic.toLowerCase(Locale.ROOT);
                if (!topicMap.containsKey(key)) {
                    Optional<Topic> existing = topicRepository.findByNameIgnoreCase(normTopic);
                    if (existing.isPresent()) {
                        topicMap.put(key, existing.get());
                    } else {
                        Topic newTopic = new Topic();
                        newTopic.setName(normTopic);
                        newTopic.setSortOrder(nextSortOrder++);
                        newTopic.setKanjiSection(true);
                        newTopic = topicRepository.save(newTopic);
                        newlyAddedTopics.add(newTopic);
                        topicsAdded++;
                        topicMap.put(key, newTopic);
                    }
                }
            }

            // 5. Ghi dữ liệu từ vựng (New Word)
            int wordsAdded = 0;
            int wordsUpdated = 0;
            int wordsSkipped = 0;
            List<Vocabulary> vocabBatch = new ArrayList<>();
            Set<String> processedVocabKeysInBatch = new HashSet<>();

            for (WordRow row : wordResult.rows()) {
                String normTopic = normalizeTopic(row.topic());
                Topic topic = topicMap.get(normTopic.toLowerCase(Locale.ROOT));
                String vocabKey = topic.getId() + ":" + row.japanese().trim();

                Optional<Vocabulary> existingOpt = vocabularyRepository.findByTopicIdAndJapaneseText(topic.getId(), row.japanese());

                if (existingOpt.isPresent()) {
                    if (effectiveMode == ImportMode.UPDATE_EXISTING) {
                        Vocabulary existing = existingOpt.get();
                        if (row.romaji() != null) {
                            existing.setRomaji(row.romaji());
                        }
                        if (row.meaningVi() != null) {
                            existing.setMeaningVi(row.meaningVi());
                        }
                        existing.setReading(extractReading(row.japanese()));
                        existing.setHasKanji(containsKanji(row.japanese()));
                        vocabBatch.add(existing);
                        wordsUpdated++;
                    } else {
                        wordsSkipped++;
                    }
                } else if (!processedVocabKeysInBatch.add(vocabKey)) {
                    // Trùng lặp ngay trong cùng một tệp tin
                    if (effectiveMode == ImportMode.UPDATE_EXISTING) {
                        wordsUpdated++;
                    } else {
                        wordsSkipped++;
                    }
                } else {
                    Vocabulary newVocab = new Vocabulary();
                    newVocab.setTopic(topic);
                    newVocab.setJapaneseText(row.japanese());
                    newVocab.setRomaji(row.romaji());
                    newVocab.setMeaningVi(row.meaningVi());
                    newVocab.setReading(extractReading(row.japanese()));
                    newVocab.setHasKanji(containsKanji(row.japanese()));
                    newVocab.setSourceLine(null);
                    vocabBatch.add(newVocab);
                    newlyAddedVocabs.add(newVocab);
                    wordsAdded++;
                }

                if (vocabBatch.size() >= BATCH_SIZE) {
                    vocabularyRepository.saveAll(vocabBatch);
                    vocabBatch.clear();
                }
            }
            if (!vocabBatch.isEmpty()) {
                vocabularyRepository.saveAll(vocabBatch);
                vocabBatch.clear();
            }

            // 6. Ghi dữ liệu Kanji và từ cần nhớ của Kanji
            int kanjiAdded = 0;
            int kanjiUpdated = 0;
            int kanjiSkipped = 0;
            int kanjiWordsAdded = 0;
            int kanjiWordsUpdated = 0;
            int kanjiWordsSkipped = 0;
            int nextKanjiSourceLine = kanjiEntryRepository.findMaxSourceLine() + 1;

            for (KanjiRow row : kanjiResult.rows()) {
                String normTopic = normalizeTopic(row.topic());
                Topic topic = topicMap.get(normTopic.toLowerCase(Locale.ROOT));
                String kanjiChar = row.kanji().trim();

                Optional<KanjiEntry> existingKanjiOpt = kanjiEntryRepository.findByCharacter(kanjiChar);
                KanjiEntry kanjiTarget;

                if (existingKanjiOpt.isPresent()) {
                    if (effectiveMode == ImportMode.UPDATE_EXISTING) {
                        kanjiTarget = existingKanjiOpt.get();
                        if (topic != null) {
                            kanjiTarget.setTopic(topic);
                        }
                        if (row.hanViet() != null) {
                            kanjiTarget.setHanViet(row.hanViet());
                        }
                        if (row.meaningVi() != null) {
                            kanjiTarget.setMeaningVi(row.meaningVi());
                        }
                        if (row.onReading() != null) {
                            kanjiTarget.setOnyomi(row.onReading());
                        }
                        if (row.kunReading() != null) {
                            kanjiTarget.setKunyomi(row.kunReading());
                        }
                        kanjiTarget = kanjiEntryRepository.save(kanjiTarget);
                        kanjiUpdated++;
                    } else {
                        kanjiTarget = existingKanjiOpt.get();
                        kanjiSkipped++;
                    }
                } else {
                    KanjiEntry newKanji = new KanjiEntry();
                    newKanji.setTopic(topic);
                    newKanji.setCharacter(kanjiChar);
                    newKanji.setHanViet(row.hanViet());
                    newKanji.setMeaningVi(row.meaningVi());
                    newKanji.setOnyomi(row.onReading());
                    newKanji.setKunyomi(row.kunReading());
                    newKanji.setSourceLine(nextKanjiSourceLine++);
                    kanjiTarget = kanjiEntryRepository.save(newKanji);
                    newlyAddedKanjis.add(kanjiTarget);
                    kanjiAdded++;
                }

                // Ghi các từ cần nhớ của Kanji vào bảng từ vựng chung (vocabulary)
                for (KanjiWordRow kwRow : row.words()) {
                    String kwJapanese = kwRow.japanese().trim();
                    String kwVocabKey = topic.getId() + ":" + kwJapanese;
                    Optional<Vocabulary> existingVocabOpt = vocabularyRepository.findByTopicIdAndJapaneseText(topic.getId(), kwJapanese);

                    if (existingVocabOpt.isPresent()) {
                        if (effectiveMode == ImportMode.UPDATE_EXISTING) {
                            Vocabulary existing = existingVocabOpt.get();
                            if (kwRow.romaji() != null && !kwRow.romaji().isBlank()) {
                                existing.setRomaji(kwRow.romaji().trim());
                            }
                            if (kwRow.meaningVi() != null && !kwRow.meaningVi().isBlank()) {
                                existing.setMeaningVi(kwRow.meaningVi().trim());
                            }
                            existing.setReading(extractReading(kwJapanese));
                            existing.setHasKanji(containsKanji(kwJapanese));
                            vocabBatch.add(existing);
                            wordsUpdated++;
                            kanjiWordsUpdated++;
                        } else {
                            wordsSkipped++;
                            kanjiWordsSkipped++;
                        }
                    } else if (!processedVocabKeysInBatch.add(kwVocabKey)) {
                        // Trùng lặp ngay trong cùng một tệp tin
                        if (effectiveMode == ImportMode.UPDATE_EXISTING) {
                            wordsUpdated++;
                            kanjiWordsUpdated++;
                        } else {
                            wordsSkipped++;
                            kanjiWordsSkipped++;
                        }
                    } else {
                        Vocabulary newVocab = new Vocabulary();
                        newVocab.setTopic(topic);
                        newVocab.setJapaneseText(kwJapanese);
                        newVocab.setReading(extractReading(kwJapanese));
                        newVocab.setRomaji(kwRow.romaji() != null && !kwRow.romaji().isBlank() ? kwRow.romaji().trim() : null);
                        newVocab.setMeaningVi(kwRow.meaningVi() != null && !kwRow.meaningVi().isBlank() ? kwRow.meaningVi().trim() : null);
                        newVocab.setHasKanji(containsKanji(kwJapanese));
                        newVocab.setSourceLine(null);
                        vocabBatch.add(newVocab);
                        newlyAddedVocabs.add(newVocab);
                        wordsAdded++;
                        kanjiWordsAdded++;
                    }

                    if (vocabBatch.size() >= BATCH_SIZE) {
                        vocabularyRepository.saveAll(vocabBatch);
                        vocabBatch.clear();
                    }
                }
            }
            if (!vocabBatch.isEmpty()) {
                vocabularyRepository.saveAll(vocabBatch);
                vocabBatch.clear();
            }

            int totalAdded = topicsAdded + wordsAdded + kanjiAdded;
            int totalUpdated = wordsUpdated + kanjiUpdated;
            int totalSkipped = wordsSkipped + kanjiSkipped;

            // 7. Ghi Audit Log thành công và gắn import_batch_id cho các bản ghi mới thêm
            ImportAuditLog savedAudit = auditService.log(
                    effectiveUserId, cleanFileName, fileHash, totalAdded, totalUpdated, totalSkipped, "SUCCESS", null
            );
            if (savedAudit != null && savedAudit.getId() != null) {
                Long batchId = savedAudit.getId();
                assignBatchIdToAddedEntities(batchId, newlyAddedTopics, newlyAddedVocabs, newlyAddedKanjis);
            }

            log.info("Import committed successfully: file='{}', added={}, updated={}, skipped={}",
                    cleanFileName, totalAdded, totalUpdated, totalSkipped);

            return ImportCommitResult.success(
                    topicsAdded, wordsAdded, wordsUpdated, wordsSkipped,
                    kanjiAdded, kanjiUpdated, kanjiSkipped,
                    kanjiWordsAdded, kanjiWordsUpdated, kanjiWordsSkipped
            );

        } catch (DataIntegrityViolationException e) {
            String clearMessage = translateDataIntegrityException(e);
            log.error("Concurrent data conflict during import of file '{}': {}", cleanFileName, clearMessage, e);
            auditService.log(effectiveUserId, cleanFileName, fileHash, 0, 0, 0, "FAILED", clearMessage);
            throw new ImportConflictException(clearMessage, e);
        }
    }

    /**
     * Dịch ngoại lệ vi phạm ràng buộc độc nhất thành thông báo rõ nghĩa cho người dùng.
     */
    private String translateDataIntegrityException(DataIntegrityViolationException e) {
        String msg = (e.getMessage() != null) ? e.getMessage().toLowerCase(Locale.ROOT) : "";
        if (msg.contains("uk_vocabulary_topic_japanese") || msg.contains("vocabulary")) {
            return "Xung đột dữ liệu đồng thời: Từ vựng tiếng Nhật đã tồn tại trong cùng chủ đề.";
        }
        if (msg.contains("uk_kanji_character") || msg.contains("kanji_entry")) {
            return "Xung đột dữ liệu đồng thời: Ký tự Kanji đã được thêm bởi tiến trình khác.";
        }
        if (msg.contains("uk_topic_source_line")) {
            return "Xung đột dữ liệu đồng thời: Dòng chủ đề bị trùng lặp.";
        }
        return "Xung đột dữ liệu đồng thời: Vi phạm ràng buộc toàn vẹn cơ sở dữ liệu.";
    }

    /**
     * Kiểm tra bảo vệ trước khi lưu: mọi chuỗi vượt giới hạn độ dài cột của Entity đều bị chặn
     * và ghi nhận vào danh sách lỗi hàng, tuyệt đối không chờ database báo lỗi.
     */
    private void validateEntityColumnLimits(List<WordRow> wordRows, List<KanjiRow> kanjiRows, List<RowError> errors) {
        if (wordRows != null) {
            for (WordRow row : wordRows) {
                int r = row.rowNumber();
                String normTopic = normalizeTopic(row.topic());
                if (normTopic.length() > KanjiLimits.MAX_TOPIC_LENGTH) {
                    addErrorIfAbsent(errors, r, WordSheetParser.HEADER_TOPIC,
                            "Chủ đề vượt quá độ dài tối đa " + KanjiLimits.MAX_TOPIC_LENGTH + " ký tự");
                }
                if (row.japanese() != null && row.japanese().length() > KanjiLimits.MAX_JAPANESE_LENGTH) {
                    addErrorIfAbsent(errors, r, WordSheetParser.HEADER_JAPANESE,
                            "Từ tiếng Nhật vượt quá độ dài tối đa " + KanjiLimits.MAX_JAPANESE_LENGTH + " ký tự");
                }
                String reading = extractReading(row.japanese());
                if (reading != null && reading.length() > KanjiLimits.MAX_READING_LENGTH) {
                    addErrorIfAbsent(errors, r, WordSheetParser.HEADER_JAPANESE,
                            "Cách đọc vượt quá độ dài tối đa " + KanjiLimits.MAX_READING_LENGTH + " ký tự");
                }
                if (row.romaji() != null && row.romaji().length() > KanjiLimits.MAX_ROMAJI_LENGTH) {
                    addErrorIfAbsent(errors, r, WordSheetParser.HEADER_ROMAJI,
                            "Romaji vượt quá độ dài tối đa " + KanjiLimits.MAX_ROMAJI_LENGTH + " ký tự");
                }
                if (row.meaningVi() != null && row.meaningVi().length() > KanjiLimits.MAX_VOCAB_MEANING_LENGTH) {
                    addErrorIfAbsent(errors, r, WordSheetParser.HEADER_MEANING,
                            "Nghĩa tiếng Việt vượt quá độ dài tối đa " + KanjiLimits.MAX_VOCAB_MEANING_LENGTH + " ký tự");
                }
            }
        }

        if (kanjiRows != null) {
            for (KanjiRow row : kanjiRows) {
                int r = row.rowNumber();
                String normTopic = normalizeTopic(row.topic());
                if (normTopic.length() > KanjiLimits.MAX_TOPIC_LENGTH) {
                    addErrorIfAbsent(errors, r, KanjiSheetParser.HEADER_TOPIC,
                            "Chủ đề vượt quá độ dài tối đa " + KanjiLimits.MAX_TOPIC_LENGTH + " ký tự");
                }
                if (row.kanji() != null && row.kanji().trim().length() > KanjiLimits.MAX_KANJI_CHAR_LENGTH) {
                    addErrorIfAbsent(errors, r, KanjiSheetParser.HEADER_KANJI,
                            "Ký tự Kanji vượt quá độ dài tối đa " + KanjiLimits.MAX_KANJI_CHAR_LENGTH + " ký tự");
                }
                if (row.hanViet() != null && row.hanViet().length() > KanjiLimits.MAX_HAN_VIET_LENGTH) {
                    addErrorIfAbsent(errors, r, KanjiSheetParser.HEADER_HAN_VIET,
                            "Hán Việt vượt quá " + KanjiLimits.MAX_HAN_VIET_LENGTH + " ký tự");
                }
                if (row.meaningVi() != null && row.meaningVi().length() > KanjiLimits.MAX_KANJI_MEANING_LENGTH) {
                    addErrorIfAbsent(errors, r, KanjiSheetParser.HEADER_MEANING,
                            "Ý nghĩa vượt quá " + KanjiLimits.MAX_KANJI_MEANING_LENGTH + " ký tự");
                }
                if (row.onReading() != null && row.onReading().length() > KanjiLimits.MAX_ONYOMI_LENGTH) {
                    addErrorIfAbsent(errors, r, KanjiSheetParser.HEADER_ON,
                            "Âm On vượt quá " + KanjiLimits.MAX_ONYOMI_LENGTH + " ký tự");
                }
                if (row.kunReading() != null && row.kunReading().length() > KanjiLimits.MAX_KUNYOMI_LENGTH) {
                    addErrorIfAbsent(errors, r, KanjiSheetParser.HEADER_KUN,
                            "Âm Kun vượt quá " + KanjiLimits.MAX_KUNYOMI_LENGTH + " ký tự");
                }
                if (row.words() != null) {
                    for (KanjiWordRow kw : row.words()) {
                        int wr = kw.rowNumber();
                        if (kw.japanese() != null && kw.japanese().trim().length() > KanjiLimits.MAX_JAPANESE_LENGTH) {
                            addErrorIfAbsent(errors, wr, KanjiSheetParser.HEADER_WORD_JAPANESE,
                                    "Từ tiếng Nhật vượt quá độ dài tối đa " + KanjiLimits.MAX_JAPANESE_LENGTH + " ký tự");
                        }
                        String kwReading = extractReading(kw.japanese());
                        if (kwReading != null && kwReading.length() > KanjiLimits.MAX_READING_LENGTH) {
                            addErrorIfAbsent(errors, wr, KanjiSheetParser.HEADER_WORD_JAPANESE,
                                    "Cách đọc vượt quá độ dài tối đa " + KanjiLimits.MAX_READING_LENGTH + " ký tự");
                        }
                        if (kw.romaji() != null && kw.romaji().trim().length() > KanjiLimits.MAX_ROMAJI_LENGTH) {
                            addErrorIfAbsent(errors, wr, KanjiSheetParser.HEADER_WORD_ROMAJI,
                                    "Romaji vượt quá độ dài tối đa " + KanjiLimits.MAX_ROMAJI_LENGTH + " ký tự");
                        }
                        if (kw.meaningVi() != null && kw.meaningVi().trim().length() > KanjiLimits.MAX_VOCAB_MEANING_LENGTH) {
                            addErrorIfAbsent(errors, wr, KanjiSheetParser.HEADER_WORD_MEANING,
                                    "Nghĩa tiếng Việt vượt quá độ dài tối đa " + KanjiLimits.MAX_VOCAB_MEANING_LENGTH + " ký tự");
                        }
                    }
                }
            }
        }
    }

    private void addErrorIfAbsent(List<RowError> errors, int rowNumber, String column, String message) {
        boolean exists = errors.stream()
                .anyMatch(e -> e.rowNumber() == rowNumber && column.equals(e.column()));
        if (!exists) {
            errors.add(new RowError(rowNumber, column, message));
        }
    }

    /**
     * Chuẩn hóa tên chủ đề: cắt tỉa khoảng trắng, chuẩn hóa Unicode NFC và nén khoảng trắng liên tiếp.
     */
    private String normalizeTopic(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String nfc = Normalizer.normalize(trimmed, Normalizer.Form.NFC);
        return MULTI_SPACE_PATTERN.matcher(nfc).replaceAll(" ").trim();
    }

    /**
     * Chuẩn hóa tên file, loại bỏ toàn bộ đường dẫn để chống tấn công Path Traversal.
     */
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "unknown.xlsx";
        }
        String clean = filename.trim();
        int lastSlash = Math.max(clean.lastIndexOf('/'), clean.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            clean = clean.substring(lastSlash + 1);
        }
        return clean.replaceAll("[\\r\\n]", "_");
    }

    /**
     * Tính toán mã băm SHA-256 của tệp tin tải lên.
     */
    private String calculateSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Thuật toán SHA-256 không khả dụng trong môi trường runtime.", e);
        }
    }

    /**
     * Kiểm tra xem chuỗi có chứa chữ Hán tự Kanji (CJK Unified Ideographs) hay không.
     */
    public static boolean containsKanji(String text) {
        if (text == null) {
            return false;
        }
        return text.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
    }

    /**
     * Trích xuất cách đọc từ ngoặc tròn cuối chuỗi tiếng Nhật (ví dụ: "私（わたし）" -> "わたし", "地味（な）（じみ）" -> "じみ").
     * Hỗ trợ cả ngoặc đủ rộng （ ） và ngoặc thường ( ).
     * Nếu không có ngoặc: trả về null (không gây lỗi).
     */
    public static String extractReading(String japanese) {
        if (japanese == null) {
            return null;
        }
        String trimmed = japanese.trim();
        Matcher m = LAST_PARENTHESIS_PATTERN.matcher(trimmed);
        if (!m.find()) {
            return null;
        }
        String content = m.group(1).trim();
        return content.isEmpty() ? null : content;
    }

    /**
     * Xuất toàn bộ dữ liệu học liệu ra mảng byte định dạng Excel (.xlsx) gồm 2 sheet "New Word" và "Kanji".
     * Sử dụng {@link SXSSFWorkbook} để tiết kiệm bộ nhớ, giới hạn tối đa 50.000 hàng mỗi sheet,
     * ghi mọi giá trị dạng chuỗi và chống Formula Injection.
     */
    @Transactional(readOnly = true)
    public byte[] exportToExcel() {
        SXSSFWorkbook workbook = new SXSSFWorkbook(null, 100, false, true);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeNewWordSheet(workbook);
            writeKanjiSheet(workbook);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Không thể tạo tệp tin Excel xuất dữ liệu.", e);
        } finally {
            workbook.dispose();
            try {
                workbook.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void writeNewWordSheet(SXSSFWorkbook workbook) {
        Sheet sheet = workbook.createSheet(WordSheetParser.TARGET_SHEET_NAME);

        // Hàng 1 (index 0): Topic | Tiếng Nhật | Romaji | Nghĩa tiếng Việt
        Row headerRow = sheet.createRow(0);
        writeStringCell(headerRow, 0, WordSheetParser.HEADER_TOPIC);
        writeStringCell(headerRow, 1, WordSheetParser.HEADER_JAPANESE);
        writeStringCell(headerRow, 2, WordSheetParser.HEADER_ROMAJI);
        writeStringCell(headerRow, 3, WordSheetParser.HEADER_MEANING);

        int rowIndex = 1;
        List<Topic> allTopics = topicRepository.findAllByOrderBySortOrderAsc();
        if (allTopics != null) {
            for (Topic topic : allTopics) {
                if (topic == null || topic.isKanjiSection()) {
                    continue;
                }
                List<Vocabulary> vocabs = vocabularyRepository.findByTopicIdOrderBySourceLineAsc(topic.getId());
                if (vocabs == null || vocabs.isEmpty()) {
                    continue;
                }
                for (int i = 0; i < vocabs.size(); i++) {
                    if (rowIndex >= MAX_EXPORT_ROWS_PER_SHEET) {
                        throw new IllegalStateException("Số lượng hàng trên sheet '"
                                + WordSheetParser.TARGET_SHEET_NAME + "' vượt quá giới hạn tối đa "
                                + MAX_EXPORT_ROWS_PER_SHEET + " hàng.");
                    }
                    Vocabulary v = vocabs.get(i);
                    Row row = sheet.createRow(rowIndex++);
                    if (i == 0) {
                        writeStringCell(row, 0, topic.getName());
                    }
                    writeStringCell(row, 1, formatJapaneseWithReading(v.getJapaneseText(), v.getReading()));
                    writeStringCell(row, 2, v.getRomaji());
                    writeStringCell(row, 3, v.getMeaningVi());
                }
            }
        }
    }

    private void writeKanjiSheet(SXSSFWorkbook workbook) {
        Sheet sheet = workbook.createSheet(KanjiSheetParser.TARGET_SHEET_NAME);

        // Hàng 1 (index 0): Topic | Kanji | Hán Việt | Ý nghĩa | On | Kun | Từ cần nhớ (merge G1:I1)
        Row headerRow1 = sheet.createRow(0);
        writeStringCell(headerRow1, 0, KanjiSheetParser.HEADER_TOPIC);
        writeStringCell(headerRow1, 1, KanjiSheetParser.HEADER_KANJI);
        writeStringCell(headerRow1, 2, KanjiSheetParser.HEADER_HAN_VIET);
        writeStringCell(headerRow1, 3, KanjiSheetParser.HEADER_MEANING);
        writeStringCell(headerRow1, 4, KanjiSheetParser.HEADER_ON);
        writeStringCell(headerRow1, 5, KanjiSheetParser.HEADER_KUN);
        writeStringCell(headerRow1, 6, "Từ cần nhớ");

        // Hàng 2 (index 1): cột G, H, I là Tiếng Nhật | Romaji | Nghĩa tiếng Việt
        Row headerRow2 = sheet.createRow(1);
        writeStringCell(headerRow2, 6, KanjiSheetParser.HEADER_WORD_JAPANESE);
        writeStringCell(headerRow2, 7, KanjiSheetParser.HEADER_WORD_ROMAJI);
        writeStringCell(headerRow2, 8, KanjiSheetParser.HEADER_WORD_MEANING);

        // Merge G1:I1
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 6, 8));

        List<KanjiEntry> allKanjis = kanjiEntryRepository.findAll();
        if (allKanjis == null || allKanjis.isEmpty()) {
            return;
        }

        List<KanjiEntry> sortedKanjis = new ArrayList<>(allKanjis);
        sortedKanjis.sort(Comparator
                .comparing((KanjiEntry k) -> k.getTopic() != null ? k.getTopic().getSortOrder() : null,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(k -> k.getTopic() != null ? k.getTopic().getId() : null,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(KanjiEntry::getSourceLine, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(KanjiEntry::getId, Comparator.nullsLast(Comparator.naturalOrder())));

        int rowIndex = 2;
        Long lastTopicId = null;

        for (KanjiEntry kanji : sortedKanjis) {
            if (rowIndex >= MAX_EXPORT_ROWS_PER_SHEET) {
                throw new IllegalStateException("Số lượng hàng trên sheet '"
                        + KanjiSheetParser.TARGET_SHEET_NAME + "' vượt quá giới hạn tối đa "
                        + MAX_EXPORT_ROWS_PER_SHEET + " hàng.");
            }
            Row kanjiRow = sheet.createRow(rowIndex++);
            Long currentTopicId = kanji.getTopic() != null ? kanji.getTopic().getId() : null;
            String topicName = kanji.getTopic() != null ? kanji.getTopic().getName() : null;
            if (topicName != null && !Objects.equals(currentTopicId, lastTopicId)) {
                writeStringCell(kanjiRow, 0, topicName);
                lastTopicId = currentTopicId;
            }
            writeStringCell(kanjiRow, 1, kanji.getCharacter());
            writeStringCell(kanjiRow, 2, kanji.getHanViet());
            writeStringCell(kanjiRow, 3, kanji.getMeaningVi());
            writeStringCell(kanjiRow, 4, kanji.getOnyomi());
            writeStringCell(kanjiRow, 5, kanji.getKunyomi());

            // Các từ cần nhớ ở các hàng tiếp theo với cột B trống, cột G, H, I
            List<Vocabulary> kanjiWords = getWordsForKanjiEntity(currentTopicId, kanji.getCharacter());
            for (Vocabulary kw : kanjiWords) {
                if (rowIndex >= MAX_EXPORT_ROWS_PER_SHEET) {
                    throw new IllegalStateException("Số lượng hàng trên sheet '"
                            + KanjiSheetParser.TARGET_SHEET_NAME + "' vượt quá giới hạn tối đa "
                            + MAX_EXPORT_ROWS_PER_SHEET + " hàng.");
                }
                Row wordRow = sheet.createRow(rowIndex++);
                writeStringCell(wordRow, 6, formatJapaneseWithReading(kw.getJapaneseText(), kw.getReading()));
                writeStringCell(wordRow, 7, kw.getRomaji());
                writeStringCell(wordRow, 8, kw.getMeaningVi());
            }
        }
    }

    /**
     * Lấy danh sách từ cần nhớ của một Kanji theo đúng quy tắc KanjiService.getWordsForKanji.
     */
    private List<Vocabulary> getWordsForKanjiEntity(Long topicId, String character) {
        if (character == null || character.isBlank()) {
            return Collections.emptyList();
        }
        String cleanChar = character.strip();
        List<Vocabulary> list = null;
        if (topicId != null) {
            list = vocabularyRepository.findWordsByTopicIdAndCharacter(topicId, cleanChar);
            if (list == null || list.isEmpty()) {
                list = vocabularyRepository.findWordsByCharacter(cleanChar);
            }
        } else {
            list = vocabularyRepository.findWordsByCharacter(cleanChar);
        }
        return (list != null) ? list : Collections.emptyList();
    }

    /**
     * Định dạng cột Tiếng Nhật kèm cách đọc trong ngoặc tròn nếu có và chưa nằm sẵn ở cuối chuỗi.
     */
    public static String formatJapaneseWithReading(String japaneseText, String reading) {
        if (japaneseText == null) {
            return "";
        }
        String jp = japaneseText.strip();
        if (reading == null || reading.isBlank()) {
            return jp;
        }
        if (extractReading(jp) != null) {
            return jp;
        }
        return jp + "（" + reading.strip() + "）";
    }

    /**
     * Chống Formula Injection: thêm tiền tố dấu nháy đơn (') nếu chuỗi bắt đầu bằng =, +, -, @, tab hoặc xuống dòng.
     */
    public static String sanitizeFormulaInjection(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@'
                || first == '\t' || first == '\r' || first == '\n') {
            return "'" + value;
        }
        return value;
    }

    private void writeStringCell(Row row, int colIndex, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        Cell cell = row.createCell(colIndex, CellType.STRING);
        cell.setCellValue(sanitizeFormulaInjection(value));
    }

    /**
     * Gắn import_batch_id cho toàn bộ các bản ghi mới được tạo trong lần commit thành công.
     * Các bản ghi bị cập nhật hoặc bỏ qua được giữ nguyên import_batch_id cũ.
     */
    private void assignBatchIdToAddedEntities(Long batchId,
                                              List<Topic> newlyAddedTopics,
                                              List<Vocabulary> newlyAddedVocabs,
                                              List<KanjiEntry> newlyAddedKanjis) {
        if (batchId == null) {
            return;
        }
        List<Long> topicIds = newlyAddedTopics.stream()
                .map(Topic::getId)
                .filter(Objects::nonNull)
                .toList();
        if (!topicIds.isEmpty()) {
            topicRepository.assignImportBatchId(topicIds, batchId);
        }

        List<Long> vocabIds = newlyAddedVocabs.stream()
                .map(Vocabulary::getId)
                .filter(Objects::nonNull)
                .toList();
        for (int i = 0; i < vocabIds.size(); i += 500) {
            List<Long> chunk = vocabIds.subList(i, Math.min(i + 500, vocabIds.size()));
            vocabularyRepository.assignImportBatchId(chunk, batchId);
        }

        List<Long> kanjiIds = newlyAddedKanjis.stream()
                .map(KanjiEntry::getId)
                .filter(Objects::nonNull)
                .toList();
        for (int i = 0; i < kanjiIds.size(); i += 500) {
            List<Long> chunk = kanjiIds.subList(i, Math.min(i + 500, kanjiIds.size()));
            kanjiEntryRepository.assignImportBatchId(chunk, batchId);
        }
    }

    /**
     * Tính toán xem trước (bằng truy vấn đếm) số lượng bản ghi sẽ bị xóa và số chủ đề được giữ lại
     * cho hộp xác nhận hoàn tác của một lần import.
     */
    @Transactional(readOnly = true)
    public ImportUndoPreview previewUndo(Long batchId) {
        if (batchId == null || batchId <= 0) {
            throw new IllegalArgumentException("Mã lần import không hợp lệ.");
        }
        ImportAuditLog auditLog = auditService.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lịch sử import #" + batchId + "."));

        long vocabCount = vocabularyRepository.countByImportBatchId(batchId);
        long kanjiCount = kanjiEntryRepository.countByImportBatchId(batchId);

        LocalDateTime importedAt = auditLog.getImportedAt();
        long modifiedCount = (importedAt != null)
                ? (vocabularyRepository.countModifiedAfterImport(batchId, importedAt)
                   + kanjiEntryRepository.countModifiedAfterImport(batchId, importedAt))
                : 0L;

        List<Topic> batchTopics = topicRepository.findByImportBatchIdOrderBySortOrderDescIdDesc(batchId);
        long topicsToDelete = 0L;
        long topicsRetained = 0L;
        List<String> retainedTopicNames = new ArrayList<>();

        if (batchTopics != null) {
            for (Topic topic : batchTopics) {
                Long topicId = topic.getId();
                boolean hasExternalVocab = vocabularyRepository.existsByTopicIdOutsideBatch(topicId, batchId);
                boolean hasExternalKanji = kanjiEntryRepository.existsByTopicIdOutsideBatch(topicId, batchId);
                boolean hasExternalChild = topicRepository.existsChildOutsideBatch(topicId, batchId);
                if (!hasExternalVocab && !hasExternalKanji && !hasExternalChild) {
                    topicsToDelete++;
                } else {
                    topicsRetained++;
                    retainedTopicNames.add(topic.getName());
                }
            }
        }

        return new ImportUndoPreview(
                batchId,
                vocabCount,
                kanjiCount,
                topicsToDelete,
                topicsRetained,
                modifiedCount,
                retainedTopicNames
        );
    }

    /**
     * Lấy bản đồ xem trước hoàn tác cho danh sách các dòng lịch sử hiển thị trên trang.
     */
    @Transactional(readOnly = true)
    public Map<Long, ImportUndoPreview> getUndoPreviews(List<ImportAuditLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, ImportUndoPreview> previews = new LinkedHashMap<>();
        for (ImportAuditLog item : logs) {
            if (item != null && item.getId() != null && item.isCanUndo()) {
                previews.put(item.getId(), previewUndo(item.getId()));
            }
        }
        return previews;
    }

    /**
     * Hoàn tác (undo) một lần import:
     * - Chỉ cho phép hoàn tác bản ghi có kết quả SUCCESS và chưa hoàn tác (undone_at == null).
     * - Thực hiện trong một transaction duy nhất: xóa vocabulary, kanji_entry, sau đó xóa các topic thuộc batch
     *   không còn từ vựng, Kanji hay chủ đề con nào (giữ lại chủ đề đã có dữ liệu thêm vào sau đó).
     * - Đếm riêng số bản ghi đã bị sửa sau import (updated_at > imported_at).
     * - Ghi nhận undone_at trên bản ghi gốc và tạo thêm một dòng audit mới với kết quả UNDO.
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportUndoResult undoImport(Long batchId, String clientIp) {
        if (batchId == null || batchId <= 0) {
            throw new IllegalArgumentException("Mã lần import không hợp lệ.");
        }
        ImportAuditLog originalLog = auditService.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lịch sử import #" + batchId + "."));

        if (!"SUCCESS".equals(originalLog.getResult())) {
            throw new IllegalStateException("Chỉ có thể hoàn tác lần import có kết quả thành công (SUCCESS).");
        }
        if (originalLog.getUndoneAt() != null) {
            throw new IllegalStateException("Lần import #" + batchId + " đã được hoàn tác trước đó.");
        }

        LocalDateTime importedAt = originalLog.getImportedAt();
        int modifiedVocab = (importedAt != null)
                ? (int) vocabularyRepository.countModifiedAfterImport(batchId, importedAt)
                : 0;
        int modifiedKanji = (importedAt != null)
                ? (int) kanjiEntryRepository.countModifiedAfterImport(batchId, importedAt)
                : 0;
        int modifiedAfterImportDeleted = modifiedVocab + modifiedKanji;

        // 1. Xóa toàn bộ vocabulary thuộc batch này
        List<Vocabulary> batchVocabs = vocabularyRepository.findByImportBatchId(batchId);
        int vocabDeleted = (batchVocabs != null) ? batchVocabs.size() : 0;
        if (batchVocabs != null && !batchVocabs.isEmpty()) {
            vocabularyRepository.deleteAllInBatch(batchVocabs);
            vocabularyRepository.flush();
        }

        // 2. Xóa toàn bộ kanji_entry thuộc batch này
        List<KanjiEntry> batchKanjis = kanjiEntryRepository.findByImportBatchId(batchId);
        int kanjiDeleted = (batchKanjis != null) ? batchKanjis.size() : 0;
        if (batchKanjis != null && !batchKanjis.isEmpty()) {
            kanjiEntryRepository.deleteAllInBatch(batchKanjis);
            kanjiEntryRepository.flush();
        }

        // 3. Xóa các topic thuộc batch này nếu không còn dữ liệu nào (giữ lại chủ đề có dữ liệu thêm sau đó)
        List<Topic> batchTopics = topicRepository.findByImportBatchIdOrderBySortOrderDescIdDesc(batchId);
        int topicsDeleted = 0;
        int topicsRetained = 0;
        List<String> retainedTopicNames = new ArrayList<>();

        if (batchTopics != null && !batchTopics.isEmpty()) {
            List<Topic> orderedTopics = new ArrayList<>(batchTopics);
            orderedTopics.sort((a, b) -> {
                boolean aHasParent = a.getParent() != null;
                boolean bHasParent = b.getParent() != null;
                if (aHasParent != bHasParent) {
                    return aHasParent ? -1 : 1;
                }
                return 0;
            });

            for (Topic topic : orderedTopics) {
                Long topicId = topic.getId();
                boolean hasRemainingVocab = vocabularyRepository.existsByTopicId(topicId);
                boolean hasRemainingKanji = kanjiEntryRepository.existsByTopicId(topicId);
                boolean hasRemainingChildren = topicRepository.existsByParentId(topicId);

                if (!hasRemainingVocab && !hasRemainingKanji && !hasRemainingChildren) {
                    topicRepository.delete(topic);
                    topicRepository.flush();
                    topicsDeleted++;
                } else {
                    topicsRetained++;
                    retainedTopicNames.add(topic.getName());
                }
            }
        }

        // 4. Đánh dấu undone_at cho bản ghi audit gốc
        originalLog.setUndoneAt(LocalDateTime.now());
        auditService.saveInCurrentTransaction(originalLog);

        // 5. Ghi thêm dòng audit kết quả UNDO kèm số bản ghi đã xóa
        int totalDeleted = vocabDeleted + kanjiDeleted + topicsDeleted;
        ImportUndoResult preliminary = new ImportUndoResult(
                batchId, vocabDeleted, kanjiDeleted, topicsDeleted,
                topicsRetained, modifiedAfterImportDeleted, retainedTopicNames, null
        );
        ImportAuditLog undoAudit = auditService.logUndo(
                clientIp, originalLog, totalDeleted, topicsRetained, preliminary.toNotificationMessage()
        );
        Long undoAuditId = (undoAudit != null) ? undoAudit.getId() : null;

        return new ImportUndoResult(
                batchId, vocabDeleted, kanjiDeleted, topicsDeleted,
                topicsRetained, modifiedAfterImportDeleted, retainedTopicNames, undoAuditId
        );
    }
}
