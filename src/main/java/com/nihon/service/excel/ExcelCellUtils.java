package com.nihon.service.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Lớp tiện ích hỗ trợ đọc, chuẩn hóa văn bản ô Excel (Unicode NFC, khoảng trắng)
 * và kiểm tra an toàn (chống công thức độc hại, chặn số thẻ thanh toán PCI-DSS).
 */
public final class ExcelCellUtils {

    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern MULTI_NEWLINE_PATTERN = Pattern.compile("\n{2,}");
    private static final Pattern CARD_CANDIDATE_PATTERN = Pattern.compile("(?<!\\d)(\\d[\\d\\s\\-]{11,28}\\d)(?!\\d)");

    private ExcelCellUtils() {
        // Private constructor ngăn việc khởi tạo tiện ích tĩnh
    }

    /**
     * Kiểm tra xem ô có chứa công thức hay không.
     *
     * @param cell ô cần kiểm tra
     * @return true nếu ô có kiểu FORMULA
     */
    public static boolean isFormula(Cell cell) {
        return cell != null && cell.getCellType() == CellType.FORMULA;
    }

    /**
     * Chuẩn hóa giá trị ô thông thường: đọc qua DataFormatter, trim, NFC, nén nhiều khoảng trắng thành 1.
     *
     * @param formatter đối tượng DataFormatter
     * @param cell ô cần đọc
     * @return chuỗi đã chuẩn hóa, hoặc rỗng nếu ô null hoặc rỗng
     */
    /**
     * Chuẩn hóa giá trị ô thông thường: đọc qua DataFormatter, trim, NFC, nén nhiều khoảng trắng thành 1.
     *
     * @param formatter đối tượng DataFormatter
     * @param cell ô cần đọc
     * @return chuỗi đã chuẩn hóa, hoặc rỗng nếu ô null hoặc rỗng
     */
    public static String normalizeCell(DataFormatter formatter, Cell cell) {
        if (cell == null) {
            return "";
        }
        String text = formatter.formatCellValue(cell);
        return normalizeText(text);
    }

    /**
     * Chuẩn hóa giá trị ô: nếu ô trống, null hoặc chỉ chứa khoảng trắng thì trả về null (không lưu chuỗi rỗng).
     *
     * @param formatter đối tượng DataFormatter
     * @param cell ô cần đọc
     * @return chuỗi đã chuẩn hóa, hoặc null nếu ô trống/khoảng trắng
     */
    public static String normalizeCellOrNull(DataFormatter formatter, Cell cell) {
        if (cell == null) {
            return null;
        }
        String text = formatter.formatCellValue(cell);
        return normalizeTextOrNull(text);
    }

    /**
     * Chuẩn hóa chuỗi văn bản thông thường: trim, chuẩn hóa Unicode NFC và thay thế chuỗi khoảng trắng bằng 1 khoảng trắng.
     *
     * @param raw chuỗi nguồn
     * @return chuỗi sau khi chuẩn hóa, hoặc chuỗi rỗng nếu raw là null hoặc rỗng
     */
    public static String normalizeText(String raw) {
        String result = normalizeTextOrNull(raw);
        return result != null ? result : "";
    }

