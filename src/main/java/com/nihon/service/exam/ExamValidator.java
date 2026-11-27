package com.nihon.service.exam;

import com.nihon.entity.ExamAnswerKind;
import com.nihon.entity.ExamQuestionType;
import com.nihon.validation.TextNormalizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tiện ích thẩm định nghiệp vụ câu hỏi và cấu trúc đề thi (F41).
 * Áp dụng thống nhất cho cả thao tác quản trị qua giao diện web và quy trình nhập dữ liệu từ tệp Excel.
 */
public final class ExamValidator {

    public static final int MAX_SECTIONS_PER_EXAM = 10;
    public static final int MAX_QUESTIONS_PER_EXAM = 200;

    public static final int MIN_CHOICES_MULTIPLE_CHOICE = 2;
    public static final int MAX_CHOICES_MULTIPLE_CHOICE = 6;

    public static final int MIN_FRAGMENTS_ORDERING = 3;
    public static final int MAX_FRAGMENTS_ORDERING = 10;

    private ExamValidator() {}

    /**
     * DTO đại diện cho một khóa đáp án phục vụ việc kiểm tra tính hợp lệ trước khi lưu.
     */
    public record AnswerKeyItem(ExamAnswerKind kind, String text) {}

    /**
     * Kiểm tra tính hợp lệ của câu hỏi thi theo loại và quy tắc nghiệp vụ.
     * Ném ra IllegalArgumentException kèm thông điệp chi tiết nếu vi phạm.
     */
    public static void validateQuestion(ExamQuestionType type,
                                         String promptJa,
                                         String promptVi,
                                         List<String> rawChoices,
                                         Integer correctChoiceIndex,
                                         List<AnswerKeyItem> answerKeys) {
        if (type == null) {
            throw new IllegalArgumentException("Loại câu hỏi không được để trống.");
        }

        boolean hasPromptJa = promptJa != null && !promptJa.isBlank();
        boolean hasPromptVi = promptVi != null && !promptVi.isBlank();
        if (!hasPromptJa && !hasPromptVi) {
            throw new IllegalArgumentException("Câu hỏi phải có ít nhất một đề bài (tiếng Nhật hoặc tiếng Việt).");
        }

        // Lọc các lựa chọn không rỗng
        List<String> cleanChoices = new ArrayList<>();
        if (rawChoices != null) {
            for (String c : rawChoices) {
                if (c != null && !c.isBlank()) {
                    cleanChoices.add(TextNormalizer.normalize(c.trim()));
                }
            }
        }

        List<AnswerKeyItem> cleanKeys = new ArrayList<>();
        if (answerKeys != null) {
            for (AnswerKeyItem k : answerKeys) {
                if (k != null && k.text() != null && !k.text().isBlank()) {
                    cleanKeys.add(new AnswerKeyItem(k.kind(), TextNormalizer.normalize(k.text().trim())));
                }
            }
        }

        switch (type) {
            case MULTIPLE_CHOICE -> validateMultipleChoice(cleanChoices, correctChoiceIndex);
            case TYPING -> validateTyping(cleanKeys);
            case ORDERING -> validateOrdering(cleanChoices, cleanKeys);
            case TRANSLATION -> validateTranslation(cleanKeys);
            case ESSAY -> validateEssay(cleanKeys);
        }
    }

    private static void validateMultipleChoice(List<String> choices, Integer correctChoiceIndex) {
        if (choices.size() < MIN_CHOICES_MULTIPLE_CHOICE || choices.size() > MAX_CHOICES_MULTIPLE_CHOICE) {
            throw new IllegalArgumentException(String.format(
                    "Câu hỏi trắc nghiệm phải có từ %d đến %d phương án lựa chọn (hiện có: %d).",
                    MIN_CHOICES_MULTIPLE_CHOICE, MAX_CHOICES_MULTIPLE_CHOICE, choices.size()
            ));
        }

        if (correctChoiceIndex == null || correctChoiceIndex < 1 || correctChoiceIndex > choices.size()) {
            throw new IllegalArgumentException(String.format(
                    "Câu hỏi trắc nghiệm phải có đúng 1 đáp án đúng (chọn từ 1 đến %d).",
                    choices.size()
            ));
        }
    }

    private static void validateTyping(List<AnswerKeyItem> answerKeys) {
        boolean hasAccepted = answerKeys.stream()
                .anyMatch(k -> k.kind() == ExamAnswerKind.ACCEPTED && !k.text().isBlank());
        if (!hasAccepted) {
            throw new IllegalArgumentException("Câu hỏi gõ từ (TYPING) phải có ít nhất một đáp án đúng được chấp nhận (ACCEPTED).");
        }
    }

