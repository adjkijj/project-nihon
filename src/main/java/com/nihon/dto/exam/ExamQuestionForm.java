package com.nihon.dto.exam;

import com.nihon.entity.ExamAnswerKey;
import com.nihon.entity.ExamAnswerKind;
import com.nihon.entity.ExamChoice;
import com.nihon.entity.ExamQuestion;
import com.nihon.entity.ExamQuestionType;
import com.nihon.validation.JapaneseText;
import com.nihon.validation.NoControlChars;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Form DTO để thêm mới hoặc cập nhật Câu hỏi thi (F41).
 * Hỗ trợ linh hoạt theo loại câu hỏi: MULTIPLE_CHOICE, TYPING, ORDERING, TRANSLATION, ESSAY.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamQuestionForm {

    private Long id;

    @NotNull(message = "Phần thi không được để trống.")
    private Long sectionId;

    @NotBlank(message = "Vui lòng chọn loại câu hỏi.")
    private String type;

    @Size(max = 1000, message = "Đề bài tiếng Nhật không được vượt quá 1000 ký tự.")
    @JapaneseText(optional = true, message = "Đề bài tiếng Nhật phải chứa ít nhất một ký tự tiếng Nhật hợp lệ.")
    @NoControlChars(message = "Đề bài tiếng Nhật không được chứa ký tự điều khiển.")
    private String promptJa;

    @Size(max = 1000, message = "Đề bài tiếng Việt không được vượt quá 1000 ký tự.")
    @NoControlChars(message = "Đề bài tiếng Việt không được chứa ký tự điều khiển.")
    private String promptVi;

    @NotNull(message = "Điểm số không được để trống.")
    @DecimalMin(value = "0.01", message = "Điểm số phải từ 0.01 trở lên.")
    @DecimalMax(value = "100.00", message = "Điểm số tối đa là 100.00.")
    @Builder.Default
    private BigDecimal points = BigDecimal.valueOf(1.00);

    @Size(max = 1000, message = "Giải thích không được vượt quá 1000 ký tự.")
    @NoControlChars(message = "Giải thích không được chứa ký tự điều khiển.")
    private String explanation;

    private int sortOrder;

    // Các phương án lựa chọn (cho MULTIPLE_CHOICE hoặc mảnh ghép ORDERING)
    @Size(max = 500, message = "Lựa chọn 1 không được vượt quá 500 ký tự.")
    @NoControlChars(message = "Lựa chọn 1 không được chứa ký tự điều khiển.")
    private String choice1;

    @Size(max = 500, message = "Lựa chọn 2 không được vượt quá 500 ký tự.")
    @NoControlChars(message = "Lựa chọn 2 không được chứa ký tự điều khiển.")
    private String choice2;

    @Size(max = 500, message = "Lựa chọn 3 không được vượt quá 500 ký tự.")
    @NoControlChars(message = "Lựa chọn 3 không được chứa ký tự điều khiển.")
    private String choice3;

    @Size(max = 500, message = "Lựa chọn 4 không được vượt quá 500 ký tự.")
    @NoControlChars(message = "Lựa chọn 4 không được chứa ký tự điều khiển.")
    private String choice4;

    @Size(max = 500, message = "Lựa chọn 5 không được vượt quá 500 ký tự.")
    @NoControlChars(message = "Lựa chọn 5 không được chứa ký tự điều khiển.")
    private String choice5;

    @Size(max = 500, message = "Lựa chọn 6 không được vượt quá 500 ký tự.")
    @NoControlChars(message = "Lựa chọn 6 không được chứa ký tự điều khiển.")
    private String choice6;

    // Chỉ số lựa chọn đúng cho trắc nghiệm (1..6)
    private Integer correctChoiceIndex;

    // Cho TYPING: Các đáp án chấp nhận (ngăn cách bằng "|" hoặc xuống dòng)
    @Size(max = 1000, message = "Đáp án gõ không được vượt quá 1000 ký tự.")
    @NoControlChars(message = "Đáp án gõ không được chứa ký tự điều khiển.")
    private String typingAcceptedAnswers;

    // Cho ORDERING: Thứ tự đúng (ví dụ "1,3,2,4") và vị trí ngôi sao
    @Size(max = 100, message = "Thứ tự sắp xếp không được vượt quá 100 ký tự.")
    @NoControlChars(message = "Thứ tự sắp xếp không được chứa ký tự điều khiển.")
    private String orderingOrder;

    private Integer orderingStar;

    // Cho TRANSLATION: Đáp án chấp nhận và câu mẫu
    @Size(max = 1000, message = "Đáp án dịch chấp nhận không được vượt quá 1000 ký tự.")
    @NoControlChars(message = "Đáp án dịch chấp nhận không được chứa ký tự điều khiển.")
    private String translationAccepted;

    @Size(max = 1000, message = "Câu dịch mẫu không được vượt quá 1000 ký tự.")
    @NoControlChars(message = "Câu dịch mẫu không được chứa ký tự điều khiển.")
    private String translationSample;

    // Cho ESSAY: Bài mẫu và từ khóa
    @Size(max = 1000, message = "Bài mẫu không được vượt quá 1000 ký tự.")
    @NoControlChars(message = "Bài mẫu không được chứa ký tự điều khiển.")
    private String essaySample;

    @Size(max = 1000, message = "Từ khóa không được vượt quá 1000 ký tự.")
    @NoControlChars(message = "Từ khóa không được chứa ký tự điều khiển.")
    private String essayKeywords;

    public static ExamQuestionForm fromEntity(ExamQuestion question) {
        if (question == null) {
            return null;
        }

        ExamQuestionForm form = ExamQuestionForm.builder()
                .id(question.getId())
                .sectionId(question.getSection() != null ? question.getSection().getId() : null)
                .type(question.getType() != null ? question.getType().name() : ExamQuestionType.MULTIPLE_CHOICE.name())
                .promptJa(question.getPromptJa())
                .promptVi(question.getPromptVi())
                .points(question.getPoints())
                .explanation(question.getExplanation())
                .sortOrder(question.getSortOrder())
                .build();

        // Populate choices
        List<ExamChoice> choices = question.getChoices();
        if (choices != null && !choices.isEmpty()) {
            for (int i = 0; i < choices.size(); i++) {
                ExamChoice c = choices.get(i);
                if (i == 0) form.setChoice1(c.getText());
                else if (i == 1) form.setChoice2(c.getText());
                else if (i == 2) form.setChoice3(c.getText());
                else if (i == 3) form.setChoice4(c.getText());
                else if (i == 4) form.setChoice5(c.getText());
                else if (i == 5) form.setChoice6(c.getText());

                if (c.isCorrect()) {
                    form.setCorrectChoiceIndex(i + 1);
                }
            }
        }

        // Populate answer keys
        List<ExamAnswerKey> keys = question.getAnswerKeys();
        if (keys != null && !keys.isEmpty()) {
            List<String> typingAccepted = new ArrayList<>();
            List<String> transAccepted = new ArrayList<>();
            List<String> essayKw = new ArrayList<>();

            for (ExamAnswerKey key : keys) {
                if (key.getKind() == ExamAnswerKind.ACCEPTED) {
                    if (question.getType() == ExamQuestionType.TYPING) {
                        typingAccepted.add(key.getAnswerText());
                    } else if (question.getType() == ExamQuestionType.TRANSLATION) {
                        transAccepted.add(key.getAnswerText());
                    }
                } else if (key.getKind() == ExamAnswerKind.SAMPLE) {
                    if (question.getType() == ExamQuestionType.TRANSLATION) {
                        form.setTranslationSample(key.getAnswerText());
                    } else if (question.getType() == ExamQuestionType.ESSAY) {
                        form.setEssaySample(key.getAnswerText());
                    }
                } else if (key.getKind() == ExamAnswerKind.KEYWORD) {
                    essayKw.add(key.getAnswerText());
                } else if (key.getKind() == ExamAnswerKind.ORDER) {
                    String txt = key.getAnswerText();
                    if (txt != null && txt.startsWith("ORDER:")) {
                        form.setOrderingOrder(txt.substring(6).trim());
                    } else if (txt != null && txt.startsWith("STAR:")) {
                        try {
                            form.setOrderingStar(Integer.parseInt(txt.substring(5).trim()));
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }

            if (!typingAccepted.isEmpty()) {
                form.setTypingAcceptedAnswers(String.join(" | ", typingAccepted));
            }
            if (!transAccepted.isEmpty()) {
                form.setTranslationAccepted(String.join(" | ", transAccepted));
            }
            if (!essayKw.isEmpty()) {
                form.setEssayKeywords(String.join(", ", essayKw));
            }
        }

        return form;
    }
}

