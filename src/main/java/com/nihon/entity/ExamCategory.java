package com.nihon.entity;

import java.util.Arrays;
import java.util.Locale;

/**
 * Phân loại đề thi (F41).
 * Whitelist: MIDTERM, MOCK, QUICK_TEST, REVIEW
 */
public enum ExamCategory {
    MIDTERM("Thi giữa kỳ", "Kiểm tra kiến thức trọng tâm giữa kỳ"),
    MOCK("Thi thử", "Đề thi thử toàn diện theo cấu trúc chuẩn"),
    QUICK_TEST("Kiểm tra nhanh", "Bài kiểm tra ngắn 15-30 phút"),
    REVIEW("Ôn tập", "Luyện tập và củng cố kiến thức theo chủ đề");

    private final String displayName;
    private final String description;

    ExamCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public static boolean isValid(String categoryStr) {
        if (categoryStr == null || categoryStr.isBlank()) {
            return false;
        }
        String normalized = categoryStr.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).anyMatch(c -> c.name().equals(normalized));
    }

    public static ExamCategory fromString(String categoryStr) {
        if (categoryStr == null || categoryStr.isBlank()) {
            return REVIEW;
        }
        String normalized = categoryStr.trim().toUpperCase(Locale.ROOT);
        for (ExamCategory cat : values()) {
            if (cat.name().equals(normalized)) {
                return cat;
            }
        }
        // Thử khớp theo tên tiếng Việt nếu người dùng nhập từ Excel
        for (ExamCategory cat : values()) {
            if (cat.displayName.equalsIgnoreCase(categoryStr.trim())) {
                return cat;
            }
        }
        throw new IllegalArgumentException("Loại đề thi không hợp lệ: " + categoryStr +
                ". Danh sách hợp lệ: MIDTERM, MOCK, QUICK_TEST, REVIEW.");
    }
}

