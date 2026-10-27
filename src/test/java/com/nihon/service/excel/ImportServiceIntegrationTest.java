package com.nihon.service.excel;

import com.nihon.dto.excel.ImportCommitResult;
import com.nihon.dto.excel.ImportMode;
import com.nihon.dto.excel.ImportPreviewReport;
import com.nihon.entity.ImportAuditLog;
import com.nihon.entity.KanjiEntry;
import com.nihon.entity.KanjiWord;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.repository.ImportAuditLogRepository;
import com.nihon.repository.KanjiEntryRepository;
import com.nihon.repository.KanjiWordRepository;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VocabularyRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bộ kiểm thử tích hợp toàn diện cho {@link ImportService} với cơ sở dữ liệu kiểm thử thực tế:
 * 1. Import lần đầu: kiểm tra preview, commit thành công mọi bản ghi và ghi audit log.
 * 2. Import lại cùng tệp với SKIP_DUPLICATES: không tạo bản ghi trùng lặp, giữ nguyên dữ liệu.
 * 3. UPDATE_EXISTING: cập nhật thông tin cho bản ghi đã tồn tại mà không tăng số lượng.
 * 4. Rollback toàn bộ khi có lỗi dòng dữ liệu: không ghi một phần, hoàn tác 100% và ghi audit log thất bại.
 * 5. Hai chủ đề chung tên khác chữ hoa/thường: gộp chung thành một chủ đề duy nhất trong database.
 */
@SpringBootTest
class ImportServiceIntegrationTest {

    @Autowired
    private ImportService importService;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private VocabularyRepository vocabularyRepository;

    @Autowired
    private KanjiEntryRepository kanjiEntryRepository;

    @Autowired
    private KanjiWordRepository kanjiWordRepository;

    @Autowired
    private ImportAuditLogRepository auditLogRepository;

    @Test
    @DisplayName("1. Import lần đầu: Preview chính xác, Commit thành công ghi đủ dữ liệu và Audit Log")
    void testFirstTimeImport_InsertsAllRecords() throws IOException {
        String testTopicName = "Chủ đề tích hợp 1";
        String testKanjiTopicName = "Bài Kanji tích hợp 1";

        byte[] xlsxBytes = createStandardImportWorkbook(
                testTopicName,
                List.of(
                        new String[]{"本（ほん）", "hon", "Sách"},
                        new String[]{"車（くるま）", "kuruma", "Ô tô"}
                ),
                testKanjiTopicName,
                "森", "SÂM", "Rừng rậm", "シン", "もり",
                List.of(
                        new String[]{"森林", "shinrin", "Rừng rú"},
                        new String[]{"森", "mori", "Rừng"}
                )
        );

        MockMultipartFile file = new MockMultipartFile(
                "file", "integration_test_1.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes
        );

        long topicCountBefore = topicRepository.count();
        long vocabCountBefore = vocabularyRepository.count();
        long kanjiCountBefore = kanjiEntryRepository.count();

        // 1.1 Kiểm tra Preview không làm thay đổi DB
        ImportPreviewReport preview = importService.preview(file);
        assertThat(preview.hasErrors()).isFalse();
        assertThat(preview.newTopicsCount()).isEqualTo(2);
        assertThat(preview.newWordsCount()).isEqualTo(2);
        assertThat(preview.duplicateWordsCount()).isEqualTo(0);
        assertThat(preview.newKanjiCount()).isEqualTo(1);
        assertThat(preview.newKanjiWordsCount()).isEqualTo(2);

        // Đảm bảo preview không ghi database
        assertThat(topicRepository.count()).isEqualTo(topicCountBefore);
        assertThat(vocabularyRepository.count()).isEqualTo(vocabCountBefore);
        assertThat(kanjiEntryRepository.count()).isEqualTo(kanjiCountBefore);

        // 1.2 Thực hiện Commit
        ImportCommitResult result = importService.commit(file, ImportMode.SKIP_DUPLICATES, "user_tester_1");

        assertThat(result.success()).isTrue();
        assertThat(result.topicsAdded()).isEqualTo(2);
        assertThat(result.wordsAdded()).isEqualTo(2);
        assertThat(result.wordsSkipped()).isEqualTo(0);
        assertThat(result.kanjiAdded()).isEqualTo(1);
        assertThat(result.kanjiWordsAdded()).isEqualTo(2);

        // 1.3 Kiểm tra dữ liệu thực tế trong Database
        Optional<Topic> vocabTopicOpt = topicRepository.findByNameIgnoreCase(testTopicName);
        assertThat(vocabTopicOpt).isPresent();
        Topic vocabTopic = vocabTopicOpt.get();

        Optional<Vocabulary> v1Opt = vocabularyRepository.findByTopicIdAndJapaneseText(vocabTopic.getId(), "本（ほん）");
        assertThat(v1Opt).isPresent();
        Vocabulary v1 = v1Opt.get();
        assertThat(v1.getRomaji()).isEqualTo("hon");
        assertThat(v1.getMeaningVi()).isEqualTo("Sách");
        assertThat(v1.getReading()).isEqualTo("ほん");
        assertThat(v1.isHasKanji()).isTrue();

        Optional<KanjiEntry> kanjiOpt = kanjiEntryRepository.findByCharacter("森");
        assertThat(kanjiOpt).isPresent();
        KanjiEntry kanji = kanjiOpt.get();
        assertThat(kanji.getHanViet()).isEqualTo("SÂM");
        assertThat(kanji.getMeaningVi()).isEqualTo("Rừng rậm");

        List<KanjiWord> words = kanjiWordRepository.findByKanjiIdOrderBySortOrderAsc(kanji.getId());
        assertThat(words).hasSize(2);
        assertThat(words.get(0).getJapanese()).isEqualTo("森林");
        assertThat(words.get(1).getJapanese()).isEqualTo("森");

        // 1.4 Kiểm tra Audit Log
        List<ImportAuditLog> logs = auditLogRepository.findByUserIdOrderByImportedAtDesc("user_tester_1");
        assertThat(logs).isNotEmpty();
        ImportAuditLog latestLog = logs.get(0);
        assertThat(latestLog.getFileName()).isEqualTo("integration_test_1.xlsx");
        assertThat(latestLog.getResult()).isEqualTo("SUCCESS");
        assertThat(latestLog.getRecordsAdded()).isEqualTo(7); // 2 topic + 2 word + 1 kanji + 2 kanjiWord
        assertThat(latestLog.getFileHashSha256()).hasSize(64);
    }

