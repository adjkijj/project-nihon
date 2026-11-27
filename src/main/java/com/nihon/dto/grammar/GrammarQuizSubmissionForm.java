package com.nihon.dto.grammar;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Form gửi bài làm trắc nghiệm mini ngữ pháp (F32).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarQuizSubmissionForm implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotNull(message = "ID chủ đề ngữ pháp không được để trống.")
    @Positive(message = "ID chủ đề ngữ pháp không hợp lệ.")
    private Long grammarId;

    @Valid
    @Size(max = 20, message = "Số lượng câu trả lời không được vượt quá 20 câu.")
    @Builder.Default
    private List<GrammarQuizAnswerItem> answers = new ArrayList<>();
}

