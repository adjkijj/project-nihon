package com.nihon.service.excel;

import com.nihon.dto.excel.ParseResult;
import com.nihon.dto.excel.RowError;
import com.nihon.dto.excel.WordRow;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bộ kiểm thử tuân thủ PCI-DSS v4.0 cho tính năng import:
 * 1. Số thẻ thanh toán hợp lệ theo thuật toán Luhn (13-19 chữ số) bị chặn hoàn toàn.
 * 2. Số 16 chữ số không thỏa mãn thuật toán Luhn KHÔNG bị chặn nhầm.
 * 3. Số điện thoại (10-11 số) KHÔNG bị chặn nhầm.
 * 4. Thông báo lỗi trả về đúng: "Dữ liệu giống số thẻ thanh toán, không được nhập".
 * 5. Giá trị số thẻ không bị lộ trong thông báo lỗi hoặc log.
 */
class PaymentCardFilterTest {

    private WordSheetParser wordParser;

    // Số thẻ kiểm thử chuẩn (Test PANs) thỏa mãn thuật toán Luhn
    private static final String VALID_VISA_16 = "4111111111111111";
    private static final String VALID_VISA_HYPHEN = "4111-1111-1111-1111";
    private static final String VALID_VISA_SPACE = "4111 1111 1111 1111";
    private static final String VALID_MC_16 = "5500000000000004";
    private static final String VALID_AMEX_15 = "378282246310005";

    // Số 16 chữ số KHÔNG thỏa mãn Luhn
    private static final String INVALID_LUHN_16 = "4111-1111-1111-1112";
    private static final String INVALID_LUHN_DUMMY = "1234-5678-1234-5678";

    // Số điện thoại thông thường
    private static final String PHONE_VN_10 = "0901234567";
    private static final String PHONE_VN_HYPHEN = "098-765-4321";
    private static final String PHONE_VN_SPACE = "091 234 5678";
    private static final String PHONE_INTL = "+84-901-234-567";

    @BeforeEach
    void setUp() {
        wordParser = new WordSheetParser();
    }

    @Test
    @DisplayName("1. Thuật toán Luhn: Nhận diện chính xác số thẻ hợp lệ và không hợp lệ")
    void testLuhnAlgorithmDirectly() {
        // Hợp lệ Luhn
        assertThat(ExcelCellUtils.isValidLuhn("4111111111111111")).isTrue();
        assertThat(ExcelCellUtils.isValidLuhn("5500000000000004")).isTrue();
        assertThat(ExcelCellUtils.isValidLuhn("378282246310005")).isTrue();

        // Không hợp lệ Luhn
        assertThat(ExcelCellUtils.isValidLuhn("4111111111111112")).isFalse();
        assertThat(ExcelCellUtils.isValidLuhn("1234567812345678")).isFalse();

        // Độ dài không trong khoảng 13-19
        assertThat(ExcelCellUtils.isValidLuhn("123456789012")).isFalse(); // 12 số
        assertThat(ExcelCellUtils.isValidLuhn("12345678901234567890")).isFalse(); // 20 số
        assertThat(ExcelCellUtils.isValidLuhn(null)).isFalse();
        assertThat(ExcelCellUtils.isValidLuhn("")).isFalse();
    }

    @Test
    @DisplayName("2. Kiểm tra chuỗi chứa số thẻ: Phát hiện các định dạng thẻ Visa/Master/Amex có khoảng trắng hoặc gạch ngang")
    void testContainsPotentialPaymentCard_DetectsValidCards() {
        assertThat(ExcelCellUtils.containsPotentialPaymentCard(VALID_VISA_16)).isTrue();
        assertThat(ExcelCellUtils.containsPotentialPaymentCard(VALID_VISA_HYPHEN)).isTrue();
        assertThat(ExcelCellUtils.containsPotentialPaymentCard(VALID_VISA_SPACE)).isTrue();
        assertThat(ExcelCellUtils.containsPotentialPaymentCard(VALID_MC_16)).isTrue();
        assertThat(ExcelCellUtils.containsPotentialPaymentCard(VALID_AMEX_15)).isTrue();

        // Số thẻ nằm xen kẽ trong câu văn
        assertThat(ExcelCellUtils.containsPotentialPaymentCard("Ghi chú thanh toán bằng thẻ 4111-1111-1111-1111 tại quầy")).isTrue();
    }

    @Test
    @DisplayName("3. Số 16 chữ số không thỏa mãn Luhn: KHÔNG bị chặn nhầm")
    void testContainsPotentialPaymentCard_AllowsInvalidLuhnNumbers() {
        assertThat(ExcelCellUtils.containsPotentialPaymentCard(INVALID_LUHN_16)).isFalse();
        assertThat(ExcelCellUtils.containsPotentialPaymentCard(INVALID_LUHN_DUMMY)).isFalse();
        assertThat(ExcelCellUtils.containsPotentialPaymentCard("Mã số đơn hàng 1234-5678-1234-5678")).isFalse();
    }

