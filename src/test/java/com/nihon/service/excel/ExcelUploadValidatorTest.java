package com.nihon.service.excel;

import com.nihon.config.ExcelUploadProperties;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bộ kiểm thử toàn diện cho {@link ExcelUploadValidator}:
 * 1. File hợp lệ (.xlsx chuẩn, hàng < 5000, cột < 20).
 * 2. File hợp lệ thực tế từ Update.xlsx nếu có.
 * 3. File vượt quá kích thước cho phép (> 5 MB).
 * 4. Sai phần mở rộng (.xls, .xlsm, .xlsb, .xltm, .csv, .txt, không có đuôi...).
 * 5. Giả đuôi file bằng file text (sai magic bytes).
 * 6. File có magic bytes không hợp lệ (hỏng ZIP header).
 * 7. File có chứa Macro hoặc vbaProject.bin.
 * 8. File có chứa External Links.
 * 9. File có chứa OLE Embeddings.
 * 10. Chống Zip Bomb (vi phạm tỷ lệ giải nén).
 * 11. Chống XXE (chứa DOCTYPE và entity ngoài).
 * 12. Vượt quá số hàng tối đa (5000 hàng/sheet).
 * 13. Vượt quá số cột tối đa (20 cột/sheet).
 * 14. Kiểm thử chống Formula Injection (ExcelCellUtils.sanitizeForExport).
 * 15. Kiểm tra thông báo lỗi trả về người dùng không làm lộ chi tiết nội bộ, đường dẫn, thư viện.
 */
class ExcelUploadValidatorTest {

    private ExcelUploadProperties properties;
    private ExcelUploadValidator validator;

    @BeforeEach
    void setUp() {
        properties = new ExcelUploadProperties();
        properties.setMaxFileSizeBytes(5 * 1024 * 1024L); // 5 MB
        properties.setMaxRowsPerSheet(5000);
        properties.setMaxColumnsPerSheet(20);
        properties.setMinInflateRatio(0.01);
        properties.setMaxEntrySize(10 * 1024 * 1024L);
        properties.setMaxTextSize(10 * 1024 * 1024L);

        validator = new ExcelUploadValidator(properties);
    }

    @Test
    @DisplayName("1. Ca hợp lệ: File .xlsx chuẩn pass kiểm tra")
    void testValidExcelFile_PassesValidation() throws IOException {
        byte[] validXlsx = createValidWorkbookBytes(10, 4);
        MockMultipartFile file = new MockMultipartFile(
                "file", "valid_test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                validXlsx
        );

        // Không ném ngoại lệ
        validator.validate(file);
    }