    @Test
    @DisplayName("2. Import lại cùng file với SKIP_DUPLICATES: Không tạo bản ghi trùng, số lượng giữ nguyên")
    void testReimportSameFile_SkipDuplicates_DoesNotCreateDuplicates() throws IOException {
        String testTopicName = "Chủ đề trùng lặp 2";
        byte[] xlsxBytes = createStandardImportWorkbook(
                testTopicName,
                java.util.Collections.singletonList(new String[]{"猫（ねこ）", "neko", "Con mèo"}),
                "Bài Kanji 2",
                "犬", "KHUYỂN", "Con chó", "ケン", "いぬ",
                java.util.Collections.singletonList(new String[]{"番犬", "banken", "Chó giữ nhà"})
        );

        MockMultipartFile file = new MockMultipartFile("file", "duplicate_test.xlsx", "application/octet-stream", xlsxBytes);

        // Lần 1: Thêm mới
        ImportCommitResult firstCommit = importService.commit(file, ImportMode.SKIP_DUPLICATES, "user_tester_2");
        assertThat(firstCommit.success()).isTrue();
        assertThat(firstCommit.wordsAdded()).isEqualTo(1);
        assertThat(firstCommit.kanjiAdded()).isEqualTo(1);

        long topicCountAfterFirst = topicRepository.count();
        long vocabCountAfterFirst = vocabularyRepository.count();
        long kanjiCountAfterFirst = kanjiEntryRepository.count();
        long kanjiWordCountAfterFirst = kanjiWordRepository.count();

        // Lần 2: Import lại đúng file đó với SKIP_DUPLICATES
        ImportCommitResult secondCommit = importService.commit(file, ImportMode.SKIP_DUPLICATES, "user_tester_2");
        assertThat(secondCommit.success()).isTrue();
        assertThat(secondCommit.topicsAdded()).isEqualTo(0);
        assertThat(secondCommit.wordsAdded()).isEqualTo(0);
        assertThat(secondCommit.wordsSkipped()).isEqualTo(1);
        assertThat(secondCommit.kanjiAdded()).isEqualTo(0);
        assertThat(secondCommit.kanjiSkipped()).isEqualTo(1);
        assertThat(secondCommit.kanjiWordsSkipped()).isEqualTo(1);

        // Xác nhận số lượng bản ghi trong database không bị tăng lên
        assertThat(topicRepository.count()).isEqualTo(topicCountAfterFirst);
        assertThat(vocabularyRepository.count()).isEqualTo(vocabCountAfterFirst);
        assertThat(kanjiEntryRepository.count()).isEqualTo(kanjiCountAfterFirst);
        assertThat(kanjiWordRepository.count()).isEqualTo(kanjiWordCountAfterFirst);
    }