    /**
     * Chuẩn hóa chuỗi văn bản: trim, Unicode NFC, nén khoảng trắng. Trả về null nếu null hoặc chỉ chứa khoảng trắng.
     *
     * @param raw chuỗi nguồn
     * @return chuỗi đã chuẩn hóa, hoặc null nếu chuỗi rỗng/chỉ chứa khoảng trắng
     */
    public static String normalizeTextOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String nfc = Normalizer.normalize(trimmed, Normalizer.Form.NFC);
        String singleSpaced = MULTI_SPACE_PATTERN.matcher(nfc).replaceAll(" ").trim();
        return singleSpaced.isEmpty() ? null : singleSpaced;
    }

    /**
     * Chuẩn hóa tên chủ đề: Giữ nguyên ký tự xuống dòng nhưng chuẩn hóa thành một dấu xuống dòng duy nhất (\n).
     * Trả về null nếu ô trống hoặc chỉ chứa khoảng trắng.
     *
     * @param formatter đối tượng DataFormatter
     * @param cell ô chứa chủ đề
     * @return chuỗi chủ đề đã chuẩn hóa ký tự xuống dòng và khoảng trắng, hoặc null nếu ô trống
     */
    public static String normalizeTopic(DataFormatter formatter, Cell cell) {
        if (cell == null) {
            return null;
        }
        String raw = formatter.formatCellValue(cell);
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        // Chuẩn hóa mọi ký tự xuống dòng CRLF và CR thành LF
        String unifiedNewlines = trimmed.replace("\r\n", "\n").replace("\r", "\n");
        // Nén nhiều dấu xuống dòng liên tiếp thành một dấu \n duy nhất
        String singleNewlines = MULTI_NEWLINE_PATTERN.matcher(unifiedNewlines).replaceAll("\n");

        // Chuẩn hóa từng dòng: trim và nén khoảng trắng
        String normalized = Arrays.stream(singleNewlines.split("\n", -1))
                .map(String::trim)
                .map(line -> MULTI_SPACE_PATTERN.matcher(line).replaceAll(" ").trim())
                .collect(Collectors.joining("\n"));

        String res = Normalizer.normalize(normalized.trim(), Normalizer.Form.NFC);
        return res.isEmpty() ? null : res;
    }

    /**
     * Tìm sheet trong workbook theo tên không phân biệt hoa thường.
     *
     * @param workbook đối tượng Workbook
     * @param targetSheetName tên sheet cần tìm
     * @return {@link Sheet} tương ứng hoặc null nếu không tồn tại
     */
    public static Sheet findSheetCaseInsensitive(Workbook workbook, String targetSheetName) {
        if (workbook == null || targetSheetName == null) {
            return null;
        }
        int count = workbook.getNumberOfSheets();
        for (int i = 0; i < count; i++) {
            String name = workbook.getSheetName(i);
            if (name != null && targetSheetName.equalsIgnoreCase(name.trim())) {
                return workbook.getSheetAt(i);
            }
        }
        return null;
    }

    /**
     * Chống Formula Injection (CSV/Excel injection):
     * Mọi chuỗi bắt đầu bằng '=', '+', '-', '@', tab ('\t') hoặc xuống dòng ('\r', '\n'),
     * khi xuất ra file Excel hoặc CSV, phải thêm tiền tố dấu nháy đơn (').
     *
     * @param value chuỗi cần xuất
     * @return chuỗi an toàn đã được thêm dấu nháy đơn nếu bắt đầu bằng ký tự nguy hiểm, hoặc null/nguyên văn nếu an toàn
     */
    public static String sanitizeForExport(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r' || first == '\n') {
            return "'" + value;
        }
        return value;
    }

    /**
     * Chống Log Injection (CWE-117 / OWASP A09):
     * Loại bỏ các ký tự xuống dòng (\r, \n) khỏi giá trị ghi log và thay thế bằng dấu gạch dưới (_).
     *
     * @param value giá trị đầu vào cần ghi log
     * @return chuỗi an toàn không chứa ký tự xuống dòng, hoặc chuỗi rỗng nếu value null
     */
    public static String sanitizeForLog(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\r', '_').replace('\n', '_');
    }

    /**
     * Kiểm tra thuật toán Luhn (Mod 10) để xác thực số thẻ thanh toán (Primary Account Number - PAN).
     *
     * @param digits chuỗi chỉ chứa các chữ số (0-9)
     * @return true nếu chuỗi số thỏa mãn thuật toán Luhn
     */
    public static boolean isValidLuhn(String digits) {
        if (digits == null || digits.length() < 13 || digits.length() > 19) {
            return false;
        }
        int sum = 0;
        boolean alternate = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
            int n = c - '0';
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }

    /**
     * Kiểm tra xem chuỗi có chứa số thẻ thanh toán tiềm năng (PAN từ 13 đến 19 chữ số,
     * cho phép dấu cách và dấu gạch ngang) thỏa mãn thuật toán Luhn hay không (Tuân thủ PCI-DSS).
     *
     * @param text chuỗi cần kiểm tra
     * @return true nếu phát hiện chuỗi số thỏa mãn kiểm tra Luhn
     */
    public static boolean containsPotentialPaymentCard(String text) {
        if (text == null || text.length() < 13) {
            return false;
        }
        Matcher matcher = CARD_CANDIDATE_PATTERN.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            String digits = candidate.replaceAll("[\\s\\-]", "");
            if (digits.length() >= 13 && digits.length() <= 19 && isValidLuhn(digits)) {
                return true;
            }
        }
        return false;
    }
}
