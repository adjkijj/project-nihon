package com.nihon.service;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lớp tiện ích hỗ trợ xử lý và hiển thị phiên âm Furigana cho từ vựng tiếng Nhật.
 * Tách chuỗi cách đọc trong ngoặc nguồn để tránh lặp lại hai lần khi kết hợp thẻ ruby.
 */
@Component("furiganaHelper")
public class FuriganaHelper {

    /**
     * Mẫu nhận diện cặp ngoặc toàn giác （...） hoặc bán giác (...) chứa cách đọc.
     */
    private static final Pattern PARENTHESES_PATTERN = Pattern.compile("[（(]([^）)]*)[）)]");

    /**
     * Tách bỏ phần ngoặc chứa cách đọc, chỉ giữ lại phần chữ gốc (Kanji/Kana).
     *
     * <p>Ví dụ:
     * <ul>
     *   <li>"私（わたし）" -> "私"</li>
     *   <li>"1歳" -> "1歳"</li>
     *   <li>"日本酒（にほんしゅ）" -> "日本酒"</li>
     *   <li>"" -> ""</li>
     *   <li>null -> null</li>
     * </ul>
     *
     * @param japaneseText chuỗi tiếng Nhật nguồn
     * @return chuỗi đã loại bỏ phần ngoặc, hoặc null nếu đầu vào null
     */
    public static String extractBaseText(String japaneseText) {
        if (japaneseText == null) {
            return null;
        }
        String trimmed = japaneseText.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return PARENTHESES_PATTERN.matcher(trimmed).replaceAll("").trim();
    }

    /**
     * Bí danh cho {@link #extractBaseText(String)}.
     *
     * @param japaneseText chuỗi tiếng Nhật nguồn
     * @return chuỗi đã loại bỏ phần ngoặc
     */
    public static String stripParentheses(String japaneseText) {
        return extractBaseText(japaneseText);
    }

    /**
     * Trích xuất phần cách đọc nằm trong ngoặc nếu có.
     *
     * @param japaneseText chuỗi tiếng Nhật nguồn
     * @return nội dung trong ngoặc đã cắt tỉa, hoặc null nếu không có ngoặc
     */
    public static String extractParenthesesReading(String japaneseText) {
        if (japaneseText == null) {
            return null;
        }
        Matcher matcher = PARENTHESES_PATTERN.matcher(japaneseText);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    /**
     * Kiểm tra xem từ có cách đọc hợp lệ để hiển thị ruby hay không.
     *
     * @param reading chuỗi cách đọc
     * @return true nếu cách đọc không rỗng
     */
    public static boolean hasReading(String reading) {
        return reading != null && !reading.isBlank();
    }
}

