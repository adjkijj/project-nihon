package com.nihon.dto.grammar;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Đánh giá kết quả cho từng câu hỏi trong bài kiểm tra mini ngữ pháp.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarQuizQuestionFeedback implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long questionId;
    private String prompt;
    private Long userChoiceId;
    private Long correctChoiceId;
    private String userChoiceText;
    private String correctChoiceText;
    private boolean correct;
    private String explanation;
}

