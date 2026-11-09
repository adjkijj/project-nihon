package com.nihon.service.excel;

import com.nihon.dto.excel.ImportCommitResult;
import com.nihon.dto.excel.ImportMode;
import com.nihon.dto.excel.ImportUndoPreview;
import com.nihon.dto.excel.ImportUndoResult;
import com.nihon.entity.ImportAuditLog;
import com.nihon.entity.KanjiEntry;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.repository.ImportAuditLogRepository;
import com.nihon.repository.KanjiEntryRepository;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VocabularyRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

/**
 * Kiểm thử tích hợp toàn diện cho tính năng Hoàn tác (Undo) một lần import:
 * 1) Import rồi hoàn tác: database về trạng thái trước import (kể cả đếm riêng bản ghi đã sửa sau import và ghi log UNDO).
 * 2) Hoàn tác hai lần: lần hai bị từ chối.
 * 3) Chủ đề có dữ liệu import sau đó được giữ lại, từ vựng của batch bị xóa bình thường.
 * 4) Bản ghi cũ (batch khác hoặc null) không bị xóa.
 * 5) Lỗi giữa chừng: rollback toàn bộ.
 */
@SpringBootTest
class ImportUndoIntegrationTest {

    @Autowired
    private ImportService importService;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private VocabularyRepository vocabularyRepository;

    @Autowired
    private KanjiEntryRepository kanjiEntryRepository;

    @Autowired
    private ImportAuditLogRepository auditLogRepository;

    @Autowired
    private ImportAuditService auditService;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager txManager;

    @Test
    @DisplayName("1. Import rồi hoàn tác: database về trạng thái trước import, đếm riêng bản ghi sửa sau import và ghi dòng audit UNDO")
    void importThenUndo_restoresDatabaseToPreImportStateAndLogsUndo() throws IOException {
        long topicCountBefore = topicRepository.count();
        long vocabCountBefore = vocabularyRepository.count();
        long kanjiCountBefore = kanjiEntryRepository.count();

        byte[] xlsxBytes = createWorkbook(
                "Chủ đề Undo Test 1",
                List.of(
                        new String[]{"新幹線（しんかんせん）", "shinkansen", "Tàu siêu tốc"},
                        new String[]{"切符（きっぷ）", "kippu", "Vé"}
                ),
                "Kanji Undo Test 1",
                "驛", "DỊCH", "Nhà ga cổ", "エキ", "うまや",
                List.<String[]>of(
                        new String[]{"驛前（えきまえ）", "ekimae", "Trước nhà ga"}
                )
        );

        ImportCommitResult commitResult = importService.commit(
                xlsxBytes, "undo_test_1.xlsx", ImportMode.SKIP_DUPLICATES, "127.0.0.1"
        );
        assertThat(commitResult.success()).isTrue();
        assertThat(topicRepository.count()).isEqualTo(topicCountBefore + 2);
        assertThat(vocabularyRepository.count()).isEqualTo(vocabCountBefore + 3);
        assertThat(kanjiEntryRepository.count()).isEqualTo(kanjiCountBefore + 1);

        // Lấy dòng audit vừa tạo
        List<ImportAuditLog> logs = auditLogRepository.findAll();
        ImportAuditLog batchLog = logs.stream()
                .filter(l -> "undo_test_1.xlsx".equals(l.getFileName()) && "SUCCESS".equals(l.getResult()))
                .reduce((first, second) -> second)
                .orElseThrow();
        Long batchId = batchLog.getId();

        // Kiểm tra mọi bản ghi mới được gắn đúng import_batch_id
        assertThat(topicRepository.countByImportBatchId(batchId)).isEqualTo(2);
        assertThat(vocabularyRepository.countByImportBatchId(batchId)).isEqualTo(3);
        assertThat(kanjiEntryRepository.countByImportBatchId(batchId)).isEqualTo(1);

        // Giả lập người dùng sửa 1 từ vựng sau khi import (updated_at > imported_at)
        Topic wordTopic = topicRepository.findByNameIgnoreCase("Chủ đề Undo Test 1").orElseThrow();
        Vocabulary editedVocab = vocabularyRepository.findByTopicIdAndJapaneseText(wordTopic.getId(), "切符（きっぷ）").orElseThrow();
        editedVocab.setMeaningVi("Vé tàu (đã sửa sau import)");
        editedVocab.setUpdatedAt(batchLog.getImportedAt().plusMinutes(5));
        vocabularyRepository.saveAndFlush(editedVocab);

        // Kiểm tra bước xem trước (previewUndo) tính trước bằng truy vấn đếm
        ImportUndoPreview preview = importService.previewUndo(batchId);
        assertThat(preview.vocabToDelete()).isEqualTo(3);
        assertThat(preview.kanjiToDelete()).isEqualTo(1);
        assertThat(preview.topicsToDelete()).isEqualTo(2);
        assertThat(preview.topicsRetained()).isZero();
        assertThat(preview.modifiedAfterImportCount()).isEqualTo(1);
        assertThat(preview.getConfirmMessage()).contains("Sẽ xóa 6 bản ghi (3 từ vựng, 1 Kanji, 2 chủ đề)");
        assertThat(preview.getConfirmMessage()).contains("1 bản ghi đã bị chỉnh sửa sau khi import");

        // Thực hiện hoàn tác
        ImportUndoResult undoResult = importService.undoImport(batchId, "127.0.0.1");
        assertThat(undoResult.vocabDeleted()).isEqualTo(3);
        assertThat(undoResult.kanjiDeleted()).isEqualTo(1);
        assertThat(undoResult.topicsDeleted()).isEqualTo(2);
        assertThat(undoResult.topicsRetained()).isZero();
        assertThat(undoResult.modifiedAfterImportDeleted()).isEqualTo(1);
        assertThat(undoResult.toNotificationMessage()).contains("1 bản ghi đã bị người dùng sửa sau khi import");

        // Database trở về đúng trạng thái trước khi import
        assertThat(topicRepository.count()).isEqualTo(topicCountBefore);
        assertThat(vocabularyRepository.count()).isEqualTo(vocabCountBefore);
        assertThat(kanjiEntryRepository.count()).isEqualTo(kanjiCountBefore);
        assertThat(topicRepository.findByNameIgnoreCase("Chủ đề Undo Test 1")).isEmpty();
        assertThat(topicRepository.findByNameIgnoreCase("Kanji Undo Test 1")).isEmpty();
        assertThat(kanjiEntryRepository.findByCharacter("驛")).isEmpty();

        // Kiểm tra undone_at được ghi nhận trên dòng audit gốc
        ImportAuditLog updatedBatchLog = auditLogRepository.findById(batchId).orElseThrow();
        assertThat(updatedBatchLog.getUndoneAt()).isNotNull();
        assertThat(updatedBatchLog.isCanUndo()).isFalse();

        // Kiểm tra dòng audit mới với kết quả UNDO và số bản ghi xóa
        assertThat(undoResult.undoAuditLogId()).isNotNull();
        ImportAuditLog undoAuditLog = auditLogRepository.findById(undoResult.undoAuditLogId()).orElseThrow();
        assertThat(undoAuditLog.getResult()).isEqualTo("UNDO");
        assertThat(undoAuditLog.getRecordsUpdated()).isEqualTo(6);
        assertThat(undoAuditLog.getErrorMessage()).contains("Đã xóa 6 bản ghi");
    }