    @Test
    @DisplayName("3. UPDATE_EXISTING: Cập nhật thông tin mới vào bản ghi đã tồn tại mà không tăng số lượng")
    void testUpdateExisting_UpdatesDataWithoutDuplicates() throws IOException {
        String testTopicName = "Chủ đề cập nhật 3";

        // Lần 1: Tạo bản ghi ban đầu
        byte[] initialXlsx = createStandardImportWorkbook(
                testTopicName,
                java.util.Collections.singletonList(new String[]{"花（はな）", "hana", "Bông hoa cũ"}),
                null, null, null, null, null, null, null
        );
        MockMultipartFile file1 = new MockMultipartFile("file", "update_test_v1.xlsx", "application/octet-stream", initialXlsx);
        importService.commit(file1, ImportMode.SKIP_DUPLICATES, "user_tester_3");

        long vocabCountBefore = vocabularyRepository.count();

        // Lần 2: Cập nhật nghĩa tiếng Việt và Romaji với UPDATE_EXISTING
        byte[] updatedXlsx = createStandardImportWorkbook(
                testTopicName,
                java.util.Collections.singletonList(new String[]{"花（はな）", "hana_updated", "Bông hoa tươi mới đã cập nhật"}),
                null, null, null, null, null, null, null
        );
        MockMultipartFile file2 = new MockMultipartFile("file", "update_test_v2.xlsx", "application/octet-stream", updatedXlsx);
        ImportCommitResult updateResult = importService.commit(file2, ImportMode.UPDATE_EXISTING, "user_tester_3");

        assertThat(updateResult.success()).isTrue();
        assertThat(updateResult.wordsUpdated()).isEqualTo(1);
        assertThat(updateResult.wordsAdded()).isEqualTo(0);
        assertThat(vocabularyRepository.count()).isEqualTo(vocabCountBefore);

        // Kiểm tra dữ liệu trong DB đã được cập nhật
        Topic topic = topicRepository.findByNameIgnoreCase(testTopicName).orElseThrow();
        Vocabulary updatedVocab = vocabularyRepository.findByTopicIdAndJapaneseText(topic.getId(), "花（はな）").orElseThrow();
        assertThat(updatedVocab.getRomaji()).isEqualTo("hana_updated");
        assertThat(updatedVocab.getMeaningVi()).isEqualTo("Bông hoa tươi mới đã cập nhật");
    }

    @Test
    @DisplayName("4. Rollback toàn bộ khi có một hàng lỗi: Không ghi một phần, DB giữ nguyên và lưu Audit FAILED")
    void testRollbackWhenAnyRowError_RollsBackAllAndReturnsErrors() throws IOException {
        long topicCountBefore = topicRepository.count();
        long vocabCountBefore = vocabularyRepository.count();

        // Tạo file có 2 hàng: hàng 1 hợp lệ, hàng 2 bị lỗi (thiếu Romaji và Nghĩa tiếng Việt)
        byte[] errorXlsx = createWorkbookWithInvalidRow(
                "Chủ đề lỗi rollback",
                "魚（さかな）", "sakana", "Con cá",
                "鳥（とり）", "", "" // Hàng lỗi thiếu trường bắt buộc
        );

        MockMultipartFile file = new MockMultipartFile("file", "rollback_test.xlsx", "application/octet-stream", errorXlsx);

        // Kỳ vọng ném ngoại lệ ImportRollbackException
        assertThatThrownBy(() -> importService.commit(file, ImportMode.SKIP_DUPLICATES, "user_tester_4"))
                .isInstanceOf(ImportRollbackException.class)
                .satisfies(ex -> {
                    ImportRollbackException rollbackEx = (ImportRollbackException) ex;
                    assertThat(rollbackEx.getErrors()).isNotEmpty();
                    assertThat(rollbackEx.getErrors().get(0).message()).contains("Thiếu dữ liệu cột");
                });

        // Kiểm tra DB: hoàn tác 100%, không lưu dở dang "Chủ đề lỗi rollback" hay "魚（さかな）"
        assertThat(topicRepository.count()).isEqualTo(topicCountBefore);
        assertThat(vocabularyRepository.count()).isEqualTo(vocabCountBefore);
        assertThat(topicRepository.findByNameIgnoreCase("Chủ đề lỗi rollback")).isEmpty();

        // Kiểm tra Audit Log có ghi nhận trạng thái FAILED
        List<ImportAuditLog> logs = auditLogRepository.findByUserIdOrderByImportedAtDesc("user_tester_4");
        assertThat(logs).isNotEmpty();
        assertThat(logs.get(0).getResult()).isEqualTo("FAILED");
        assertThat(logs.get(0).getRecordsAdded()).isEqualTo(0);
    }

