package com.nihon.dto.exam;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO đại diện cho một hàng dữ liệu câu hỏi đọc từ sheet "Exam" của tệp Excel (F41).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamExcelRow {

    private int rowNum;

    // Cột A: Code đề
    private String paperCode;

    // Cột B: Tên đề
    private String paperTitle;

    // Cột C: Thời lượng (phút)
    private Integer durationMinutes;

    // Cột D: Loại đề (MIDTERM, MOCK, QUICK_TEST, REVIEW)
    private String category;

    // Cột E: Phần thi
    private String sectionTitle;

    // Cột F: Loại câu hỏi (MULTIPLE_CHOICE, TYPING, ORDERING, TRANSLATION, ESSAY)
    private String questionType;

    // Cột G: Đề bài tiếng Nhật
    private String promptJa;

    // Cột H: Đề bài tiếng Việt
    private String promptVi;

    // Cột I: Điểm
    private BigDecimal points;

    // Cột J - M: Đáp án 1 - 4
    private String choice1;
    private String choice2;
    private String choice3;
    private String choice4;

    // Cột N: Đáp án đúng
    private String correctAnswer;

    // Cột O: Giải thích
    private String explanation;

    @Builder.Default
    private List<String> errors = new ArrayList<>();

    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    public void addError(String error) {
        if (errors == null) {
            errors = new ArrayList<>();
        }
        errors.add(error);
    }
}

