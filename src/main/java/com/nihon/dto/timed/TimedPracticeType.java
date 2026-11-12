package com.nihon.dto.timed;

/**
 * Các hình thức luyện tập tính giờ (Timed Practice).
 */
public enum TimedPracticeType {
    JA_VI("Nhật → Việt"),
    VI_JA("Việt → Nhật"),
    KANJI_READING("Đọc Kanji"),
    CHOICE("Trắc nghiệm 4 lựa chọn");

    private final String displayName;

    TimedPracticeType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static TimedPracticeType from(String value) {
        if (value == null || value.isBlank()) {
            return JA_VI;
        }
        for (TimedPracticeType t : values()) {
            if (t.name().equalsIgnoreCase(value.trim())) {
                return t;
            }
        }
        throw new IllegalArgumentException("Hình thức luyện tập tính giờ không hợp lệ: " + value);
    }
}
