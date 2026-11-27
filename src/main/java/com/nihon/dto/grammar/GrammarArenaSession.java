package com.nihon.dto.grammar;

import com.nihon.dto.timed.TimedSessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Phiên đấu trường phản xạ ngữ pháp lưu trong HttpSession (F33).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarArenaSession implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String SESSION_ATTR = "GRAMMAR_ARENA_SESSION";

    private String id;
    private String category;
    private Long topicId;
    private String topicName;
    private int durationMinutes;
    private int questionCount;
    private Instant startedAt;
    private Instant deadline;

    @Builder.Default
    private int currentIndex = 0;

    @Builder.Default
    private int currentStreak = 0;

    @Builder.Default
    private int maxStreak = 0;

    @Builder.Default
    private List<GrammarArenaQuestionItem> questions = new ArrayList<>();

    @Builder.Default
    private List<GrammarArenaAnswerRecord> answers = new ArrayList<>();

    @Builder.Default
    private TimedSessionStatus status = TimedSessionStatus.RUNNING;

    public boolean isRunning() {
        return status == TimedSessionStatus.RUNNING;
    }

    public boolean isExpired(Instant now) {
        if (deadline == null) {
            return false;
        }
        // Cho phép dung sai trễ mạng 3 giây
        return now.isAfter(deadline.plusSeconds(3));
    }

    public long getRemainingSeconds(Instant now) {
        if (deadline == null || status != TimedSessionStatus.RUNNING) {
            return 0;
        }
        long rem = Duration.between(now, deadline).toSeconds();
        return Math.max(0, rem);
    }

    public int getCorrectCount() {
        if (answers == null) return 0;
        return (int) answers.stream().filter(GrammarArenaAnswerRecord::isCorrect).count();
    }

    public int getAccuracyPercent() {
        if (answers == null || answers.isEmpty()) return 0;
        return (int) Math.round((double) getCorrectCount() * 100.0 / answers.size());
    }

    public int getDurationSeconds(Instant now) {
        if (startedAt == null) return 0;
        Instant end = (deadline != null && now.isAfter(deadline)) ? deadline : now;
        return (int) Duration.between(startedAt, end).toSeconds();
    }
}

