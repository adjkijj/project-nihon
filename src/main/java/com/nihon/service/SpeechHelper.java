package com.nihon.service;

import org.springframework.stereotype.Component;

/**
 * Lớp tiện ích hỗ trợ sinh văn bản phát âm tiếng Nhật cho Web Speech API.
 * Ưu tiên cách đọc Hiragana/Katakana nếu có để đảm bảo bộ máy đọc đúng âm;
 * nếu chưa có cách đọc thì dùng văn bản tiếng Nhật gốc đã loại bỏ phần ngoặc nguồn.
 */
@Component("speechHelper")
public class SpeechHelper {

    public static final double DEFAULT_SPEED = 0.9;
    public static final java.util.List<String> ALLOWED_SPEEDS = java.util.List.of("1.0", "1", "0.85", "0.75");

    /**
     * Xác định và kiểm tra tốc độ phát âm (TTS speed) hợp lệ.
     * Mặc định 0.9 để giữ hành vi cũ.
     * Giá trị lạ hoặc ngoài danh sách cho phép (1.0x, 0.85x, 0.75x) sẽ được đưa về mặc định 0.9.
     *
     * @param rawSpeed Chuỗi tốc độ từ client (ví dụ: "1.0", "0.85", "0.75")
     * @return Tốc độ hợp lệ dạng double (1.0, 0.85, 0.75 hoặc 0.9)
     */
    public static double resolveTtsSpeed(String rawSpeed) {
        if (rawSpeed == null || rawSpeed.isBlank()) {
            return DEFAULT_SPEED;
        }
        String trimmed = rawSpeed.trim();
        if (!ALLOWED_SPEEDS.contains(trimmed)) {
            return DEFAULT_SPEED;
        }
        try {
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException e) {
            return DEFAULT_SPEED;
        }
    }

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

