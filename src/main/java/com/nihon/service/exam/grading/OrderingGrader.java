package com.nihon.service.exam.grading;

import com.nihon.entity.ExamAnswerKind;
import com.nihon.entity.ExamAnswerKey;
import com.nihon.entity.ExamChoice;
import com.nihon.entity.ExamQuestion;
import com.nihon.entity.ExamQuestionType;
import com.nihon.service.exam.ExamValidator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bộ chấm câu hỏi ghép câu ngôi sao JLPT (ORDERING - F43).
 * Mặc định chấm theo mảnh ở vị trí ngôi sao ★ nếu câu có cấu hình STAR:x,
 * ngược lại chấm theo sự khớp toàn bộ thứ tự các mảnh từ.
 */
@Component
public class OrderingGrader implements QuestionGrader {

    @Override
    public ExamQuestionType supportsType() {
        return ExamQuestionType.ORDERING;
    }

    @Override
    public GradingResult grade(ExamQuestion question, String studentAnswer) {
        BigDecimal maxPoints = question.getPoints() != null ? question.getPoints() : BigDecimal.ONE;
        List<ExamChoice> choices = question.getChoices() != null ? question.getChoices() : List.of();
        List<ExamAnswerKey> keys = question.getAnswerKeys() != null ? question.getAnswerKeys() : List.of();

        // Tìm khóa thứ tự đúng (ORDER:1,2,3,4)
        ExamAnswerKey orderKey = keys.stream()
                .filter(k -> k.getKind() == ExamAnswerKind.ORDER && k.getAnswerText().startsWith("ORDER:"))
                .findFirst()
                .orElse(null);

        // Tìm khóa vị trí ngôi sao nếu có (STAR:x)
        ExamAnswerKey starKey = keys.stream()
                .filter(k -> k.getKind() == ExamAnswerKind.ORDER && k.getAnswerText().startsWith("STAR:"))
                .findFirst()
                .orElse(null);

        if (orderKey == null) {
            return GradingResult.builder()
                    .pointsAwarded(BigDecimal.ZERO)
                    .correct(false)
                    .feedback("Câu hỏi thiếu cấu hình thứ tự đáp án chuẩn.")
                    .build();
        }

        List<Integer> expectedOrders = ExamValidator.parseOrderNumbers(orderKey.getAnswerText().substring(6));

        Integer starPosition = null;
        if (starKey != null) {
            try {
                starPosition = Integer.parseInt(starKey.getAnswerText().substring(5).trim());
            } catch (NumberFormatException ignored) {
            }
        }

        // Tạo văn bản đáp án đúng tham khảo
        String sampleAnswer = buildSampleAnswer(expectedOrders, choices, starPosition);

        if (studentAnswer == null || studentAnswer.isBlank()) {
            return GradingResult.builder()
                    .pointsAwarded(BigDecimal.ZERO)
                    .correct(false)
                    .feedback("Chưa thực hiện ghép câu.")
                    .sampleAnswer(sampleAnswer)
                    .build();
        }

        // Chuyển câu trả lời của học viên sang danh sách thứ tự mảnh ghép (1-based)
        List<Integer> studentOrders = parseStudentOrder(studentAnswer, choices);

        boolean isCorrect;
        String feedback;

        if (starPosition != null && starPosition >= 1 && starPosition <= expectedOrders.size()) {
            // Chấm theo vị trí ngôi sao ★
            int expectedPieceAtStar = expectedOrders.get(starPosition - 1);
            if (studentOrders.size() >= starPosition) {
                int actualPieceAtStar = studentOrders.get(starPosition - 1);
                isCorrect = (actualPieceAtStar == expectedPieceAtStar);
                if (isCorrect) {
                    feedback = String.format("Chính xác! Mảnh từ ở vị trí ngôi sao ★ (vị trí %d) là phương án [%d].",
                            starPosition, expectedPieceAtStar);
                } else {
                    feedback = String.format("Chưa chính xác. Mảnh từ ở vị trí ngôi sao ★ (vị trí %d) đúng là [%d], bạn đã chọn [%d].",
                            starPosition, expectedPieceAtStar, actualPieceAtStar);
                }
            } else {
                isCorrect = false;
                feedback = String.format("Bạn chưa ghép đủ các mảnh đến vị trí ngôi sao ★ (vị trí %d).", starPosition);
            }
        } else {
            // Chấm theo toàn bộ thứ tự khớp
            isCorrect = studentOrders.equals(expectedOrders);
            feedback = isCorrect ? "Chính xác toàn bộ thứ tự ghép câu." : "Thứ tự sắp xếp các mảnh từ chưa chính xác.";
        }

        return GradingResult.builder()
                .pointsAwarded(isCorrect ? maxPoints : BigDecimal.ZERO)
                .correct(isCorrect)
                .feedback(feedback)
                .sampleAnswer(sampleAnswer)
                .build();
    }

    /**
     * Chuyển đổi chuỗi đáp án (có thể là danh sách ID phương án cách nhau bởi dấu phẩy
     * hoặc các số thứ tự mảnh ghép) thành danh sách số thứ tự mảnh ghép (1..N).
     */
    public static List<Integer> parseStudentOrder(String answer, List<ExamChoice> choices) {
        if (answer == null || answer.isBlank()) {
            return List.of();
        }

        Map<String, Integer> idToOrder = new HashMap<>();
        if (choices != null) {
            for (ExamChoice c : choices) {
                if (c.getId() != null) {
                    idToOrder.put(c.getId().toString(), c.getSortOrder());
                }
            }
        }

        String[] tokens = answer.split("[,;\\s]+");
        List<Integer> result = new ArrayList<>();
        for (String t : tokens) {
            String clean = t.trim();
            if (clean.isEmpty()) {
                continue;
            }
            if (idToOrder.containsKey(clean)) {
                result.add(idToOrder.get(clean));
            } else {
                try {
                    int order = Integer.parseInt(clean);
                    result.add(order);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return result;
    }

    private String buildSampleAnswer(List<Integer> expectedOrders, List<ExamChoice> choices, Integer starPosition) {
        StringBuilder sb = new StringBuilder();
        sb.append("Thứ tự đúng: ");
        for (int i = 0; i < expectedOrders.size(); i++) {
            if (i > 0) sb.append(" → ");
            int pieceNum = expectedOrders.get(i);
            sb.append("[").append(pieceNum).append("]");
            if (starPosition != null && (i + 1) == starPosition) {
                sb.append("★");
            }
        }
        if (starPosition != null && starPosition >= 1 && starPosition <= expectedOrders.size()) {
            int starPiece = expectedOrders.get(starPosition - 1);
            String pieceText = choices.stream()
                    .filter(c -> c.getSortOrder() == starPiece)
                    .map(ExamChoice::getText)
                    .findFirst()
                    .orElse("");
            sb.append(" (Vị trí ★ là phương án [").append(starPiece).append("]: ").append(pieceText).append(")");
        }
        return sb.toString();
    }
}

