package com.nihon.dto.grammar;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả chấm trắc nghiệm mini ngữ pháp (F32).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarQuizCheckResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long grammarId;
    private int totalQuestions;
    private int correctCount;
    private boolean allCorrect;
    private int scorePercent;

    @Builder.Default
    private List<GrammarQuizQuestionFeedback> questionResults = new ArrayList<>();
}

