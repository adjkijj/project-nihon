package com.nihon.dto.timed;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * DTO tổng kết kết quả phiên luyện tập tính giờ sau khi hoàn thành hoặc hết giờ.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimedResultDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sessionId;
    private TimedPracticeType type;
    private String typeName;
    private Long topicId;
    private String topicName;
    private int totalQuestions;
    private int answeredCount;
    private int correctCount;
    private int incorrectCount;
    private int accuracyPercentage;
    private long timeSpentSeconds;
    private String timeSpentFormatted;
    private long totalTimeSeconds;
    private int maxStreak;
    private TimedSessionStatus status;
    private boolean isSpeedPreset;
    private List<TimedAnswerRecord> records;
}
