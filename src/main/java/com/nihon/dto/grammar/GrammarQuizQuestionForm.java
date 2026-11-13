package com.nihon.dto.grammar;

import com.nihon.entity.GrammarQuizChoice;
import com.nihon.entity.GrammarQuizQuestion;
import com.nihon.validation.NoControlChars;
import com.nihon.validation.VietnameseText;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Form DTO để thêm mới hoặc sửa câu hỏi trắc nghiệm ngữ pháp (F30).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarQuizQuestionForm {

    private Long id;

    @Builder.Default
    private Integer sortOrder = 1;

    @NotBlank(message = "Đề bài câu hỏi không được để trống.")
    @Size(max = 500, message = "Đề bài không được vượt quá 500 ký tự.")
    @NoControlChars(message = "Đề bài không được chứa ký tự điều khiển.")
    private String prompt;

    @Size(max = 1000, message = "Giải thích câu hỏi không được vượt quá 1000 ký tự.")
    @VietnameseText(allowNewlines = true, allowJapanese = true, message = "Giải thích chứa ký tự không hợp lệ hoặc chứa mã HTML.")
    private String explanation;

    private Integer correctChoiceIndex;

    @Valid
    @Builder.Default
    private List<GrammarQuizChoiceForm> choices = new ArrayList<>();

    public static GrammarQuizQuestionForm fromEntity(GrammarQuizQuestion question) {
        if (question == null) {
            return new GrammarQuizQuestionForm();
        }
        List<GrammarQuizChoiceForm> choiceForms = new ArrayList<>();
        Integer correctIdx = null;
        if (question.getChoices() != null) {
            for (int i = 0; i < question.getChoices().size(); i++) {
                GrammarQuizChoice choice = question.getChoices().get(i);
                choiceForms.add(GrammarQuizChoiceForm.fromEntity(choice));
                if (choice.isCorrect()) {
                    correctIdx = i;
                }
            }
        }
        return GrammarQuizQuestionForm.builder()
                .id(question.getId())
                .sortOrder(question.getSortOrder())
                .prompt(question.getPrompt())
                .explanation(question.getExplanation())
                .correctChoiceIndex(correctIdx)
                .choices(choiceForms)
                .build();
    }
}

