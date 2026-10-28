package com.nihon.service;

import com.nihon.dto.ImportIssue;
import com.nihon.dto.ImportReport;
import com.nihon.dto.ParsedVocabularyFile;
import com.nihon.dto.ParsedVocabularyFile.ParsedKanji;
import com.nihon.dto.ParsedVocabularyFile.ParsedTopic;
import com.nihon.dto.ParsedVocabularyFile.ParsedVocabulary;
import com.nihon.entity.KanjiEntry;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.repository.KanjiEntryRepository;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VocabularyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Nhập dữ liệu từ file từ vựng vào database.
 * <ol>
 *   <li>Phân tích + kiểm tra toàn bộ file. Có lỗi → dừng, không ghi gì.</li>
 *   <li>Đối chiếu với dữ liệu đã có theo dòng nguồn (source_line). Xung đột → dừng, không ghi gì.</li>
 *   <li>Chỉ thêm bản ghi chưa có; bản ghi đã có giữ nguyên (không cập nhật, không xóa).</li>
 * </ol>
 * Toàn bộ chạy trong một transaction: lỗi kỹ thuật giữa chừng sẽ rollback mọi thay đổi.
 */
@Service
@Transactional
public class VocabularyImportService {

    private static final Logger log = LoggerFactory.getLogger(VocabularyImportService.class);

    private final VocabularyFileParser parser;
    private final TopicRepository topicRepository;
    private final VocabularyRepository vocabularyRepository;
    private final KanjiEntryRepository kanjiEntryRepository;

    public VocabularyImportService(VocabularyFileParser parser,
                                   TopicRepository topicRepository,
                                   VocabularyRepository vocabularyRepository,
                                   KanjiEntryRepository kanjiEntryRepository) {
        this.parser = parser;
        this.topicRepository = topicRepository;
        this.vocabularyRepository = vocabularyRepository;
        this.kanjiEntryRepository = kanjiEntryRepository;
    }

