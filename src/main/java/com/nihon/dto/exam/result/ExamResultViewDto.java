package com.nihon.dto.exam.result;

import com.nihon.entity.ExamAttempt;
import com.nihon.entity.ExamPaper;
import com.nihon.entity.ExamQuestion;
import com.nihon.service.exam.grading.GradingResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO tổng hợp toàn bộ kết quả bài thi chi tiết (F40 - F45).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamResultViewDto {

    private ExamAttempt attempt;
    private ExamPaper paper;

    private BigDecimal totalPoints;
    private BigDecimal maxPoints;
    private double percentage;
    private int durationSeconds;

    private int totalQuestions;
    private int correctCount;
    private int incorrectCount;
    private int essayCount;

    @Builder.Default
    private List<ExamSectionResultDto> sectionResults = new ArrayList<>();

    @Builder.Default
    private List<ExamQuestionResultDto> questionResults = new ArrayList<>();
}

