package com.nihon.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tiện ích trích xuất các ký tự chữ Hán (Kanji) từ chuỗi văn bản tiếng Nhật.
 * Duyệt theo Unicode code point (hỗ trợ cả các ký tự Kanji ngoài BMP / Supplementary Characters),
 * loại bỏ trùng lặp và bảo toàn thứ tự xuất hiện ban đầu.
 */
public final class KanjiExtractor {

    private KanjiExtractor() {
        // Utility class
    }

    /**
     * Trích xuất danh sách các ký tự Kanji duy nhất từ chuỗi tiếng Nhật.
     *
     * <p>Ví dụ:
     * <ul>
     *   <li>"日本酒（にほんしゅ）" -> ["日", "本", "酒"]</li>
     *   <li>"あいう" -> []</li>
     *   <li>"𠮟る" -> ["𠮟"]</li>
     *   <li>null hoặc chuỗi rỗng -> []</li>
     * </ul>
     *
     * @param text chuỗi văn bản tiếng Nhật nguồn
     * @return danh sách các ký tự Kanji theo thứ tự xuất hiện ban đầu, không trùng lặp
     */
    public static List<String> extractKanji(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        Set<String> kanjiSet = new LinkedHashSet<>();
        text.codePoints().forEach(cp -> {
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) {
                kanjiSet.add(new String(Character.toChars(cp)));
            }
        });

        return new ArrayList<>(kanjiSet);
    }

    /**
     * Escape các ký tự đặc biệt của mệnh đề SQL LIKE (%, _, \) để chống SQL Wildcard Injection.
     *
     * @param text chuỗi cần escape
     * @return chuỗi đã escape an toàn
     */
    public static String escapeLikePattern(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}

