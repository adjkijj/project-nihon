package com.nihon.conjugation;

import java.util.Locale;

/**
 * Các thể đích trong luyện tập chia động từ tiếng Nhật.
 */
public enum TargetForm {
    TE("Thể Te (て形)", "て"),
    RU("Thể từ điển (辞書形)", "る"),
    TA("Thể Ta (た形 / Quá khứ)", "た"),
    NAI("Thể Nai (ない形 / Phủ định)", "ない"),
    MASU("Thể Masu (ます形 / Lịch sự)", "ます"),
    RANDOM("Ngẫu nhiên mọi thể", "?");

    private final String displayName;
    private final String shortLabel;

    TargetForm(String displayName, String shortLabel) {
        this.displayName = displayName;
        this.shortLabel = shortLabel;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getShortLabel() {
        return shortLabel;
    }

    public static TargetForm fromString(String value) {
        if (value == null || value.isBlank()) {
            return TE;
        }
        String clean = value.strip().toUpperCase(Locale.ROOT);
        for (TargetForm tf : values()) {
            if (tf.name().equals(clean)) {
                return tf;
            }
        }
        return TE;
    }

    public static boolean isSpecificForm(TargetForm form) {
        return form != null && form != RANDOM;
    }
}
