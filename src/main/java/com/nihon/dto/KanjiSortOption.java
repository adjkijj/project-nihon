package com.nihon.dto;

import lombok.Getter;

/**
 * Whitelist các tiêu chí sắp xếp cho danh sách Kanji:
 * SOURCE_LINE: theo thứ tự bài học nguồn (mặc định)
 * CHARACTER: theo ký tự Kanji (bảng mã)
 * HAN_VIET: theo âm Hán Việt (A-Z)
 */
@Getter
public enum KanjiSortOption {
    SOURCE_LINE("sourceLine"),
    CHARACTER("character"),
    HAN_VIET("hanViet");

    private final String propertyName;

    KanjiSortOption(String propertyName) {
        this.propertyName = propertyName;
    }

    public static KanjiSortOption fromString(String value) {
        if (value == null || value.isBlank()) {
            return SOURCE_LINE;
        }
        for (KanjiSortOption option : values()) {
            if (option.name().equalsIgnoreCase(value.strip())) {
                return option;
            }
        }
        return SOURCE_LINE;
    }
}

