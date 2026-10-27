package com.nihon.service.excel;

import com.nihon.dto.excel.ImportCommitResult;
import com.nihon.dto.excel.ImportMode;
import com.nihon.dto.excel.ImportPreviewReport;
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
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bộ kiểm thử toàn trình End-to-End (E2E) cho tính năng Import học liệu:
 * 1. Với tệp Update.xlsx chứa đầy đủ dữ liệu mẫu (ít nhất 2 chủ đề/sheet, mỗi chủ đề >= 3 từ,
 *    3 Kanji: 1 Kanji nhiều từ, 1 Kanji 1 từ, 1 Kanji không có từ).
 * 2. Thực hiện: Preview -> Commit -> Import lại cùng file kiểm tra không trùng lặp (SKIP_DUPLICATES).
 * 3. Kiểm tra dữ liệu trong database khớp với file: số chủ đề, số từ, số Kanji, số từ Kanji,
 *    ký tự tiếng Nhật Unicode và tiếng Việt có dấu hiển thị chuẩn xác.
 * 4. Thử nghiệm 6 ca file xấu (Negative test cases):
 *    - Sai phần mở rộng
 *    - File text giả đổi đuôi .xlsx (sai magic bytes)
 *    - File chứa Macro (vbaProject.bin)
 *    - File vượt quá kích thước cho phép (> 5 MB)
 *    - File chứa công thức độc hại
 *    - File chứa ô có số thẻ hợp lệ theo thuật toán Luhn
 */
@SpringBootTest
class EndToEndImportFlowTest {

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
    @DisplayName("E2E-1: Luồng hoàn chỉnh với Update.xlsx (Preview -> Commit -> DB Check -> Re-import No Duplicates)")
    void testEndToEndImportWithUpdateExcel() throws IOException {
        File templateFile = new File("src/main/resources/static/template/Update.xlsx");
        if (!templateFile.exists()) {
            templateFile = new File("Update.xlsx");
        }
        assertThat(templateFile).exists();

        byte[] fileBytes;
        try (FileInputStream fis = new FileInputStream(templateFile)) {
            fileBytes = fis.readAllBytes();
        }

        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "Update.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                fileBytes
        );

        // --- BƯỚC 1: PREVIEW ---
        ImportPreviewReport preview = importService.preview(multipartFile);
        assertThat(preview.errors()).isEmpty();
        // File Update.xlsx mẫu có 2 chủ đề New Word + 2 chủ đề Kanji = 4 chủ đề
        assertThat(preview.newTopicsCount()).isGreaterThanOrEqualTo(2);
        // Có 6 từ vựng New Word
        assertThat(preview.newWordsCount()).isGreaterThanOrEqualTo(6);
        // Có 3 Kanji
        assertThat(preview.newKanjiCount()).isGreaterThanOrEqualTo(3);
        // Có 4 từ cần nhớ của Kanji (3 từ của '日', 1 từ của '月')
        assertThat(preview.newKanjiWordsCount()).isGreaterThanOrEqualTo(4);

        long topicCountBefore = topicRepository.count();
        long vocabCountBefore = vocabularyRepository.count();
        long kanjiCountBefore = kanjiEntryRepository.count();
        long kanjiWordCountBefore = kanjiWordRepository.count();

        // --- BƯỚC 2: COMMIT LẦN 1 ---
        ImportCommitResult commitResult = importService.commit(multipartFile, ImportMode.SKIP_DUPLICATES, "e2e_tester");
        assertThat(commitResult.success()).isTrue();
        assertThat(commitResult.wordsAdded()).isGreaterThanOrEqualTo(6);
        assertThat(commitResult.kanjiAdded()).isGreaterThanOrEqualTo(3);
        assertThat(commitResult.kanjiWordsAdded()).isGreaterThanOrEqualTo(4);

        // --- BƯỚC 3: KIỂM TRA DỮ LIỆU TRONG DATABASE ---
        // 1. Kiểm tra chủ đề và từ vựng tiếng Việt có dấu
        Optional<Topic> topic1Opt = topicRepository.findByNameIgnoreCase("Chủ đề 1: Giao tiếp thường ngày");
        assertThat(topic1Opt).isPresent();
        Topic topic1 = topic1Opt.get();