    @Test
    @DisplayName("4. Số điện thoại: KHÔNG bị chặn nhầm")
    void testContainsPotentialPaymentCard_AllowsPhoneNumbers() {
        assertThat(ExcelCellUtils.containsPotentialPaymentCard(PHONE_VN_10)).isFalse();
        assertThat(ExcelCellUtils.containsPotentialPaymentCard(PHONE_VN_HYPHEN)).isFalse();
        assertThat(ExcelCellUtils.containsPotentialPaymentCard(PHONE_VN_SPACE)).isFalse();
        assertThat(ExcelCellUtils.containsPotentialPaymentCard(PHONE_INTL)).isFalse();
        assertThat(ExcelCellUtils.containsPotentialPaymentCard("Liên hệ: 0901234567")).isFalse();
        assertThat(ExcelCellUtils.containsPotentialPaymentCard("Số điện thoại bàn: 024-3829-1234")).isFalse();
    }

    @Test
    @DisplayName("5. Tích hợp WordSheetParser: Từ chối hàng chứa số thẻ và trả về thông báo chuẩn")
    void testWordSheetParser_RejectsRowWithPaymentCard() throws IOException {
        byte[] xlsx = createWorkbookWithRow(
                "Chủ đề PCI",
                "私（わたし）",
                "watashi",
                "Nghĩa tiếng Việt có số thẻ: " + VALID_VISA_HYPHEN
        );

        ParseResult<WordRow> result = wordParser.parse(new ByteArrayInputStream(xlsx));

        // Hàng chứa số thẻ bị loại bỏ khỏi danh sách hợp lệ
        assertThat(result.rows()).isEmpty();
        assertThat(result.errors()).isNotEmpty();

        RowError cardError = result.errors().stream()
                .filter(e -> "Dữ liệu giống số thẻ thanh toán, không được nhập".equals(e.message()))
                .findFirst()
                .orElse(null);

        assertThat(cardError).isNotNull();
        assertThat(cardError.rowNumber()).isEqualTo(2);
        assertThat(cardError.column()).isEqualTo("Nghĩa tiếng Việt");

        // Đảm bảo thông điệp lỗi KHÔNG chứa số thẻ thực tế
        assertThat(cardError.message()).doesNotContain("4111");
        assertThat(cardError.message()).isEqualTo("Dữ liệu giống số thẻ thanh toán, không được nhập");
    }

    @Test
    @DisplayName("6. Tích hợp WordSheetParser: Cho phép hàng chứa số điện thoại và số 16 chữ số không hợp lệ Luhn")
    void testWordSheetParser_AllowsPhoneAndInvalidLuhn() throws IOException {
        byte[] xlsx = createWorkbookWithTwoRows(
                // Hàng 1 chứa số điện thoại
                "Chủ đề 1", "電話（でんわ）", "denwa", "Điện thoại: 098-765-4321",
                // Hàng 2 chứa số 16 số không thỏa mãn Luhn
                "Chủ đề 1", "番号（ばんごう）", "bangou", "Mã ngẫu nhiên 1234-5678-1234-5678"
        );

        ParseResult<WordRow> result = wordParser.parse(new ByteArrayInputStream(xlsx));

        // Cả 2 hàng đều được chấp nhận hợp lệ
        assertThat(result.errors()).isEmpty();
        assertThat(result.rows()).hasSize(2);
    }

    // --- Helper Methods ---

    private byte[] createWorkbookWithRow(String topic, String jp, String ro, String me) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("New Word");
            Row h = sheet.createRow(0);
            h.createCell(0).setCellValue("Topic");
            h.createCell(1).setCellValue("Tiếng Nhật");
            h.createCell(2).setCellValue("Romaji");
            h.createCell(3).setCellValue("Nghĩa tiếng Việt");

            Row r = sheet.createRow(1);
            r.createCell(0).setCellValue(topic);
            r.createCell(1).setCellValue(jp);
            r.createCell(2).setCellValue(ro);
            r.createCell(3).setCellValue(me);

            wb.write(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createWorkbookWithTwoRows(String t1, String j1, String r1, String m1,
                                            String t2, String j2, String r2, String m2) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("New Word");
            Row h = sheet.createRow(0);
            h.createCell(0).setCellValue("Topic");
            h.createCell(1).setCellValue("Tiếng Nhật");
            h.createCell(2).setCellValue("Romaji");
            h.createCell(3).setCellValue("Nghĩa tiếng Việt");

            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue(t1);
            row1.createCell(1).setCellValue(j1);
            row1.createCell(2).setCellValue(r1);
            row1.createCell(3).setCellValue(m1);

            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue(t2);
            row2.createCell(1).setCellValue(j2);
            row2.createCell(2).setCellValue(r2);
            row2.createCell(3).setCellValue(m2);

            wb.write(baos);
            return baos.toByteArray();
        }
    }
}
