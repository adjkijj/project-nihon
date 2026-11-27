package com.nihon.dto;

/**
 * Whitelist chế độ xem danh sách từ vựng:
 * COMPACT: Bảng thu gọn (mặc định)
 * FULL: Bảng đầy đủ các cột (Tiếng Nhật, Cách đọc, Romaji, Hán Việt, Nghĩa, Chủ đề, Thao tác)
 */
public enum VocabViewMode {
    COMPACT,
    FULL;

    public static VocabViewMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return COMPACT;
        }
        for (VocabViewMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value.strip())) {
                return mode;
            }
        }
        return COMPACT;
    }
}