        Optional<Vocabulary> v1 = vocabularyRepository.findByTopicIdAndJapaneseText(topic1.getId(), "おはよう（ございます）");
        assertThat(v1).isPresent();
        assertThat(v1.get().getRomaji()).isEqualTo("ohayou");
        assertThat(v1.get().getMeaningVi()).isEqualTo("Chào buổi sáng tốt lành");

        Optional<Vocabulary> v2 = vocabularyRepository.findByTopicIdAndJapaneseText(topic1.getId(), "こんにちは");
        assertThat(v2).isPresent();
        assertThat(v2.get().getMeaningVi()).isEqualTo("Chào buổi trưa và chiều");

        Optional<Topic> topic2Opt = topicRepository.findByNameIgnoreCase("Chủ đề 2: Thời tiết bốn mùa");
        assertThat(topic2Opt).isPresent();
        Topic topic2 = topic2Opt.get();

        Optional<Vocabulary> v3 = vocabularyRepository.findByTopicIdAndJapaneseText(topic2.getId(), "雨（あめ）");
        assertThat(v3).isPresent();
        assertThat(v3.get().getMeaningVi()).isEqualTo("Cơn mưa rào mùa hạ");

        // 2. Kiểm tra Kanji:
        // Kanji 1: '波' có 3 từ cần nhớ
        Optional<KanjiEntry> kanji1Opt = kanjiEntryRepository.findByCharacter("波");
        assertThat(kanji1Opt).isPresent();
        KanjiEntry kanji1 = kanji1Opt.get();
        assertThat(kanji1.getHanViet()).isEqualTo("BA");
        assertThat(kanji1.getMeaningVi()).isEqualTo("Sóng biển, làn sóng");
        List<KanjiWord> kanji1Words = kanjiWordRepository.findByKanjiIdOrderBySortOrderAsc(kanji1.getId());
        assertThat(kanji1Words).hasSizeGreaterThanOrEqualTo(3);
        assertThat(kanji1Words.stream().anyMatch(w -> "波（なみ）".equals(w.getJapanese()) && "Sóng biển nhấp nhô".equals(w.getMeaningVi()))).isTrue();
        assertThat(kanji1Words.stream().anyMatch(w -> "津波（つなみ）".equals(w.getJapanese()) && "Sóng thần dữ dội".equals(w.getMeaningVi()))).isTrue();
        assertThat(kanji1Words.stream().anyMatch(w -> "電波（でんぱ）".equals(w.getJapanese()) && "Sóng điện từ viễn thông".equals(w.getMeaningVi()))).isTrue();

        // Kanji 2: '橋' có 1 từ cần nhớ
        Optional<KanjiEntry> kanji2Opt = kanjiEntryRepository.findByCharacter("橋");
        assertThat(kanji2Opt).isPresent();
        KanjiEntry kanji2 = kanji2Opt.get();
        assertThat(kanji2.getHanViet()).isEqualTo("KIỀU");
        assertThat(kanji2.getMeaningVi()).isEqualTo("Cây cầu bắc qua sông");
        List<KanjiWord> kanji2Words = kanjiWordRepository.findByKanjiIdOrderBySortOrderAsc(kanji2.getId());
        assertThat(kanji2Words).hasSize(1);
        assertThat(kanji2Words.get(0).getJapanese()).isEqualTo("橋（はし）");
        assertThat(kanji2Words.get(0).getMeaningVi()).isEqualTo("Cây cầu bắc qua sông");

        // Kanji 3: '嵐' KHÔNG có từ cần nhớ
        Optional<KanjiEntry> kanji3Opt = kanjiEntryRepository.findByCharacter("嵐");
        assertThat(kanji3Opt).isPresent();
        KanjiEntry kanji3 = kanji3Opt.get();
        assertThat(kanji3.getHanViet()).isEqualTo("LAM");
        assertThat(kanji3.getMeaningVi()).isEqualTo("Cơn bão tố cuồng phong");
        List<KanjiWord> kanji3Words = kanjiWordRepository.findByKanjiIdOrderBySortOrderAsc(kanji3.getId());
        assertThat(kanji3Words).isEmpty();

