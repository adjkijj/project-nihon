package com.nihon.dto;

import java.util.Locale;

/**
 * Định dạng tệp văn bản xuất từ vựng hỗ trợ Quizlet và Anki.
 */
public enum ExportTextFormat {
    TAB,
    COLON;

    /**
     * Chuyển đổi an toàn từ chuỗi sang enum.
     * Mặc định là TAB nếu giá trị null hoặc không hợp lệ (whitelist validation).
     *
     * @param value Giá trị chuỗi đầu vào từ request
     * @return Định dạng ExportTextFormat tương ứng (mặc định TAB)
     */
    public static ExportTextFormat fromString(String value) {
        if (value == null || value.isBlank()) {
            return TAB;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (ExportTextFormat format : values()) {
            if (format.name().equals(normalized)) {
                return format;
            }
        }
        return TAB;
    }
}

