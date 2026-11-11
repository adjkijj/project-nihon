package com.nihon.dto;

import lombok.Getter;

/**
 * Whitelist bộ lọc nhanh ở trang Quản lý từ vựng:
 * ALL: Tất cả từ vựng (mặc định)
 * HAS_KANJI: Chỉ từ có Kanji
 * NO_READING: Chỉ từ chưa có cách đọc (reading rỗng/null)
 * NO_MEANING: Chỉ từ chưa có nghĩa (meaningVi rỗng/null)
 */
@Getter
public enum VocabQuickFilter {
    ALL("Tất cả"),
    HAS_KANJI("Chỉ từ có Kanji"),
    NO_READING("Chỉ từ chưa có cách đọc"),
    NO_MEANING("Chỉ từ chưa có nghĩa");

    private final String displayName;

    VocabQuickFilter(String displayName) {
        this.displayName = displayName;
    }

    public static VocabQuickFilter fromString(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        for (VocabQuickFilter f : values()) {
            if (f.name().equalsIgnoreCase(value.strip())) {
                return f;
            }
        }
        return ALL;
    }
}