        // --- BƯỚC 4: IMPORT LẠI CÙNG FILE VỚI SKIP_DUPLICATES ---
        long topicCountAfterFirst = topicRepository.count();
        long vocabCountAfterFirst = vocabularyRepository.count();
        long kanjiCountAfterFirst = kanjiEntryRepository.count();
        long kanjiWordCountAfterFirst = kanjiWordRepository.count();

        ImportCommitResult secondCommit = importService.commit(multipartFile, ImportMode.SKIP_DUPLICATES, "e2e_tester");
        assertThat(secondCommit.success()).isTrue();
        // Không có bản ghi mới nào được thêm vào
        assertThat(secondCommit.topicsAdded()).isEqualTo(0);
        assertThat(secondCommit.wordsAdded()).isEqualTo(0);
        assertThat(secondCommit.kanjiAdded()).isEqualTo(0);
        assertThat(secondCommit.kanjiWordsAdded()).isEqualTo(0);
        // Toàn bộ bản ghi cũ được đánh dấu skipped
        assertThat(secondCommit.wordsSkipped()).isGreaterThanOrEqualTo(6);
        assertThat(secondCommit.kanjiSkipped()).isGreaterThanOrEqualTo(3);
        assertThat(secondCommit.kanjiWordsSkipped()).isGreaterThanOrEqualTo(4);

