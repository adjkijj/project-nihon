package com.nihon.service.exam.grading;

import com.nihon.entity.ExamAnswerKind;
import com.nihon.entity.ExamAnswerKey;
import com.nihon.entity.ExamQuestion;
import com.nihon.entity.ExamQuestionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Bộ chấm câu hỏi dịch câu (TRANSLATION - F44).
 * So khớp tương đối với các đáp án ACCEPTED hoặc SAMPLE bằng thuật toán Levenshtein:
 * - >= 90% tương đồng: đạt 100% điểm
 * - 75% đến < 90% tương đồng: đạt 50% điểm
 * - < 75% tương đồng: 0 điểm
 * Phản hồi ghi rõ kết quả mang tính chất chấm tự động tham khảo.
 */
@Component
public class TranslationGrader implements QuestionGrader {

    public static final double THRESHOLD_FULL = 0.90;
    public static final double THRESHOLD_HALF = 0.75;

    @Override
    public ExamQuestionType supportsType() {
        return ExamQuestionType.TRANSLATION;
    }

    @Override
    public GradingResult grade(ExamQuestion question, String studentAnswer) {
        BigDecimal maxPoints = question.getPoints() != null ? question.getPoints() : BigDecimal.ONE;
        List<ExamAnswerKey> keys = question.getAnswerKeys() != null ? question.getAnswerKeys() : List.of();

        List<ExamAnswerKey> targetKeys = keys.stream()
                .filter(k -> k.getKind() == ExamAnswerKind.ACCEPTED || k.getKind() == ExamAnswerKind.SAMPLE)
                .toList();

        String sampleAnswer = targetKeys.stream()
                .map(ExamAnswerKey::getAnswerText)
                .filter(s -> s != null && !s.isBlank())
                .findFirst()
                .orElse("");

        if (studentAnswer == null || studentAnswer.isBlank() || targetKeys.isEmpty()) {
            return GradingResult.builder()
                    .pointsAwarded(BigDecimal.ZERO)
                    .correct(false)
                    .similarity(0.0)
                    .feedback("Chưa nhập bài dịch. Chấm tự động tham khảo.")
                    .sampleAnswer(sampleAnswer)
                    .build();
        }

        String normalizedStudent = normalizeTranslation(studentAnswer);

        double maxSimilarity = 0.0;
        String closestKeyText = sampleAnswer;

        for (ExamAnswerKey key : targetKeys) {
            String keyText = key.getAnswerText();
            String normalizedKey = normalizeTranslation(keyText);
            if (normalizedKey.isEmpty()) {
                continue;
            }

            double sim = LevenshteinDistance.computeSimilarity(normalizedStudent, normalizedKey);
            if (sim > maxSimilarity) {
                maxSimilarity = sim;
                closestKeyText = keyText;
            }
        }

        BigDecimal pointsAwarded;
        boolean correct;
        String feedback;

        if (maxSimilarity >= THRESHOLD_FULL) {
            pointsAwarded = maxPoints;
            correct = true;
            feedback = String.format("Đúng hoàn toàn / độ tương đồng cao (%.1f%%). Chấm tự động tham khảo.", maxSimilarity * 100);
        } else if (maxSimilarity >= THRESHOLD_HALF) {
            pointsAwarded = maxPoints.multiply(BigDecimal.valueOf(0.5)).setScale(2, RoundingMode.HALF_UP);
            correct = true;
            feedback = String.format("Độ tương đồng đạt %.1f%% (được 50%% số điểm). Chấm tự động tham khảo.", maxSimilarity * 100);
        } else {
            pointsAwarded = BigDecimal.ZERO;
            correct = false;
            feedback = String.format("Độ tương đồng %.1f%% (< 75%%). Chấm tự động tham khảo.", maxSimilarity * 100);
        }

        return GradingResult.builder()
                .pointsAwarded(pointsAwarded)
                .correct(correct)
                .similarity(maxSimilarity)
                .feedback(feedback)
                .sampleAnswer(closestKeyText)
                .build();
    }

    /**
     * Chuẩn hóa văn bản dịch câu: NFKC, chuyển chữ thường, bỏ toàn bộ khoảng trắng và dấu câu.
     */
    public static String normalizeTranslation(String text) {
        if (text == null) {
            return "";
        }
        String normalized = Normalizer.normalize(text.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        // Bỏ khoảng trắng
        normalized = normalized.replaceAll("\\s+", "");
        // Bỏ toàn bộ dấu câu (tiếng Nhật và ký tự ASCII/Unicode)
        normalized = normalized.replaceAll("[。、.,!?！？:：;；()（）「」『』\"'\"_~～\\-\\/\\#\\[\\]\\{\\}]+", "");
        return normalized;
    }
}

