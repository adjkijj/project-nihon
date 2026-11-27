package com.nihon.dto.grammar;

import com.nihon.dto.timed.TimedSessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO tổng kết kết quả đấu trường phản xạ ngữ pháp (F33).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarArenaResultDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sessionId;
    private String category;
    private String topicName;
    private int totalQuestions;
    private int answeredCount;
    private int correctCount;
    private int accuracyPercent;
    private int maxStreak;
    private int durationSeconds;
    private TimedSessionStatus status;

    @Builder.Default
    private List<GrammarArenaAnswerRecord> details = new ArrayList<>();
}