    @Test
    @DisplayName("2. Ca hợp lệ: File Update.xlsx thực tế pass kiểm tra nếu tồn tại")
    void testActualUpdateXlsx_PassesValidation() throws IOException {
        File file = new File("Update.xlsx");
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                MockMultipartFile multipartFile = new MockMultipartFile(
                        "file", "Update.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        fis.readAllBytes()
                );
                validator.validate(multipartFile);
            }
        }
    }

    @Test
    @DisplayName("3. Ca từ chối: File vượt quá kích thước tối đa 5 MB")
    void testExceedMaxFileSize_ThrowsException() {
        // Tạo thuộc tính với kích thước nhỏ để test nhanh
        ExcelUploadProperties customProps = new ExcelUploadProperties();
        customProps.setMaxFileSizeBytes(1024L); // 1 KB
        ExcelUploadValidator customValidator = new ExcelUploadValidator(customProps);

        byte[] largeBytes = new byte[2048]; // 2 KB
        MockMultipartFile file = new MockMultipartFile("file", "large.xlsx", "application/octet-stream", largeBytes);

        assertThatThrownBy(() -> customValidator.validate(file))
                .isInstanceOf(UploadRejectedException.class)
                .hasMessageContaining("vượt quá giới hạn");
    }

    @Test
    @DisplayName("4. Ca từ chối: Sai phần mở rộng (.xls, .xlsm, .xlsb, .xltm, .csv, .txt, không đuôi)")
    void testInvalidFileExtension_ThrowsException() throws IOException {
        byte[] validXlsx = createValidWorkbookBytes(2, 2);

        String[] invalidExtensions = {
                "test.xls", "test.xlsm", "test.xlsb", "test.xltm",
                "test.csv", "test.txt", "test.json", "no_extension",
                "malicious.xlsx.exe", "../../path/traversal.sh"
        };

        for (String filename : invalidExtensions) {
            MockMultipartFile file = new MockMultipartFile("file", filename, "application/octet-stream", validXlsx);
            assertThatThrownBy(() -> validator.validate(file))
                    .as("Testing invalid extension for: " + filename)
                    .isInstanceOf(UploadRejectedException.class)
                    .hasMessageContaining("Định dạng tệp tin không hợp lệ");
        }
    }

    @Test
    @DisplayName("5. Ca từ chối: Giả đuôi .xlsx bằng file văn bản thuần túy (sai magic bytes)")
    void testFakeExtensionWithPlainText_ThrowsException() {
        byte[] plainTextBytes = "This is a plain text file pretending to be excel".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "fake.xlsx", "text/plain", plainTextBytes);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(UploadRejectedException.class)
                .hasMessageContaining("không đúng định dạng bảng tính");
    }

    @Test
    @DisplayName("6. Ca từ chối: Magic bytes bị sai lệch không phải ZIP [50 4B 03 04]")
    void testInvalidMagicBytes_ThrowsException() {
        byte[] corruptMagic = new byte[]{0x50, 0x4B, 0x00, 0x00, 0x01, 0x02};
        MockMultipartFile file = new MockMultipartFile("file", "corrupt.xlsx", "application/octet-stream", corruptMagic);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(UploadRejectedException.class)
                .hasMessageContaining("không đúng định dạng bảng tính");
    }

    @Test
    @DisplayName("7. Ca từ chối: Tệp tin có chứa Macro hoặc vbaProject.bin")
    void testFileWithMacro_ThrowsException() throws IOException {
        byte[] validXlsx = createValidWorkbookBytes(2, 2);
        // Thêm entry /xl/vbaProject.bin vào tệp zip
        byte[] macroFileBytes = addZipEntry(validXlsx, "xl/vbaProject.bin", "FAKE_VBA_CODE".getBytes(StandardCharsets.UTF_8));

        MockMultipartFile file = new MockMultipartFile("file", "macro.xlsx", "application/octet-stream", macroFileBytes);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(UploadRejectedException.class)
                .hasMessageContaining("macro");
    }

    @Test
    @DisplayName("8. Ca từ chối: Tệp tin có chứa liên kết ngoài (External Links)")
    void testFileWithExternalLinks_ThrowsException() throws IOException {
        byte[] validXlsx = createValidWorkbookBytes(2, 2);
        byte[] fileWithExternalLink = addZipEntry(validXlsx, "xl/externalLinks/externalLink1.xml",
                "<externalLink xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"/>".getBytes(StandardCharsets.UTF_8));

        MockMultipartFile file = new MockMultipartFile("file", "external_links.xlsx", "application/octet-stream", fileWithExternalLink);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(UploadRejectedException.class)
                .hasMessageContaining("liên kết ngoài");
    }

    @Test
    @DisplayName("9. Ca từ chối: Tệp tin có chứa đối tượng OLE nhúng")
    void testFileWithOleEmbeddings_ThrowsException() throws IOException {
        byte[] validXlsx = createValidWorkbookBytes(2, 2);
        byte[] fileWithOle = addZipEntry(validXlsx, "xl/embeddings/oleObject1.bin",
                new byte[]{0x01, 0x02, 0x03, 0x04});

        MockMultipartFile file = new MockMultipartFile("file", "embedded_ole.xlsx", "application/octet-stream", fileWithOle);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(UploadRejectedException.class)
                .hasMessageContaining("đối tượng nhúng OLE");
    }

    @Test
    @DisplayName("10. Ca từ chối: Tấn công Zip Bomb (vượt tỷ lệ giải nén hoặc kích thước entry)")
    void testZipBombProtection_ThrowsException() throws IOException {
        // Cấu hình tỷ lệ nén chặt chẽ hoặc giới hạn maxEntrySize nhỏ để kích hoạt bảo vệ Zip Bomb
        ExcelUploadProperties zipBombProps = new ExcelUploadProperties();
        zipBombProps.setMaxFileSizeBytes(5 * 1024 * 1024L);
        zipBombProps.setMaxEntrySize(1024L); // Giới hạn mỗi entry tối đa 1 KB
        ExcelUploadValidator zipBombValidator = new ExcelUploadValidator(zipBombProps);

        byte[] validXlsx = createValidWorkbookBytes(2, 2);
        MockMultipartFile file = new MockMultipartFile("file", "zipbomb.xlsx", "application/octet-stream", validXlsx);

        // Workbook thông thường có entry vượt quá 1 KB sẽ bị POI ZipSecureFile chặn
        assertThatThrownBy(() -> zipBombValidator.validate(file))
                .isInstanceOf(UploadRejectedException.class)
                .hasMessageContaining("không đáp ứng tiêu chuẩn an toàn");
    }

    @Test
    @DisplayName("11. Ca từ chối: Chặn tấn công XML External Entity (XXE)")
    void testXmlParserBlocksXxe_ThrowsException() throws IOException {
        // Tạo zip chứa file XML có DOCTYPE và External Entity
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Thêm [Content_Types].xml chứa payload XXE
            zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
            String xxePayload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<!DOCTYPE root [\n" +
                    "  <!ENTITY % xxe SYSTEM \"http://127.0.0.1:9999/malicious.dtd\">\n" +
                    "  %xxe;\n" +
                    "]>\n" +
                    "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n" +
                    "  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n" +
                    "</Types>";
            zos.write(xxePayload.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        MockMultipartFile file = new MockMultipartFile("file", "xxe_exploit.xlsx", "application/octet-stream", baos.toByteArray());

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(UploadRejectedException.class)
                .hasMessageContaining("không đáp ứng tiêu chuẩn an toàn");
    }

    @Test
    @DisplayName("12. Ca từ chối: Sheet vượt quá giới hạn 5000 hàng")
    void testExceedMaxRows_ThrowsException() throws IOException {
        // Tạo workbook có 1 hàng ở index 5000 (tức hàng thứ 5001)
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("LargeSheet");
            Row row = sheet.createRow(5000); // 0-indexed, hàng thứ 5001
            row.createCell(0).setCellValue("Overflow Row");
            wb.write(baos);

            MockMultipartFile file = new MockMultipartFile("file", "overflow_rows.xlsx", "application/octet-stream", baos.toByteArray());

            assertThatThrownBy(() -> validator.validate(file))
                    .isInstanceOf(UploadRejectedException.class)
                    .hasMessageContaining("vượt quá số lượng hàng cho phép");
        }
    }

    @Test
    @DisplayName("13. Ca từ chối: Sheet vượt quá giới hạn 20 cột")
    void testExceedMaxColumns_ThrowsException() throws IOException {
        // Tạo workbook có 21 cột (index 0 đến 20)
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("WideSheet");
            Row row = sheet.createRow(0);
            for (int col = 0; col <= 20; col++) { // 21 cột
                row.createCell(col).setCellValue("Col " + col);
            }
            wb.write(baos);

            MockMultipartFile file = new MockMultipartFile("file", "overflow_cols.xlsx", "application/octet-stream", baos.toByteArray());

            assertThatThrownBy(() -> validator.validate(file))
                    .isInstanceOf(UploadRejectedException.class)
                    .hasMessageContaining("vượt quá số lượng cột cho phép");
        }
    }

    @Test
    @DisplayName("14. Chống Formula Injection: Kiểm tra tiền tố dấu nháy đơn khi xuất ra CSV/Excel")
    void testFormulaInjectionSanitization() {
        // Các ký tự nguy hiểm: =, +, -, @, tab (\t), newline (\n), carriage return (\r)
        assertThat(ExcelCellUtils.sanitizeForExport("=SUM(A1:A10)")).isEqualTo("'=SUM(A1:A10)");
        assertThat(ExcelCellUtils.sanitizeForExport("=cmd|' /C calc'!A0")).isEqualTo("'=cmd|' /C calc'!A0");
        assertThat(ExcelCellUtils.sanitizeForExport("+12345")).isEqualTo("'+12345");
        assertThat(ExcelCellUtils.sanitizeForExport("-500")).isEqualTo("'-500");
        assertThat(ExcelCellUtils.sanitizeForExport("@SUM(1,2)")).isEqualTo("'@SUM(1,2)");
        assertThat(ExcelCellUtils.sanitizeForExport("\tTabPrefixed")).isEqualTo("'\tTabPrefixed");
        assertThat(ExcelCellUtils.sanitizeForExport("\nNewlinePrefixed")).isEqualTo("'\nNewlinePrefixed");
        assertThat(ExcelCellUtils.sanitizeForExport("\rCrPrefixed")).isEqualTo("'\rCrPrefixed");

        // Dữ liệu an toàn thông thường không bị thay đổi
        assertThat(ExcelCellUtils.sanitizeForExport("日本語")).isEqualTo("日本語");
        assertThat(ExcelCellUtils.sanitizeForExport("Tiếng Việt có dấu")).isEqualTo("Tiếng Việt có dấu");
        assertThat(ExcelCellUtils.sanitizeForExport("Normal text")).isEqualTo("Normal text");
        assertThat(ExcelCellUtils.sanitizeForExport(null)).isNull();
        assertThat(ExcelCellUtils.sanitizeForExport("")).isEqualTo("");
    }

    @Test
    @DisplayName("15. An toàn thông báo lỗi: Không để lộ đường dẫn nội bộ, thư viện hay stack trace")
    void testGenericErrorMessageDoesNotLeakInternalDetails() {
        byte[] invalidData = "Not a valid xlsx".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "corrupt.xlsx", "application/octet-stream", invalidData);

        try {
            validator.validate(file);
        } catch (UploadRejectedException ex) {
            String message = ex.getMessage();
            assertThat(message).doesNotContain("org.apache.poi");
            assertThat(message).doesNotContain("ZipSecureFile");
            assertThat(message).doesNotContain("XMLHelper");
            assertThat(message).doesNotContain("C:\\");
            assertThat(message).doesNotContain("/home/");
            assertThat(message).doesNotContain("/tmp/");
            assertThat(message).doesNotContain("Exception");
        }
    }

    @Test
    @DisplayName("16. Chống Log Injection (CWE-117): Loại bỏ triệt để ký tự xuống dòng (\\r, \\n)")
    void testLogInjectionSanitization() {
        assertThat(ExcelCellUtils.sanitizeForLog("file.xlsx\r\nMALICIOUS_LOG_ENTRY"))
                .isEqualTo("file.xlsx__MALICIOUS_LOG_ENTRY");
        assertThat(ExcelCellUtils.sanitizeForLog("Admin\nUser"))
                .isEqualTo("Admin_User");
        assertThat(ExcelCellUtils.sanitizeForLog("SafeString123"))
                .isEqualTo("SafeString123");
        assertThat(ExcelCellUtils.sanitizeForLog(null))
                .isEqualTo("");
    }

    // --- Helper Methods ---

    /**
     * Tạo byte[] của một workbook .xlsx chuẩn hợp lệ.
     */
    private byte[] createValidWorkbookBytes(int rows, int cols) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("New Word");
            for (int r = 0; r < rows; r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < cols; c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellValue("Data_" + r + "_" + c);
                }
            }
            wb.write(baos);
            return baos.toByteArray();
        }
    }

    /**
     * Thêm một entry tùy ý vào file zip có sẵn.
     */
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

            // Thêm entry mới
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(entryContent);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}
