package com.nihon.validation;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Tiện ích chuẩn hóa văn bản tiếng Nhật và tiếng Việt dùng chung cho toàn bộ hệ thống.
 * Hỗ trợ chuẩn hóa Unicode NFC, loại bỏ ký tự zero-width, cắt khoảng trắng đầu cuối (bao gồm U+3000),
 * thu gọn khoảng trắng liên tiếp và kiểm tra ký tự điều khiển.
 */
public final class TextNormalizer {

    /**
     * Mẫu nhận diện các ký tự độ rộng bằng 0 (zero-width):
     * U+200B (Zero Width Space), U+200C (Zero Width Non-Joiner), U+200D (Zero Width Joiner),
     * U+2060 (Word Joiner), U+FEFF (Zero Width No-Break Space / BOM).
     */
    private static final Pattern ZERO_WIDTH_PATTERN = Pattern.compile("[\\u200B\\u200C\\u200D\\u2060\\uFEFF]");

    /**
     * Mẫu nhận diện chuỗi khoảng trắng liên tiếp (bao gồm khoảng trắng chuẩn và khoảng trắng tiếng Nhật U+3000).
     */
    private static final Pattern CONSECUTIVE_WHITESPACE_PATTERN = Pattern.compile("[\\s\\u3000]+");

    private TextNormalizer() {
        // Ngăn khởi tạo lớp tiện ích tĩnh
    }

    /**
     * Chuẩn hóa văn bản:
     * - Trả về null nếu đầu vào là null.
     * - Loại bỏ các ký tự zero-width (U+200B, U+200C, U+200D, U+2060, U+FEFF).
     * - Chuẩn hóa Unicode về dạng dựng sẵn NFC.
     * - Cắt khoảng trắng đầu cuối (kể cả U+3000) và thay chuỗi khoảng trắng liên tiếp bằng một dấu cách.
     * - Trả về null nếu chuỗi sau chuẩn hóa là rỗng.
     *
     * @param input chuỗi cần chuẩn hóa
     * @return chuỗi đã chuẩn hóa hoặc null nếu rỗng
     */
    public static String normalize(String input) {
        if (input == null) {
            return null;
        }
        String withoutZeroWidth = ZERO_WIDTH_PATTERN.matcher(input).replaceAll("");
        String nfc = Normalizer.normalize(withoutZeroWidth, Normalizer.Form.NFC);
        String collapsed = CONSECUTIVE_WHITESPACE_PATTERN.matcher(nfc).replaceAll(" ").strip();
        if (collapsed.isEmpty()) {
            return null;
        }
        return collapsed;
    }

    /**
     * Kiểm tra chuỗi có chứa ký tự điều khiển (Character.isISOControl) hay không (bao gồm cả dấu xuống dòng).
     *
     * @param input chuỗi cần kiểm tra
     * @return true nếu có ít nhất một ký tự điều khiển
     */
    public static boolean containsControlChars(String input) {
        return containsControlChars(input, false);
    }

    /**
     * Kiểm tra chuỗi có chứa ký tự điều khiển (Character.isISOControl) hay không,
     * cho phép tùy chọn bỏ qua dấu xuống dòng (\r, \n) trong những trường được phép.
     *
     * @param input chuỗi cần kiểm tra
     * @param allowNewlines true nếu cho phép ký tự xuống dòng (\r, \n)
     * @return true nếu có ký tự điều khiển không được phép
     */
    public static boolean containsControlChars(String input, boolean allowNewlines) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        for (int i = 0; i < input.length(); ) {
            int cp = input.codePointAt(i);
            if (allowNewlines && (cp == '\n' || cp == '\r')) {
                i += Character.charCount(cp);
                continue;
            }
            if (Character.isISOControl(cp)) {
                return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }
}