    @Test
    @DisplayName("5. Hai chủ đề chung tên khác hoa/thường: Tự động gộp thành một chủ đề duy nhất trong Database")
    void testTwoTopicsSameNameDifferentCase_MergedIntoSingleTopic() throws IOException {
        long topicCountBefore = topicRepository.count();

        // Tạo file có 2 hàng: hàng 1 chủ đề "CHỦ ĐỀ CHỮ HOA", hàng 2 chủ đề "chủ đề chữ hoa"
        byte[] mixedCaseXlsx = createWorkbookWithTwoTopicCases(
                "CHỦ ĐỀ CHỮ HOA VÀ THƯỜNG", "雨（あめ）", "ame", "Mưa",
                "chủ đề chữ hoa và thường", "雪（ゆき）", "yuki", "Tuyết"
        );

        MockMultipartFile file = new MockMultipartFile("file", "mixed_case_topics.xlsx", "application/octet-stream", mixedCaseXlsx);
        ImportCommitResult result = importService.commit(file, ImportMode.SKIP_DUPLICATES, "user_tester_5");

        assertThat(result.success()).isTrue();
        // Chỉ thêm duy nhất 1 Topic mới
        assertThat(result.topicsAdded()).isEqualTo(1);
        assertThat(result.wordsAdded()).isEqualTo(2);

        // Số topic trong DB chỉ tăng đúng 1
        assertThat(topicRepository.count()).isEqualTo(topicCountBefore + 1);

        Topic mergedTopic = topicRepository.findByNameIgnoreCase("CHỦ ĐỀ CHỮ HOA VÀ THƯỜNG").orElseThrow();
        List<Vocabulary> vocabularies = vocabularyRepository.findByTopicIdOrderBySourceLineAsc(mergedTopic.getId());
        assertThat(vocabularies).hasSize(2);
        assertThat(vocabularies).extracting(Vocabulary::getJapaneseText).containsExactly("雨（あめ）", "雪（ゆき）");
    }

    // --- Helper Methods tạo file Excel test in-memory ---

    private byte[] createStandardImportWorkbook(String wordTopic, List<String[]> words,
                                               String kanjiTopic, String kanjiChar, String hanViet,
                                               String meaningVi, String onyomi, String kunyomi,
                                               List<String[]> kanjiWords) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            if (wordTopic != null && words != null) {
                Sheet wordSheet = wb.createSheet("New Word");
                // Header
                Row h = wordSheet.createRow(0);
                h.createCell(0).setCellValue("Topic");
                h.createCell(1).setCellValue("Tiếng Nhật");
                h.createCell(2).setCellValue("Romaji");
                h.createCell(3).setCellValue("Nghĩa tiếng Việt");

                int r = 1;
                for (String[] w : words) {
                    Row row = wordSheet.createRow(r++);
                    if (r == 2) {
                        row.createCell(0).setCellValue(wordTopic);
                    }
                    row.createCell(1).setCellValue(w[0]);
                    row.createCell(2).setCellValue(w[1]);
                    row.createCell(3).setCellValue(w[2]);
                }
            }