    @Test
    @DisplayName("2. Hoàn tác hai lần: lần hai bị từ chối với thông báo lỗi rõ ràng")
    void undoTwice_secondAttemptIsRejected() throws IOException {
        byte[] xlsxBytes = createWorkbook(
                "Chủ đề Undo Hai Lần",
                List.<String[]>of(new String[]{"空港（くうこう）", "kuukou", "Sân bay"}),
                null, null, null, null, null, null, null
        );

        importService.commit(xlsxBytes, "undo_twice.xlsx", ImportMode.SKIP_DUPLICATES, "127.0.0.1");
        ImportAuditLog batchLog = auditLogRepository.findAll().stream()
                .filter(l -> "undo_twice.xlsx".equals(l.getFileName()) && "SUCCESS".equals(l.getResult()))
                .reduce((first, second) -> second)
                .orElseThrow();

        // Lần 1: thành công
        ImportUndoResult firstUndo = importService.undoImport(batchLog.getId(), "127.0.0.1");
        assertThat(firstUndo.totalDeleted()).isEqualTo(2);

        // Lần 2: bị từ chối vì đã hoàn tác (undone_at != null)
        assertThatThrownBy(() -> importService.undoImport(batchLog.getId(), "127.0.0.1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("đã được hoàn tác");
    }

    @Test
    @DisplayName("3. Chủ đề có dữ liệu import sau đó được giữ lại và thông báo; từ vựng của batch bị xóa bình thường")
    void undoWhenTopicHasSubsequentImportData_retainsTopicAndDeletesBatchVocab() throws IOException {
        String sharedTopicName = "Chủ đề Dùng Chung Hai Batch";

        // Batch 1: tạo mới chủ đề và thêm 2 từ vựng
        byte[] batch1Bytes = createWorkbook(
                sharedTopicName,
                List.of(
                        new String[]{" 비행 -> 飛行機（ひこうき）".substring(6), "hikouki", "Máy bay"},
                        new String[]{"船（ふね）", "fune", "Tàu thủy"}
                ),
                null, null, null, null, null, null, null
        );
        importService.commit(batch1Bytes, "batch1_shared_topic.xlsx", ImportMode.SKIP_DUPLICATES, "127.0.0.1");
        ImportAuditLog batch1Log = auditLogRepository.findAll().stream()
                .filter(l -> "batch1_shared_topic.xlsx".equals(l.getFileName()) && "SUCCESS".equals(l.getResult()))
                .reduce((first, second) -> second)
                .orElseThrow();

        // Batch 2: import thêm 1 từ vựng mới vào cùng chủ đề đó
        byte[] batch2Bytes = createWorkbook(
                sharedTopicName,
                List.<String[]>of(
                        new String[]{"自転車（じてんしゃ）", "jitensha", "Xe đạp"}
                ),
                null, null, null, null, null, null, null
        );
        importService.commit(batch2Bytes, "batch2_shared_topic.xlsx", ImportMode.SKIP_DUPLICATES, "127.0.0.1");
        ImportAuditLog batch2Log = auditLogRepository.findAll().stream()
                .filter(l -> "batch2_shared_topic.xlsx".equals(l.getFileName()) && "SUCCESS".equals(l.getResult()))
                .reduce((first, second) -> second)
                .orElseThrow();

        // Xem trước hoàn tác Batch 1: báo xóa 2 từ vựng, xóa 0 chủ đề, giữ lại 1 chủ đề
        ImportUndoPreview preview1 = importService.previewUndo(batch1Log.getId());
        assertThat(preview1.vocabToDelete()).isEqualTo(2);
        assertThat(preview1.topicsToDelete()).isZero();
        assertThat(preview1.topicsRetained()).isEqualTo(1);
        assertThat(preview1.retainedTopicNames()).containsExactly(sharedTopicName);

        // Hoàn tác Batch 1
        ImportUndoResult undo1 = importService.undoImport(batch1Log.getId(), "127.0.0.1");
        assertThat(undo1.vocabDeleted()).isEqualTo(2);
        assertThat(undo1.topicsDeleted()).isZero();
        assertThat(undo1.topicsRetained()).isEqualTo(1);
        assertThat(undo1.retainedTopicNames()).containsExactly(sharedTopicName);
        assertThat(undo1.toNotificationMessage()).contains("Giữ lại 1 chủ đề").contains(sharedTopicName);

        // Chủ đề vẫn tồn tại và chỉ còn đúng từ vựng của Batch 2
        Topic retainedTopic = topicRepository.findByNameIgnoreCase(sharedTopicName).orElseThrow();
        List<Vocabulary> remainingWords = vocabularyRepository.findByTopicIdOrderBySourceLineAsc(retainedTopic.getId());
        assertThat(remainingWords).hasSize(1);
        assertThat(remainingWords.get(0).getJapaneseText()).isEqualTo("自転車（じてんしゃ）");
        assertThat(remainingWords.get(0).getImportBatchId()).isEqualTo(batch2Log.getId());

        // Dọn dẹp sau kiểm thử: hoàn tác Batch 2 và xóa chủ đề
        importService.undoImport(batch2Log.getId(), "127.0.0.1");
        topicRepository.delete(retainedTopic);
    }

    @Test
    @DisplayName("4. Bản ghi cũ (batch khác hoặc import_batch_id = null) không bị xóa kể cả khi được cập nhật trong lần import bị hoàn tác")
    void undoImport_doesNotDeleteOldRecordsWithNullOrDifferentBatchId() throws IOException {
        // Tạo sẵn 1 chủ đề và 1 từ vựng cũ có import_batch_id = null
        Topic legacyTopic = new Topic();
        legacyTopic.setName("Chủ đề Cũ Không Batch");
        legacyTopic.setSortOrder(9999);
        legacyTopic.setKanjiSection(false);
        legacyTopic.setImportBatchId(null);
        legacyTopic = topicRepository.saveAndFlush(legacyTopic);

        Vocabulary legacyVocab = new Vocabulary();
        legacyVocab.setTopic(legacyTopic);
        legacyVocab.setJapaneseText("古い言葉（ふるいことば）");
        legacyVocab.setReading("ふるいことば");
        legacyVocab.setRomaji("furuikotoba");
        legacyVocab.setMeaningVi("Từ cổ");
        legacyVocab.setHasKanji(true);
        legacyVocab.setImportBatchId(null);
        legacyVocab = vocabularyRepository.saveAndFlush(legacyVocab);

        long totalNullBatchVocabBefore = vocabularyRepository.findAll().stream()
                .filter(v -> v.getImportBatchId() == null)
                .count();

        // Import với UPDATE_EXISTING: cập nhật từ cũ (import_batch_id = null) và thêm 1 từ mới
        byte[] xlsxBytes = createWorkbook(
                "Chủ đề Cũ Không Batch",
                List.of(
                        new String[]{"古い言葉（ふるいことば）", "furuikotoba_updated", "Từ cổ (đã cập nhật)"},
                        new String[]{"新しい言葉（あたらしいことば）", "atarashiikotoba", "Từ mới"}
                ),
                null, null, null, null, null, null, null
        );

        ImportCommitResult commitResult = importService.commit(
                xlsxBytes, "update_and_add.xlsx", ImportMode.UPDATE_EXISTING, "127.0.0.1"
        );
        assertThat(commitResult.wordsUpdated()).isEqualTo(1);
        assertThat(commitResult.wordsAdded()).isEqualTo(1);

        ImportAuditLog batchLog = auditLogRepository.findAll().stream()
                .filter(l -> "update_and_add.xlsx".equals(l.getFileName()) && "SUCCESS".equals(l.getResult()))
                .reduce((first, second) -> second)
                .orElseThrow();

        // Kiểm tra từ cũ vẫn giữ nguyên import_batch_id = null
        Vocabulary reloadedLegacy = vocabularyRepository.findById(legacyVocab.getId()).orElseThrow();
        assertThat(reloadedLegacy.getImportBatchId()).isNull();

        // Hoàn tác lần import này: chỉ xóa từ mới ("新しい言葉"), giữ nguyên từ cũ và chủ đề cũ
        ImportUndoResult undoResult = importService.undoImport(batchLog.getId(), "127.0.0.1");
        assertThat(undoResult.vocabDeleted()).isEqualTo(1);
        assertThat(undoResult.topicsDeleted()).isZero();

        assertThat(vocabularyRepository.findById(legacyVocab.getId())).isPresent();
        assertThat(topicRepository.findById(legacyTopic.getId())).isPresent();
        long totalNullBatchVocabAfter = vocabularyRepository.findAll().stream()
                .filter(v -> v.getImportBatchId() == null)
                .count();
        assertThat(totalNullBatchVocabAfter).isEqualTo(totalNullBatchVocabBefore);

        // Dọn dẹp bản ghi test
        vocabularyRepository.deleteById(legacyVocab.getId());
        topicRepository.deleteById(legacyTopic.getId());
    }

    @Test
    @DisplayName("5. Lỗi giữa chừng khi hoàn tác: rollback toàn bộ dữ liệu và trạng thái undone_at")
    void undoImport_whenErrorOccursMidway_rollsBackEverything() throws IOException {
        byte[] xlsxBytes = createWorkbook(
                "Chủ đề Rollback Undo",
                List.<String[]>of(new String[]{"電車（でんしゃ）", "densha", "Tàu điện"}),
                "Kanji Rollback Undo",
                "轍", "TRIỆT", "Vết bánh xe", "テツ", "わだち",
                null
        );

        importService.commit(xlsxBytes, "rollback_undo.xlsx", ImportMode.SKIP_DUPLICATES, "127.0.0.1");
        ImportAuditLog batchLog = auditLogRepository.findAll().stream()
                .filter(l -> "rollback_undo.xlsx".equals(l.getFileName()) && "SUCCESS".equals(l.getResult()))
                .reduce((first, second) -> second)
                .orElseThrow();
        Long batchId = batchLog.getId();

        // Tạo proxy bọc KanjiEntryRepository để ném lỗi giữa chừng (sau khi vocabulary đã bị xóa ở bước 1)
        KanjiEntryRepository failingKanjiRepo = (KanjiEntryRepository) java.lang.reflect.Proxy.newProxyInstance(
                KanjiEntryRepository.class.getClassLoader(),
                new Class<?>[]{KanjiEntryRepository.class},
                (proxy, method, args) -> {
                    if ("deleteAllInBatch".equals(method.getName())) {
                        throw new RuntimeException("Lỗi giả lập giữa chừng khi xóa Kanji");
                    }
                    return method.invoke(kanjiEntryRepository, args);
                }
        );

        ImportService serviceWithSpy = new ImportService(
                new ExcelUploadValidator(new com.nihon.config.ExcelUploadProperties()),
                new WordSheetParser(),
                new KanjiSheetParser(),
                topicRepository,
                vocabularyRepository,
                failingKanjiRepo,
                auditService
        );

        // Chạy trong TransactionTemplate để kiểm tra rollback thực tế của Spring TransactionManager
        assertThatThrownBy(() -> {
            org.springframework.transaction.support.TransactionTemplate txTemplate =
                    new org.springframework.transaction.support.TransactionTemplate(txManager);
            txTemplate.executeWithoutResult(status -> serviceWithSpy.undoImport(batchId, "127.0.0.1"));
        }).isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Lỗi giả lập giữa chừng");

        // Kiểm tra toàn bộ dữ liệu của batch (kể cả vocabulary ở bước 1) đã được rollback nguyên vẹn
        assertThat(vocabularyRepository.countByImportBatchId(batchId)).isEqualTo(1);
        assertThat(kanjiEntryRepository.countByImportBatchId(batchId)).isEqualTo(1);
        assertThat(topicRepository.countByImportBatchId(batchId)).isEqualTo(2);

        ImportAuditLog reloadedAudit = auditLogRepository.findById(batchId).orElseThrow();
        assertThat(reloadedAudit.getUndoneAt()).isNull();

        // Dọn dẹp bằng cách hoàn tác bình thường
        importService.undoImport(batchId, "127.0.0.1");
    }

    @Test
    @DisplayName("Migration V6 chạy an toàn trên DB cũ và giữ nguyên dữ liệu hiện có (import_batch_id = NULL)")
    void migrationV6_isIdempotentAndPreservesExistingData() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:v6_test_db;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        JdbcTemplate jdbc = new JdbcTemplate(ds);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);
        new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V6__add_import_batch_id_and_undone_at.sql")).execute(ds);
        // Chạy lại V6 lần 2 (idempotent)
        new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V6__add_import_batch_id_and_undone_at.sql")).execute(ds);

        jdbc.update("INSERT INTO topic (name, sort_order, kanji_section, created_at) VALUES ('Bài 1', 1, FALSE, CURRENT_TIMESTAMP)");
        Long topicId = jdbc.queryForObject("SELECT id FROM topic WHERE name = 'Bài 1'", Long.class);
        Long batchId = jdbc.queryForObject("SELECT import_batch_id FROM topic WHERE id = ?", Long.class, topicId);
        assertThat(batchId).isNull();
    }

