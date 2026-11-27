package com.nihon.dto.grammar;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Mục câu hỏi lưu trong phiên đấu trường tại server (giữ đáp án đúng để đối chiếu).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarArenaQuestionItem implements Serializable {
    private static final long serialVersionUID = 1L;

    private int index; // 1-based
    private Long questionId;
    private Long grammarId;
    private String grammarTitle;
    private String prompt;
    private String explanation;
    private List<GrammarArenaChoiceItem> choices;
    private Long correctChoiceId;
    private String correctChoiceText;
}

