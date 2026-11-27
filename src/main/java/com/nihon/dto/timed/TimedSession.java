package com.nihon.dto.timed;

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
 * Phiên luyện tập tính giờ được lưu trữ trong HttpSession (SESSION_TIMED_PRACTICE).
 * Quản lý trạng thái, danh sách câu hỏi, câu trả lời, streak và thời gian giới hạn tại máy chủ.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimedSession implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String SESSION_TIMED_PRACTICE = "SESSION_TIMED_PRACTICE";

    private String id;
    private TimedPracticeType type;
    private Long topicId;
    private String topicName;
    private int questionCount;
    private int durationMinutes;
    private Instant startedAt;
    private Instant deadline;
    private TimedEvaluationMode evaluationMode;
    private boolean isSpeedPreset;
    private int currentIndex;

    @Builder.Default
    private List<TimedQuestionItem> questions = new ArrayList<>();

    @Builder.Default
    private List<TimedAnswerRecord> answers = new ArrayList<>();

    private TimedSessionStatus status;
    private int currentStreak;
    private int maxStreak;

    /**
     * Kiểm tra phiên luyện tập đã hết hạn hay chưa.
     * Cho phép 3 giây ân hạn (grace period) để bù đắp độ trễ mạng phía client.
     */
    public boolean isExpired(Instant now) {
        if (deadline == null) {
            return false;
        }
        return now.isAfter(deadline.plusSeconds(3));
    }

    /**
     * Tính số giây còn lại tính từ thời điểm now đến deadline (không âm).
     */
    public long getRemainingSeconds(Instant now) {
        if (deadline == null) {
            return 0;
        }
        return Math.max(0, Duration.between(now, deadline).toSeconds());
    }

    public boolean isRunning() {
        return status == TimedSessionStatus.RUNNING;
    }

    public boolean isFinished() {
        return status == TimedSessionStatus.FINISHED || status == TimedSessionStatus.EXPIRED;
    }
}
