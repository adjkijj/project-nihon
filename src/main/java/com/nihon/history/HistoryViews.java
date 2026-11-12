package com.nihon.history;

import java.time.LocalDateTime;
import java.util.List;

/** DTO hiển thị cho trang /history. */
public final class HistoryViews {

    private HistoryViews() {
    }

    public record SessionView(Long id, String mode, String modeLabel, Long topicId, int totalQuestions,
                              int correctCount, int accuracyPercent, int durationSeconds,
                              LocalDateTime completedAt) {
    }

    public record Summary(long sessionCount, long totalQuestions, long correctCount, int accuracyPercent,
                          long studySeconds, long studyMinutes) {
    }

    /** Một cột biểu đồ theo tuần (weekStart = thứ Hai). */
    public record WeekBar(String label, int accuracyPercent, int totalQuestions) {
    }

    public record PageResult(List<SessionView> sessions, int page, int totalPages, long totalElements) {
    }
}