        // Xác nhận số lượng bản ghi trong Database giữ nguyên tuyệt đối
        assertThat(topicRepository.count()).isEqualTo(topicCountAfterFirst);
        assertThat(vocabularyRepository.count()).isEqualTo(vocabCountAfterFirst);
        assertThat(kanjiEntryRepository.count()).isEqualTo(kanjiCountAfterFirst);
        assertThat(kanjiWordRepository.count()).isEqualTo(kanjiWordCountAfterFirst);
    }

    @Test
    @DisplayName("E2E-2: Ca xấu 1 - File sai phần mở rộng (.csv, .xls...) bị từ chối")
    void testNegativeCase1_InvalidExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "col1,col2".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> importService.preview(file))
                .isInstanceOf(UploadRejectedException.class)
                .hasMessageContaining("Chỉ chấp nhận tệp tin .xlsx");
    }

    @Test
    @DisplayName("E2E-2: Ca xấu 2 - File text đổi đuôi .xlsx (sai magic bytes) bị từ chối")
    void testNegativeCase2_FakeExtensionBadMagicBytes() {
        MockMultipartFile file = new MockMultipartFile("file", "fake.xlsx", "application/octet-stream", "This is plain text not zip".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> importService.preview(file))
                .isInstanceOf(UploadRejectedException.class)
                .hasMessageContaining("Tệp tin không đúng định dạng bảng tính Excel hợp lệ");
    }

    @Test
    @DisplayName("E2E-2: Ca xấu 3 - File chứa macro / VBA (vbaProject.bin) bị từ chối")
    void testNegativeCase3_MacroFile() throws IOException {
        byte[] validBytes = createSimpleValidXlsx();
        byte[] macroBytes = addZipEntry(validBytes, "xl/vbaProject.bin", "FAKE_VBA_BIN".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file = new MockMultipartFile("file", "macro.xlsx", "application/octet-stream", macroBytes);

        assertThatThrownBy(() -> importService.preview(file))
                .isInstanceOf(UploadRejectedException.class)
                .hasMessageContaining("Tệp tin chứa macro hoặc mã thực thi");
    }

    @Test
    @DisplayName("E2E-2: Ca xấu 4 - File quá lớn (> 5 MB) bị từ chối")
    void testNegativeCase4_FileTooLarge() {
        byte[] oversized = new byte[6 * 1024 * 1024]; // 6 MB
        MockMultipartFile file = new MockMultipartFile("file", "large.xlsx", "application/octet-stream", oversized);

        assertThatThrownBy(() -> importService.preview(file))
                .isInstanceOf(UploadRejectedException.class)
                .hasMessageContaining("Kích thước tệp tin vượt quá giới hạn");
    }

    @Test
    @DisplayName("E2E-2: Ca xấu 5 - File chứa ô công thức (=SUM...) bị phát hiện và dừng commit")
    void testNegativeCase5_FormulaCell() throws IOException {
        byte[] formulaBytes;
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("New Word");
            Row h = sheet.createRow(0);
            h.createCell(0).setCellValue("Topic");
            h.createCell(1).setCellValue("Tiếng Nhật");
            h.createCell(2).setCellValue("Romaji");
            h.createCell(3).setCellValue("Nghĩa tiếng Việt");

            Row r = sheet.createRow(1);
            r.createCell(0).setCellValue("Chủ đề công thức");
            Cell cB = r.createCell(1);
            cB.setCellFormula("SUM(1,2)"); // Chứa FORMULA
            r.createCell(2).setCellValue("formula");
            r.createCell(3).setCellValue("Nghĩa");

            wb.write(baos);
            formulaBytes = baos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile("file", "formula.xlsx", "application/octet-stream", formulaBytes);
        ImportPreviewReport preview = importService.preview(file);
        assertThat(preview.errors()).isNotEmpty();
        assertThat(preview.errors().get(0).message()).contains("Ô chứa công thức không được phép");

        // Khi commit, ném ImportRollbackException
        assertThatThrownBy(() -> importService.commit(file, ImportMode.SKIP_DUPLICATES, "tester"))
                .isInstanceOf(ImportRollbackException.class);
    }

    @Test
    @DisplayName("E2E-2: Ca xấu 6 - File chứa ô có số thẻ thanh toán hợp lệ Luhn bị từ chối")
    void testNegativeCase6_PaymentCardNumber() throws IOException {
        byte[] cardBytes;
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("New Word");
            Row h = sheet.createRow(0);
            h.createCell(0).setCellValue("Topic");
            h.createCell(1).setCellValue("Tiếng Nhật");
            h.createCell(2).setCellValue("Romaji");
            h.createCell(3).setCellValue("Nghĩa tiếng Việt");

            Row r = sheet.createRow(1);
            r.createCell(0).setCellValue("Chủ đề thẻ");
            r.createCell(1).setCellValue("カード");
            r.createCell(2).setCellValue("kaado");
            // Ô chứa số thẻ Visa test 16 số hợp lệ Luhn
            r.createCell(3).setCellValue("Thanh toán thẻ 4111-1111-1111-1111 tại quầy");

            wb.write(baos);
            cardBytes = baos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile("file", "card.xlsx", "application/octet-stream", cardBytes);
        ImportPreviewReport preview = importService.preview(file);
        assertThat(preview.errors()).isNotEmpty();
        assertThat(preview.errors().stream().anyMatch(e -> "Dữ liệu giống số thẻ thanh toán, không được nhập".equals(e.message()))).isTrue();

        // Không commit được
        assertThatThrownBy(() -> importService.commit(file, ImportMode.SKIP_DUPLICATES, "tester"))
                .isInstanceOf(ImportRollbackException.class);
    }

    // --- Helper Methods ---

    private byte[] createSimpleValidXlsx() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("New Word");
            Row h = sheet.createRow(0);
            h.createCell(0).setCellValue("Topic");
            h.createCell(1).setCellValue("Tiếng Nhật");
            h.createCell(2).setCellValue("Romaji");
            h.createCell(3).setCellValue("Nghĩa tiếng Việt");
            wb.write(baos);
            return baos.toByteArray();
        }
    }

    private byte[] addZipEntry(byte[] originalZip, String entryName, byte[] entryContent) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(originalZip));
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                zos.putNextEntry(new ZipEntry(entry.getName()));
                zis.transferTo(zos);
                zos.closeEntry();
            }
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(entryContent);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}
