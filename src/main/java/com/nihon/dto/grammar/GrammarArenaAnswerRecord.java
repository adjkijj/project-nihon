package com.nihon.dto.grammar;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Bản ghi kết quả trả lời một câu hỏi trong phiên đấu trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarArenaAnswerRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private int questionIndex;
    private Long questionId;
    private String prompt;
    private Long userChoiceId;
    private String userChoiceText;
    private Long correctChoiceId;
    private String correctChoiceText;
    private boolean correct;
    private String explanation;
    private int streakAfter;
}

