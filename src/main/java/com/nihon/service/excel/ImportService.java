package com.nihon.service.excel;

import com.nihon.dto.excel.ImportCommitResult;
import com.nihon.dto.excel.ImportMode;
import com.nihon.dto.excel.ImportPreviewReport;
import com.nihon.dto.excel.KanjiRow;
import com.nihon.dto.excel.KanjiWordRow;
import com.nihon.dto.excel.ParseResult;
import com.nihon.dto.excel.RowError;
import com.nihon.dto.excel.WordRow;
import com.nihon.entity.KanjiEntry;
import com.nihon.entity.KanjiWord;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.repository.KanjiEntryRepository;
import com.nihon.repository.KanjiWordRepository;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VocabularyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private static final int BATCH_SIZE = 50;
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern READING_GROUP = Pattern.compile("[（(]([^）)]*)[）)]");

    private final ExcelUploadValidator validator;
    private final WordSheetParser wordSheetParser;
    private final KanjiSheetParser kanjiSheetParser;
    private final TopicRepository topicRepository;
    private final VocabularyRepository vocabularyRepository;
    private final KanjiEntryRepository kanjiEntryRepository;
    private final KanjiWordRepository kanjiWordRepository;
    private final ImportAuditService auditService;

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

        // 3. Thu thập toàn bộ lỗi hàng nếu có
        allErrors.addAll(wordResult.errors());
        allErrors.addAll(kanjiResult.errors());

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

        // Thống kê từ cần nhớ của Kanji
        int newKanjiWordsCount = 0;
        int duplicateKanjiWordsCount = 0;
        Set<String> seenKanjiWordsInFile = new HashSet<>();
        for (KanjiRow row : kanjiResult.rows()) {
            String kanjiChar = row.kanji().trim();
            Optional<KanjiEntry> kanjiOpt = kanjiEntryRepository.findByCharacter(kanjiChar);

            for (KanjiWordRow kwRow : row.words()) {
                String kwKey = kanjiChar + ":" + kwRow.japanese().trim();
                if (!seenKanjiWordsInFile.add(kwKey)) {
                    duplicateKanjiWordsCount++;
                } else if (kanjiOpt.isPresent() && kanjiWordRepository.existsByKanjiIdAndJapanese(kanjiOpt.get().getId(), kwRow.japanese())) {
                    duplicateKanjiWordsCount++;
                } else {
                    newKanjiWordsCount++;
                }
            }
        }

        return new ImportPreviewReport(
                newTopicsCount,
                newWordsCount,
                duplicateWordsCount,
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

        // 3. Thu thập toàn bộ lỗi hàng
        allErrors.addAll(wordResult.errors());
        allErrors.addAll(kanjiResult.errors());

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
                        existing.setRomaji(row.romaji());
                        existing.setMeaningVi(row.meaningVi());
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
                        kanjiTarget.setTopic(topic);
                        kanjiTarget.setHanViet(row.hanViet());
                        kanjiTarget.setMeaningVi(row.meaningVi());
                        kanjiTarget.setOnyomi(row.onReading());
                        kanjiTarget.setKunyomi(row.kunReading());
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
                    kanjiAdded++;
                }

                // Lưu các từ vựng cần nhớ (KanjiWord)
                for (KanjiWordRow kwRow : row.words()) {
                    Optional<KanjiWord> existingKwOpt = kanjiWordRepository.findByKanjiIdAndJapanese(kanjiTarget.getId(), kwRow.japanese());

                    if (existingKwOpt.isPresent()) {
                        if (effectiveMode == ImportMode.UPDATE_EXISTING) {
                            KanjiWord existingKw = existingKwOpt.get();
                            existingKw.setRomaji(kwRow.romaji());
                            existingKw.setMeaningVi(kwRow.meaningVi());
                            existingKw.setSortOrder(kwRow.rowNumber());
                            kanjiWordRepository.save(existingKw);
                            kanjiWordsUpdated++;
                        } else {
                            kanjiWordsSkipped++;
                        }
                    } else {
                        KanjiWord newKw = new KanjiWord();
                        newKw.setKanji(kanjiTarget);
                        newKw.setJapanese(kwRow.japanese());
                        newKw.setRomaji(kwRow.romaji());
                        newKw.setMeaningVi(kwRow.meaningVi());
                        newKw.setSortOrder(kwRow.rowNumber());
                        kanjiWordRepository.save(newKw);
                        kanjiWordsAdded++;
                    }
                }
            }

            int totalAdded = topicsAdded + wordsAdded + kanjiAdded + kanjiWordsAdded;
            int totalUpdated = wordsUpdated + kanjiUpdated + kanjiWordsUpdated;
            int totalSkipped = wordsSkipped + kanjiSkipped + kanjiWordsSkipped;

            // 7. Ghi Audit Log thành công
            auditService.log(effectiveUserId, cleanFileName, fileHash, totalAdded, totalUpdated, totalSkipped, "SUCCESS", null);

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
        if (msg.contains("uk_kanji_word_kanji_japanese") || msg.contains("kanji_word")) {
            return "Xung đột dữ liệu đồng thời: Từ vựng của Kanji đã được thêm bởi tiến trình khác.";
        }
        if (msg.contains("uk_topic_source_line")) {
            return "Xung đột dữ liệu đồng thời: Dòng chủ đề bị trùng lặp.";
        }
        return "Xung đột dữ liệu đồng thời: Vi phạm ràng buộc toàn vẹn cơ sở dữ liệu.";
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
    static boolean containsKanji(String text) {
        if (text == null) {
            return false;
        }
        return text.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
    }

    /**
     * Trích xuất cách đọc Hiragana từ ngoặc tròn cuối chuỗi tiếng Nhật (ví dụ: "私（わたし）" -> "わたし").
     */
    static String extractReading(String japanese) {
        if (japanese == null) {
            return null;
        }
        Matcher m = READING_GROUP.matcher(japanese);
        if (!m.find()) {
            return null;
        }
        String content = m.group(1).strip();
        boolean atEnd = (m.end() == japanese.length());
        if (m.find() || !atEnd || content.isEmpty()) {
            return null;
        }
        return content;
    }
}
