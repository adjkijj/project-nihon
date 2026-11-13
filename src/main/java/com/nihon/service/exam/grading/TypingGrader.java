package com.nihon.service.exam.grading;

import com.nihon.entity.ExamAnswerKind;
import com.nihon.entity.ExamAnswerKey;
import com.nihon.entity.ExamQuestion;
import com.nihon.entity.ExamQuestionType;
import com.nihon.service.RomajiConverter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;

/**
 * Bộ chấm câu hỏi nhập đáp án (TYPING).
 * So khớp với các đáp án ACCEPTED sau khi chuẩn hóa NFKC, bỏ khoảng trắng,
 * bỏ dấu câu ở cuối, và hỗ trợ chuyển Katakana sang Hiragana nếu câu hỏi có cờ cấu hình.
 */
@Component
public class TypingGrader implements QuestionGrader {

    public static final String FLAG_CONVERT_KANA = "[CONVERT_KANA]";

    @Override
    public ExamQuestionType supportsType() {
        return ExamQuestionType.TYPING;
    }

    @Override
    public GradingResult grade(ExamQuestion question, String studentAnswer) {
        BigDecimal maxPoints = question.getPoints() != null ? question.getPoints() : BigDecimal.ONE;
        List<ExamAnswerKey> keys = question.getAnswerKeys() != null ? question.getAnswerKeys() : List.of();

        List<ExamAnswerKey> acceptedKeys = keys.stream()
                .filter(k -> k.getKind() == ExamAnswerKind.ACCEPTED)
                .toList();

        // Kiểm tra cờ chuyển Katakana sang Hiragana (mặc định tắt)
        boolean convertKana = isConvertKanaEnabled(question, keys);

        String sampleAnswer = acceptedKeys.stream()
                .map(k -> cleanKeyText(k.getAnswerText()))
                .filter(s -> !s.isBlank())
                .findFirst()
                .orElse("");

        if (studentAnswer == null || studentAnswer.isBlank() || acceptedKeys.isEmpty()) {
            return GradingResult.builder()
                    .pointsAwarded(BigDecimal.ZERO)
                    .correct(false)
                    .feedback("Chưa nhập câu trả lời hoặc câu trả lời không chính xác.")
                    .sampleAnswer(sampleAnswer)
                    .build();
        }

        String normalizedStudent = normalizeAnswer(studentAnswer, convertKana);

        boolean isCorrect = false;
        for (ExamAnswerKey key : acceptedKeys) {
            String keyText = cleanKeyText(key.getAnswerText());
            String normalizedKey = normalizeAnswer(keyText, convertKana);
            if (!normalizedKey.isEmpty() && normalizedStudent.equals(normalizedKey)) {
                isCorrect = true;
                break;
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

    public static String normalizeAnswer(String text, boolean convertKana) {
        if (text == null) {
            return "";
        }
        String normalized = Normalizer.normalize(text.trim(), Normalizer.Form.NFKC);
        normalized = normalized.replaceAll("\\s+", "");
        // Bỏ dấu câu ở cuối chuỗi (cả ký tự Nhật và ASCII)
        normalized = normalized.replaceAll("[。、.!,?!？]+$", "");
        if (convertKana) {
            normalized = RomajiConverter.katakanaToHiragana(normalized);
        }
        return normalized;
    }

    private boolean isConvertKanaEnabled(ExamQuestion question, List<ExamAnswerKey> keys) {
        if (question.getExplanation() != null && question.getExplanation().contains(FLAG_CONVERT_KANA)) {
            return true;
        }
        return keys.stream().anyMatch(k -> k.getAnswerText() != null && k.getAnswerText().contains(FLAG_CONVERT_KANA));
    }

    private String cleanKeyText(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace(FLAG_CONVERT_KANA, "").trim();
    }
}

