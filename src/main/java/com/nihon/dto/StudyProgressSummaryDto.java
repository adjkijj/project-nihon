package com.nihon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * DTO tổng hợp tiến độ học tập trong phiên làm việc.
 * Tính toán 100% tại máy chủ, không phụ thuộc vào dữ liệu điểm do client gửi lên.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudyProgressSummaryDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private long totalAnswered;
    private long correctCount;
    private long incorrectCount;
    private int accuracyPercentage;
    private List<PracticeRecordDto> records;
    private List<PracticeRecordDto> incorrectRecords;
    private String currentModeFilter; // "ALL", "JA_VI", "VI_JA"
    private Long currentTopicFilter;  // null = tất cả chủ đề
}
