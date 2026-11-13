package com.nihon.dto.grammar;

import com.nihon.entity.GrammarQuizChoice;
import com.nihon.validation.NoControlChars;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Form DTO cho một lựa chọn trắc nghiệm câu hỏi ngữ pháp (F30).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarQuizChoiceForm {

    private Long id;

    @Builder.Default
    private Integer sortOrder = 1;

    @NotBlank(message = "Nội dung đáp án không được để trống.")
    @Size(max = 300, message = "Nội dung đáp án không được vượt quá 300 ký tự.")
    @NoControlChars(message = "Nội dung đáp án không được chứa ký tự điều khiển.")
    private String text;

    @Builder.Default
    private boolean correct = false;

    public static GrammarQuizChoiceForm fromEntity(GrammarQuizChoice choice) {
        if (choice == null) {
            return new GrammarQuizChoiceForm();
        }
        return GrammarQuizChoiceForm.builder()
                .id(choice.getId())
                .sortOrder(choice.getSortOrder())
                .text(choice.getText())
                .correct(choice.isCorrect())
                .build();
    }
}

