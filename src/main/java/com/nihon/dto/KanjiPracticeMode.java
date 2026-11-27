package com.nihon.dto;

import java.util.Locale;

/**
 * Các dạng bài luyện tập Kanji (F22, F23).
 */
public enum KanjiPracticeMode {

    /**
     * Hiện Kanji, người học gõ âm đọc (On hoặc Kun) bằng Kana.
     */
    KANJI_TO_READING("KANJI_READING", "Đoán âm đọc"),

    /**
     * Hiện Hán Việt và Ý nghĩa, người học gõ ký tự Kanji.
     */
    MEANING_TO_KANJI("KANJI_WRITE", "Viết chữ Kanji"),

    /**
     * Hiện Kanji, chọn Hán Việt / Ý nghĩa đúng trong 4 lựa chọn trắc nghiệm.
     */
    KANJI_CHOICE("KANJI_CHOICE", "Trắc nghiệm 4 lựa chọn");

    private final String studyProgressMode;
    private final String displayName;

    KanjiPracticeMode(String studyProgressMode, String displayName) {
        this.studyProgressMode = studyProgressMode;
        this.displayName = displayName;
    }

    public String getStudyProgressMode() {
        return studyProgressMode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static KanjiPracticeMode fromString(String modeStr) {
        if (modeStr == null || modeStr.isBlank()) {
            return KANJI_TO_READING;
        }
        String upper = modeStr.strip().toUpperCase(Locale.ROOT);
        for (KanjiPracticeMode m : values()) {
            if (m.name().equals(upper) || m.studyProgressMode.equals(upper)) {
                return m;
            }
        }
        return KANJI_TO_READING;
    }
}

