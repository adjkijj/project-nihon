package com.nihon.dto.exam.taker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phiên làm bài thi của thí sinh (F42, F48).
 * Lưu trong HttpSession (hoặc chuẩn bị chuyển database ở Prompt 21).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamAttempt implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String SESSION_ATTEMPTS_MAP = "SESSION_EXAM_ATTEMPTS_MAP";

    private String attemptId; // UUID
    private Long paperId;
    private String paperCode;
    private String paperTitle;
    private String paperDescription;
    private int durationMinutes;
    private String category;

    private Instant startedAt;
    private Instant deadline;
    private Instant submittedAt;

    private ExamAttemptStatus status;

    @Builder.Default
    private Map<Long, String> answers = new HashMap<>(); // questionId -> answer text or choiceId

    @Builder.Default
    private Set<Long> markedForReview = new HashSet<>(); // Set of questionId

    private long seed;

    @Builder.Default
    private Map<Long, List<Long>> shuffledChoiceIds = new HashMap<>();

    private ExamTakerPaperDto paperDto;

    /**
     * Kiểm tra phiên làm bài thi đã hết hạn hay chưa.
     * Cho phép 3 giây ân hạn (grace period) bù trừ độ trễ mạng.
     */
    public boolean isExpired(Instant now) {
        if (deadline == null) {
            return false;
        }
        return now.isAfter(deadline.plusSeconds(3));
    }

    /**
     * Tính số giây còn lại từ now đến deadline (không âm).
     */
    public long getRemainingSeconds(Instant now) {
        if (deadline == null) {
            return 0;
        }
        return Math.max(0, Duration.between(now, deadline).toSeconds());
    }

    public boolean isRunning() {
        return status == ExamAttemptStatus.RUNNING;
    }

    public boolean isCompleted() {
        return status == ExamAttemptStatus.FINISHED || status == ExamAttemptStatus.EXPIRED;
    }

    public int getAnsweredCount() {
        if (answers == null) {
            return 0;
        }
        return (int) answers.values().stream()
                .filter(a -> a != null && !a.trim().isEmpty())
                .count();
    }

    public int getTotalQuestions() {
        return paperDto != null ? paperDto.getTotalQuestions() : 0;
    }

    public int getUnansweredCount() {
        return Math.max(0, getTotalQuestions() - getAnsweredCount());
    }
}