    private static void validateOrdering(List<String> choices, List<AnswerKeyItem> answerKeys) {
        int n = choices.size();
        if (n < MIN_FRAGMENTS_ORDERING || n > MAX_FRAGMENTS_ORDERING) {
            throw new IllegalArgumentException(String.format(
                    "Câu hỏi ghép câu (ORDERING) phải có từ %d đến %d mảnh từ (hiện có: %d).",
                    MIN_FRAGMENTS_ORDERING, MAX_FRAGMENTS_ORDERING, n
            ));
        }

        // Tìm khóa ORDER:1,2,3,4
        AnswerKeyItem orderKey = answerKeys.stream()
                .filter(k -> k.kind() == ExamAnswerKind.ORDER && k.text().startsWith("ORDER:"))
                .findFirst()
                .orElse(null);

        if (orderKey == null) {
            throw new IllegalArgumentException("Câu hỏi ghép câu phải có khóa thứ tự sắp xếp đúng (định dạng ORDER:1,2,3,...).");
        }

        String orderSequence = orderKey.text().substring(6).trim();
        List<Integer> parsedOrder = parseOrderNumbers(orderSequence);

        if (parsedOrder.size() != n) {
            throw new IllegalArgumentException(String.format(
                    "Thứ tự đúng '%s' không khớp với số lượng %d mảnh từ.",
                    orderSequence, n
            ));
        }

        Set<Integer> expectedSet = new HashSet<>();
        for (int i = 1; i <= n; i++) {
            expectedSet.add(i);
        }

        Set<Integer> actualSet = new HashSet<>(parsedOrder);
        if (!actualSet.equals(expectedSet)) {
            throw new IllegalArgumentException(String.format(
                    "Thứ tự đúng '%s' phải là hoán vị hợp lệ đầy đủ của các số từ 1 đến %d.",
                    orderSequence, n
            ));
        }

        // Kiểm tra vị trí ngôi sao (STAR:x) nếu có
        AnswerKeyItem starKey = answerKeys.stream()
                .filter(k -> k.kind() == ExamAnswerKind.ORDER && k.text().startsWith("STAR:"))
                .findFirst()
                .orElse(null);

        if (starKey != null) {
            String starStr = starKey.text().substring(5).trim();
            try {
                int starIdx = Integer.parseInt(starStr);
                if (starIdx < 1 || starIdx > n) {
                    throw new IllegalArgumentException(String.format(
                            "Vị trí ngôi sao (%d) phải nằm trong khoảng từ 1 đến %d.",
                            starIdx, n
                    ));
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Vị trí ngôi sao không hợp lệ: '" + starStr + "'. Phải là số nguyên từ 1 đến " + n + ".");
            }
        }
    }

    private static void validateTranslation(List<AnswerKeyItem> answerKeys) {
        boolean hasValidAnswer = answerKeys.stream()
                .anyMatch(k -> (k.kind() == ExamAnswerKind.ACCEPTED || k.kind() == ExamAnswerKind.SAMPLE)
                        && !k.text().isBlank());
        if (!hasValidAnswer) {
            throw new IllegalArgumentException("Câu hỏi dịch câu (TRANSLATION) phải có ít nhất một đáp án chấp nhận (ACCEPTED) hoặc câu dịch mẫu (SAMPLE).");
        }
    }

    private static void validateEssay(List<AnswerKeyItem> answerKeys) {
        boolean hasSample = answerKeys.stream()
                .anyMatch(k -> k.kind() == ExamAnswerKind.SAMPLE && !k.text().isBlank());
        if (!hasSample) {
            throw new IllegalArgumentException("Câu hỏi tự luận / viết đoạn văn (ESSAY) phải có ít nhất một bài mẫu tham khảo (SAMPLE).");
        }
    }

    public static void validateExamLimits(int totalSections, int totalQuestions) {
        if (totalSections > MAX_SECTIONS_PER_EXAM) {
            throw new IllegalArgumentException(String.format(
                    "Đề thi vượt quá giới hạn tối đa %d phần thi (hiện tại: %d).",
                    MAX_SECTIONS_PER_EXAM, totalSections
            ));
        }
        if (totalQuestions > MAX_QUESTIONS_PER_EXAM) {
            throw new IllegalArgumentException(String.format(
                    "Đề thi vượt quá giới hạn tối đa %d câu hỏi (hiện tại: %d).",
                    MAX_QUESTIONS_PER_EXAM, totalQuestions
            ));
        }
    }

    public static List<Integer> parseOrderNumbers(String orderSequence) {
        if (orderSequence == null || orderSequence.isBlank()) {
            return List.of();
        }
        // Cho phép phân cách bởi dấu phẩy, dấu chấm phẩy, khoảng trắng, gạch ngang
        String cleaned = orderSequence.replaceAll("[^0-9,]", ",");
        return Arrays.stream(cleaned.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return Integer.parseInt(s);
                    } catch (NumberFormatException e) {
                        return -1;
                    }
                })
                .collect(Collectors.toList());
    }
}

