package com.nihon.service.exam.grading;

import com.nihon.entity.ExamAnswerKind;
import com.nihon.entity.ExamAnswerKey;
import com.nihon.entity.ExamQuestion;
import com.nihon.entity.ExamQuestionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bộ xử lý câu hỏi tự luận / viết đoạn văn (ESSAY - F45).
 * Không chấm điểm tự động (correct = null, points = 0).
 * Trích xuất bài mẫu tham khảo (SAMPLE) và đối chiếu danh sách từ khóa bắt buộc (KEYWORD)
 * để hiển thị trạng thái "có trong bài" / "chưa có". Người học sẽ tự chấm điểm.
 */
@Component
public class EssayGrader implements QuestionGrader {

    @Override
    public ExamQuestionType supportsType() {
        return ExamQuestionType.ESSAY;
    }

    @Override
    public GradingResult grade(ExamQuestion question, String studentAnswer) {
        List<ExamAnswerKey> keys = question.getAnswerKeys() != null ? question.getAnswerKeys() : List.of();

        // Trích xuất bài mẫu tham khảo (SAMPLE)
        String sampleAnswer = keys.stream()
                .filter(k -> k.getKind() == ExamAnswerKind.SAMPLE)
                .map(ExamAnswerKey::getAnswerText)
                .filter(s -> s != null && !s.isBlank())
                .findFirst()
                .orElse("");

        // Trích xuất các từ khóa (KEYWORD)
        List<String> keywords = keys.stream()
                .filter(k -> k.getKind() == ExamAnswerKind.KEYWORD)
                .map(ExamAnswerKey::getAnswerText)
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .toList();

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        String normalizedStudent = normalizeForKeywordCheck(studentAnswer);

        for (String kw : keywords) {
            String normalizedKw = normalizeForKeywordCheck(kw);
            if (!normalizedKw.isEmpty() && normalizedStudent.contains(normalizedKw)) {
                matched.add(kw);
            } else {
                missing.add(kw);
            }
        }

        String feedback = "Câu hỏi tự luận không chấm điểm tự động. Vui lòng đối chiếu với bài mẫu và các từ khóa bên dưới để tự cho điểm.";

        return GradingResult.builder()
                .pointsAwarded(BigDecimal.ZERO)
                .correct(null) // null = chưa chấm / tự chấm
                .feedback(feedback)
                .sampleAnswer(sampleAnswer)
                .matchedKeywords(matched)
                .missingKeywords(missing)
                .build();
    }

    public static String normalizeForKeywordCheck(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }
}

