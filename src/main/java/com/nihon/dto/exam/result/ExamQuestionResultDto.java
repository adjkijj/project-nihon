package com.nihon.dto.exam.result;

import com.nihon.entity.ExamQuestion;
import com.nihon.service.exam.grading.GradingResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO chi tiết kết quả từng câu hỏi trong đề thi (F40 - F45).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamQuestionResultDto {

    private Long questionId;
    private int questionIndex;
    private Long sectionId;
    private String sectionTitle;

    private ExamQuestion question;

    private String studentAnswer;
    private String studentAnswerDisplay; // Chuyển đổi ID phương án sang chữ nếu là trắc nghiệm

    private BigDecimal pointsAwarded;
    private BigDecimal maxPoints;

    private Boolean correct; // true, false, null (tự luận)
    private boolean flagged;

    private GradingResult gradingResult;
}

