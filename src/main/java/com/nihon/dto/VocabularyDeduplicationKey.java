package com.nihon.dto;

import com.nihon.entity.Vocabulary;

import java.util.Locale;

/**
 * Khóa định danh từ trùng nhau dựa trên:
 * 1. Cách viết tiếng Nhật chuẩn hóa (loại bỏ khoảng trắng thừa, đồng nhất ngoặc tròn).
 * 2. Cách đọc Hiragana/Katakana chuẩn hóa (nếu có).
 * 3. Nghĩa tiếng Việt chuẩn hóa khoảng trắng và không phân biệt hoa thường.
 *
 * Hai từ khác nhau nhưng cùng nghĩa tiếng Việt sẽ có khóa khác nhau (do tiếng Nhật khác nhau).
 */
public record VocabularyDeduplicationKey(
        String normalizedJapanese,
        String normalizedReading,
        String normalizedMeaningVi
) {

    public static VocabularyDeduplicationKey of(Vocabulary v) {
        return new VocabularyDeduplicationKey(
                normalizeJapanese(v.getJapaneseText()),
                normalizeReading(v.getReading()),
                normalizeMeaningVi(v.getMeaningVi())
        );
    }

    public static String normalizeJapanese(String text) {
        if (text == null) return "";
        return text.trim()
                .replace('（', '(')
                .replace('）', ')')
                .replaceAll("\\s+", " ");
    }

    public static String normalizeReading(String reading) {
        if (reading == null || reading.isBlank()) return "";
        return reading.trim();
    }

    public static String normalizeMeaningVi(String meaningVi) {
        if (meaningVi == null) return "";
        return meaningVi.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}
