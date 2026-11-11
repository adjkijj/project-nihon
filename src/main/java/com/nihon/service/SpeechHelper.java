package com.nihon.service;

import org.springframework.stereotype.Component;

/**
 * Lớp tiện ích hỗ trợ sinh văn bản phát âm tiếng Nhật cho Web Speech API.
 * Ưu tiên cách đọc Hiragana/Katakana nếu có để đảm bảo bộ máy đọc đúng âm;
 * nếu chưa có cách đọc thì dùng văn bản tiếng Nhật gốc đã loại bỏ phần ngoặc nguồn.
 */
@Component("speechHelper")
public class SpeechHelper {

    /**
     * Xác định chuỗi văn bản dùng cho phát âm tiếng Nhật.
     *
     * @param japaneseText Văn bản tiếng Nhật nguồn (có thể chứa Kanji hoặc ngoặc đọc)
     * @param reading Cách đọc Hiragana/Katakana (nếu có)
     * @return Chuỗi văn bản tiếng Nhật sạch dùng cho TTS, hoặc chuỗi rỗng nếu không có dữ liệu
     */
    public static String resolveSpeechText(String japaneseText, String reading) {
        if (reading != null && !reading.isBlank()) {
            return reading.trim();
        }
        if (japaneseText == null) {
            return "";
        }
        String base = FuriganaHelper.extractBaseText(japaneseText);
        if (base == null || base.isBlank()) {
            return "";
        }
        return base.trim();
    }
}

