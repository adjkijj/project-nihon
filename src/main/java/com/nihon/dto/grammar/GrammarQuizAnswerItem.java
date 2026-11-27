package com.nihon.dto.grammar;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Cặp câu trả lời questionId và choiceId của bài trắc nghiệm mini ngữ pháp (F32).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarQuizAnswerItem implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotNull(message = "ID câu hỏi không được để trống.")
    @Positive(message = "ID câu hỏi không hợp lệ.")
    private Long questionId;

    @NotNull(message = "ID lựa chọn không được để trống.")
    @Positive(message = "ID lựa chọn không hợp lệ.")
    private Long choiceId;
}