    /** Đọc file UTF-8; byte không hợp lệ UTF-8 sẽ gây lỗi thay vì âm thầm thay ký tự. */
    public ImportReport importFrom(Path path) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return doImport(reader);
        } catch (IOException e) {
            throw new ImportException("Không đọc được file nguồn (kiểm tra đường dẫn và mã hóa UTF-8)", e);
        }
    }

    public ImportReport importFrom(Reader reader) {
        return doImport(reader);
    }

    private ImportReport doImport(Reader reader) {
        ParsedVocabularyFile parsed;
        try {
            parsed = parser.parse(reader);
        } catch (IOException e) {
            throw new ImportException("Không đọc được dữ liệu nguồn", e);
        }

        ImportReport report = new ImportReport();
        report.setRowsRead(parsed.rowsRead());
        report.setSkippedDuplicateInFile(parsed.skippedDuplicateInFile());
        report.setReadingNotProvided(parsed.readingNotProvided());
        report.setReadingAmbiguous(parsed.readingAmbiguous());
        report.getErrors().addAll(parsed.errors());

        if (!report.getErrors().isEmpty()) {
            return abort(report);
        }

        Map<Integer, Topic> existingTopics = topicRepository.findAll().stream()
                .filter(t -> t.getSourceLine() != null)
                .collect(Collectors.toMap(Topic::getSourceLine, Function.identity()));
        Map<Integer, Vocabulary> existingVocab = vocabularyRepository.findAll().stream()
                .filter(v -> v.getSourceLine() != null)
                .collect(Collectors.toMap(Vocabulary::getSourceLine, Function.identity()));
        Map<Integer, KanjiEntry> existingKanji = kanjiEntryRepository.findAll().stream()
                .filter(k -> k.getSourceLine() != null)
                .collect(Collectors.toMap(KanjiEntry::getSourceLine, Function.identity()));
        Map<String, KanjiEntry> existingKanjiByChar = existingKanji.values().stream()
                .collect(Collectors.toMap(KanjiEntry::getCharacter, Function.identity()));

        checkConflicts(parsed, existingTopics, existingVocab, existingKanji, existingKanjiByChar, report);
        if (!report.getErrors().isEmpty()) {
            return abort(report);
        }

        Map<Integer, Topic> topicByLine = new HashMap<>(existingTopics);
        for (ParsedTopic pt : parsed.topics()) {
            if (topicByLine.containsKey(pt.sourceLine())) {
                continue;
            }
            Topic t = new Topic();
            t.setName(pt.name());
            t.setParent(pt.parentLine() == null ? null : topicByLine.get(pt.parentLine()));
            t.setSortOrder(pt.sourceLine());
            t.setSourceLine(pt.sourceLine());
            t.setKanjiSection(pt.kanjiSection());
            topicByLine.put(pt.sourceLine(), topicRepository.save(t));
            report.setTopicsInserted(report.getTopicsInserted() + 1);
        }

        for (ParsedVocabulary pv : parsed.vocabularies()) {
            if (existingVocab.containsKey(pv.sourceLine())) {
                report.setSkippedAlreadyImported(report.getSkippedAlreadyImported() + 1);
                continue;
            }
            Vocabulary v = new Vocabulary();
            v.setTopic(topicByLine.get(pv.topicLine()));
            v.setJapaneseText(pv.japaneseText());
            v.setReading(pv.reading());
            v.setRomaji(pv.romaji());
            v.setMeaningVi(pv.meaningVi());
            v.setHasKanji(pv.hasKanji());
            v.setSourceLine(pv.sourceLine());
            vocabularyRepository.save(v);
            report.setVocabularyInserted(report.getVocabularyInserted() + 1);
        }

        for (ParsedKanji pk : parsed.kanji()) {
            if (existingKanji.containsKey(pk.sourceLine())) {
                report.setSkippedAlreadyImported(report.getSkippedAlreadyImported() + 1);
                continue;
            }
            KanjiEntry k = new KanjiEntry();
            k.setTopic(topicByLine.get(pk.topicLine()));
            k.setCharacter(pk.character());
            k.setHanViet(pk.hanViet());
            k.setMeaningVi(pk.meaningVi());
            k.setOnyomi(pk.onyomi());
            k.setKunyomi(pk.kunyomi());
            k.setSourceLine(pk.sourceLine());
            kanjiEntryRepository.save(k);
            report.setKanjiInserted(report.getKanjiInserted() + 1);
        }

        logSummary(report);
        return report;
    }

    private void checkConflicts(ParsedVocabularyFile parsed,
                                Map<Integer, Topic> existingTopics,
                                Map<Integer, Vocabulary> existingVocab,
                                Map<Integer, KanjiEntry> existingKanji,
                                Map<String, KanjiEntry> existingKanjiByChar,
                                ImportReport report) {
        for (ParsedTopic pt : parsed.topics()) {
            Topic e = existingTopics.get(pt.sourceLine());
            if (e != null) {
                Integer parentLine = e.getParent() == null ? null : e.getParent().getSourceLine();
                if (!e.getName().equals(pt.name()) || !Objects.equals(parentLine, pt.parentLine())) {
                    report.getErrors().add(new ImportIssue(pt.sourceLine(),
                            "Xung đột: database đã có chủ đề khác ở cùng dòng nguồn"));
                }
            }
        }
        for (ParsedVocabulary pv : parsed.vocabularies()) {
            Vocabulary e = existingVocab.get(pv.sourceLine());
            if (e != null && !(e.getTopic().getSourceLine() == pv.topicLine()
                    && Objects.equals(e.getJapaneseText(), pv.japaneseText())
                    && Objects.equals(e.getRomaji(), pv.romaji())
                    && Objects.equals(e.getMeaningVi(), pv.meaningVi()))) {
                report.getErrors().add(new ImportIssue(pv.sourceLine(),
                        "Xung đột: database đã có từ vựng khác ở cùng dòng nguồn"));
            }
        }
        for (ParsedKanji pk : parsed.kanji()) {
            KanjiEntry e = existingKanji.get(pk.sourceLine());
            if (e != null) {
                if (!(e.getTopic().getSourceLine() == pk.topicLine() && e.getCharacter().equals(pk.character()))) {
                    report.getErrors().add(new ImportIssue(pk.sourceLine(),
                            "Xung đột: database đã có Kanji khác ở cùng dòng nguồn"));
                }
            } else if (existingKanjiByChar.containsKey(pk.character())) {
                report.getErrors().add(new ImportIssue(pk.sourceLine(),
                        "Xung đột: ký tự Kanji đã tồn tại ở dòng nguồn khác"));
            }
        }
    }

    private ImportReport abort(ImportReport report) {
        report.setAborted(true);
        logSummary(report);
        return report;
    }

    /** Chỉ log số lượng; không log nội dung bản ghi hay đường dẫn file. */
    private void logSummary(ImportReport r) {
        if (r.isAborted()) {
            log.warn("Vocabulary import aborted: rowsRead={}, errors={}, nothing written", r.getRowsRead(), r.getErrorCount());
        } else {
            log.info("Vocabulary import done: rowsRead={}, topicsInserted={}, vocabularyInserted={}, kanjiInserted={}, "
                            + "skippedAlreadyImported={}, skippedDuplicateInFile={}, readingNotProvided={}, readingAmbiguous={}",
                    r.getRowsRead(), r.getTopicsInserted(), r.getVocabularyInserted(), r.getKanjiInserted(),
                    r.getSkippedAlreadyImported(), r.getSkippedDuplicateInFile(),
                    r.getReadingNotProvided(), r.getReadingAmbiguous());
        }
    }
}
