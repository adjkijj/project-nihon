package com.nihon.service.exam.grading;

import com.nihon.entity.ExamChoice;
import com.nihon.entity.ExamQuestion;
import com.nihon.entity.ExamQuestionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bộ chấm câu hỏi trắc nghiệm (MULTIPLE_CHOICE).
 * Đúng thì được tối đa điểm câu hỏi, sai được 0 điểm.
 */
@Component
public class MultipleChoiceGrader implements QuestionGrader {

    @Override
    public ExamQuestionType supportsType() {
        return ExamQuestionType.MULTIPLE_CHOICE;
    }

    @Override
    public GradingResult grade(ExamQuestion question, String studentAnswer) {
        BigDecimal maxPoints = question.getPoints() != null ? question.getPoints() : BigDecimal.ONE;
        List<ExamChoice> choices = question.getChoices() != null ? question.getChoices() : List.of();

        ExamChoice correctChoice = choices.stream()
                .filter(ExamChoice::isCorrect)
                .findFirst()
                .orElse(null);

        String sampleAnswer = correctChoice != null ? correctChoice.getText() : "";

        if (studentAnswer == null || studentAnswer.isBlank() || correctChoice == null) {
            return GradingResult.builder()
                    .pointsAwarded(BigDecimal.ZERO)
                    .correct(false)
                    .feedback("Chưa trả lời hoặc câu trả lời không chính xác.")
                    .sampleAnswer(sampleAnswer)
                    .build();
        }

        String trimmed = studentAnswer.trim();
        boolean isCorrect = false;

        // So khớp theo ID của phương án
        if (correctChoice.getId() != null && trimmed.equals(correctChoice.getId().toString())) {
            isCorrect = true;
        } else {
            // So khớp theo thứ tự sortOrder (ví dụ "1", "2")
            try {
                int order = Integer.parseInt(trimmed);
                if (order == correctChoice.getSortOrder()) {
                    isCorrect = true;
                }
            } catch (NumberFormatException ignored) {
                // Hoặc so khớp trực tiếp nội dung phương án
                if (trimmed.equalsIgnoreCase(correctChoice.getText().trim())) {
                    isCorrect = true;
                }
            }
        }

        if (isCorrect) {
            return GradingResult.builder()
                    .pointsAwarded(maxPoints)
                    .correct(true)
                    .feedback("Chính xác.")
                    .sampleAnswer(sampleAnswer)
                    .build();
        } else {
            return GradingResult.builder()
                    .pointsAwarded(BigDecimal.ZERO)
                    .correct(false)
                    .feedback("Chưa chính xác.")
                    .sampleAnswer(sampleAnswer)
                    .build();
        }
    }
}