            if (kanjiTopic != null && kanjiChar != null) {
                Sheet kanjiSheet = wb.createSheet("Kanji");
                // Header 1
                Row h1 = kanjiSheet.createRow(0);
                h1.createCell(0).setCellValue("Topic");
                h1.createCell(1).setCellValue("Kanji");
                h1.createCell(2).setCellValue("Hán Việt");
                h1.createCell(3).setCellValue("Ý nghĩa");
                h1.createCell(4).setCellValue("On");
                h1.createCell(5).setCellValue("Kun");
                h1.createCell(6).setCellValue("Word");
                kanjiSheet.addMergedRegion(new CellRangeAddress(0, 0, 6, 8));

                // Header 2
                Row h2 = kanjiSheet.createRow(1);
                h2.createCell(6).setCellValue("Tiếng Nhật");
                h2.createCell(7).setCellValue("Romaji");
                h2.createCell(8).setCellValue("Nghĩa tiếng Việt");

                // Data row
                Row d1 = kanjiSheet.createRow(2);
                d1.createCell(0).setCellValue(kanjiTopic);
                d1.createCell(1).setCellValue(kanjiChar);
                d1.createCell(2).setCellValue(hanViet);
                d1.createCell(3).setCellValue(meaningVi);
                d1.createCell(4).setCellValue(onyomi);
                d1.createCell(5).setCellValue(kunyomi);
                if (kanjiWords != null && !kanjiWords.isEmpty()) {
                    d1.createCell(6).setCellValue(kanjiWords.get(0)[0]);
                    d1.createCell(7).setCellValue(kanjiWords.get(0)[1]);
                    d1.createCell(8).setCellValue(kanjiWords.get(0)[2]);
                }

                if (kanjiWords != null && kanjiWords.size() > 1) {
                    for (int i = 1; i < kanjiWords.size(); i++) {
                        Row dNext = kanjiSheet.createRow(2 + i);
                        dNext.createCell(6).setCellValue(kanjiWords.get(i)[0]);
                        dNext.createCell(7).setCellValue(kanjiWords.get(i)[1]);
                        dNext.createCell(8).setCellValue(kanjiWords.get(i)[2]);
                    }
                }
            }

            wb.write(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createWorkbookWithInvalidRow(String topic, String jp1, String ro1, String me1,
                                                String jp2, String ro2, String me2) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("New Word");
            Row h = sheet.createRow(0);
            h.createCell(0).setCellValue("Topic");
            h.createCell(1).setCellValue("Tiếng Nhật");
            h.createCell(2).setCellValue("Romaji");
            h.createCell(3).setCellValue("Nghĩa tiếng Việt");

            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue(topic);
            r1.createCell(1).setCellValue(jp1);
            r1.createCell(2).setCellValue(ro1);
            r1.createCell(3).setCellValue(me1);

            Row r2 = sheet.createRow(2);
            r2.createCell(1).setCellValue(jp2);
            r2.createCell(2).setCellValue(ro2);
            r2.createCell(3).setCellValue(me2);

            wb.write(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createWorkbookWithTwoTopicCases(String topicUpper, String jp1, String ro1, String me1,
                                                  String topicLower, String jp2, String ro2, String me2) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("New Word");
            Row h = sheet.createRow(0);
            h.createCell(0).setCellValue("Topic");
            h.createCell(1).setCellValue("Tiếng Nhật");
            h.createCell(2).setCellValue("Romaji");
            h.createCell(3).setCellValue("Nghĩa tiếng Việt");

            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue(topicUpper);
            r1.createCell(1).setCellValue(jp1);
            r1.createCell(2).setCellValue(ro1);
            r1.createCell(3).setCellValue(me1);

            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue(topicLower);
            r2.createCell(1).setCellValue(jp2);
            r2.createCell(2).setCellValue(ro2);
            r2.createCell(3).setCellValue(me2);

            wb.write(baos);
            return baos.toByteArray();
        }
    }

    @Test
    @DisplayName("6. File không hợp lệ bị validator từ chối khi commit: Ném ngoại lệ và ghi Audit Log REJECTED")
    void testCommitValidationFailure_RecordsAuditLogRejected() {
        byte[] invalidBytes = "Not an excel file content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "corrupt.xlsx", "application/octet-stream", invalidBytes);

        assertThatThrownBy(() -> importService.commit(file, ImportMode.SKIP_DUPLICATES, "user_tester_rejected"))
                .isInstanceOf(UploadRejectedException.class);

        List<ImportAuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).isNotEmpty();
        ImportAuditLog lastLog = logs.get(logs.size() - 1);
        assertThat(lastLog.getResult()).isEqualTo("REJECTED");
        assertThat(lastLog.getFileName()).isEqualTo("corrupt.xlsx");
        assertThat(lastLog.getUserId()).isEqualTo("user_tester_rejected");
        assertThat(lastLog.getErrorMessage()).contains("Tệp tin không đúng định dạng bảng tính Excel");
    }
}