    private byte[] createWorkbook(String wordTopic, List<String[]> words,
                                  String kanjiTopic, String kanjiChar, String hanViet,
                                  String meaningVi, String onyomi, String kunyomi,
                                  List<String[]> kanjiWords) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            if (wordTopic != null && words != null) {
                Sheet wordSheet = wb.createSheet("New Word");
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
                Row h1 = kanjiSheet.createRow(0);
                h1.createCell(0).setCellValue("Topic");
                h1.createCell(1).setCellValue("Kanji");
                h1.createCell(2).setCellValue("Hán Việt");
                h1.createCell(3).setCellValue("Ý nghĩa");
                h1.createCell(4).setCellValue("On");
                h1.createCell(5).setCellValue("Kun");
                h1.createCell(6).setCellValue("Từ cần nhớ");
                kanjiSheet.addMergedRegion(new CellRangeAddress(0, 0, 6, 8));

                Row h2 = kanjiSheet.createRow(1);
                h2.createCell(6).setCellValue("Tiếng Nhật");
                h2.createCell(7).setCellValue("Romaji");
                h2.createCell(8).setCellValue("Nghĩa tiếng Việt");

                Row d1 = kanjiSheet.createRow(2);
                d1.createCell(0).setCellValue(kanjiTopic);
                d1.createCell(1).setCellValue(kanjiChar);
                d1.createCell(2).setCellValue(hanViet);
                d1.createCell(3).setCellValue(meaningVi);
                d1.createCell(4).setCellValue(onyomi);
                d1.createCell(5).setCellValue(kunyomi);

                int r = 3;
                if (kanjiWords != null) {
                    for (String[] kw : kanjiWords) {
                        Row wRow = kanjiSheet.createRow(r++);
                        wRow.createCell(6).setCellValue(kw[0]);
                        wRow.createCell(7).setCellValue(kw[1]);
                        wRow.createCell(8).setCellValue(kw[2]);
                    }
                }
            }

            wb.write(baos);
            return baos.toByteArray();
        }
    }
}
