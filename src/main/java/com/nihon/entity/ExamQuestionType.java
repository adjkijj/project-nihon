package com.nihon.entity;

import java.util.Arrays;
import java.util.Locale;

/**
 * Loại câu hỏi thi (F41).
 * Whitelist: MULTIPLE_CHOICE, TYPING, ORDERING, TRANSLATION, ESSAY
 */
public enum ExamQuestionType {
    MULTIPLE_CHOICE("Trắc nghiệm", "Chọn 1 đáp án đúng trong các phương án"),
    TYPING("Gõ câu trả lời", "Nhập trực tiếp câu trả lời bằng bàn phím"),
    ORDERING("Ghép câu / Vị trí ngôi sao", "Sắp xếp các mảnh từ thành câu hoàn chỉnh và xác định vị trí ngôi sao"),
    TRANSLATION("Dịch câu", "Dịch câu Nhật - Việt hoặc Việt - Nhật"),
    ESSAY("Viết đoạn văn / Tự luận", "Viết bài tự luận hoặc đoạn văn theo chủ đề");

    private final String displayName;
    private final String description;

    ExamQuestionType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public static boolean isValid(String typeStr) {
        if (typeStr == null || typeStr.isBlank()) {
            return false;
        }
        String normalized = typeStr.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).anyMatch(t -> t.name().equals(normalized));
    }

    public static ExamQuestionType fromString(String typeStr) {
        if (typeStr == null || typeStr.isBlank()) {
            return MULTIPLE_CHOICE;
        }
        String normalized = typeStr.trim().toUpperCase(Locale.ROOT);
        for (ExamQuestionType t : values()) {
            if (t.name().equals(normalized)) {
                return t;
            }
        }
        // Thử khớp theo tên tiếng Việt
        for (ExamQuestionType t : values()) {
            if (t.displayName.equalsIgnoreCase(typeStr.trim())) {
                return t;
            }
        }
        // Khớp viết tắt hoặc không dấu
        if ("CHOICE".equals(normalized) || "TRẮC NGHIỆM".equalsIgnoreCase(typeStr.trim()) || "TRAC NGHIEM".equalsIgnoreCase(typeStr.trim())) {
            return MULTIPLE_CHOICE;
        }
        if ("GÕ".equalsIgnoreCase(typeStr.trim()) || "GO".equalsIgnoreCase(typeStr.trim()) || "TYPE".equals(normalized)) {
            return TYPING;
        }
        if ("GHÉP CÂU".equalsIgnoreCase(typeStr.trim()) || "GHEP CAU".equalsIgnoreCase(typeStr.trim()) || "SẮP XẾP".equalsIgnoreCase(typeStr.trim())) {
            return ORDERING;
        }
        if ("DỊCH".equalsIgnoreCase(typeStr.trim()) || "DICH".equalsIgnoreCase(typeStr.trim())) {
            return TRANSLATION;
        }
        if ("TỰ LUẬN".equalsIgnoreCase(typeStr.trim()) || "TU LUAN".equalsIgnoreCase(typeStr.trim()) || "VIẾT".equalsIgnoreCase(typeStr.trim())) {
            return ESSAY;
        }
        throw new IllegalArgumentException("Loại câu hỏi không hợp lệ: " + typeStr +
                ". Danh sách hợp lệ: MULTIPLE_CHOICE, TYPING, ORDERING, TRANSLATION, ESSAY.");
    }
}

