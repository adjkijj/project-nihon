package com.nihon.entity;

import java.util.Arrays;
import java.util.Locale;

/**
 * Phân loại đáp án / khóa chấm điểm (F41).
 * Whitelist: ACCEPTED, SAMPLE, KEYWORD, ORDER
 */
public enum ExamAnswerKind {
    ACCEPTED("Đáp án được chấp nhận"),
    SAMPLE("Câu mẫu / Bài mẫu tham khảo"),
    KEYWORD("Từ khóa bắt buộc có trong câu trả lời"),
    ORDER("Thứ tự sắp xếp hoặc vị trí ngôi sao");

    private final String description;

    ExamAnswerKind(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static boolean isValid(String kindStr) {
        if (kindStr == null || kindStr.isBlank()) {
            return false;
        }
        String normalized = kindStr.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).anyMatch(k -> k.name().equals(normalized));
    }

    public static ExamAnswerKind fromString(String kindStr) {
        if (kindStr == null || kindStr.isBlank()) {
            return ACCEPTED;
        }
        String normalized = kindStr.trim().toUpperCase(Locale.ROOT);
        for (ExamAnswerKind k : values()) {
            if (k.name().equals(normalized)) {
                return k;
            }
        }
        throw new IllegalArgumentException("Loại đáp án không hợp lệ: " + kindStr +
                ". Danh sách hợp lệ: ACCEPTED, SAMPLE, KEYWORD, ORDER.");
    }
}

