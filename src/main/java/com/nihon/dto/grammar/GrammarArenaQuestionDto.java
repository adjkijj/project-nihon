package com.nihon.dto.grammar;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * DTO dữ liệu câu hỏi gửi ra giao diện làm bài đấu trường phản xạ ngữ pháp (F33).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarArenaQuestionDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sessionId;
    private int questionIndex; // 1-based
    private int totalQuestions;
    private long remainingSeconds;
    private String prompt;
    private String grammarTitle;
    private List<GrammarArenaChoiceItem> choices;
    private int currentStreak;
    private int maxStreak;
    private GrammarArenaAnswerRecord lastResult;
}

