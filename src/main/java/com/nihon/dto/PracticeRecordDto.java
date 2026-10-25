package com.nihon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * DTO đại diện cho một bản ghi làm bài trong phiên học tập.
 * Tuyệt đối không chứa mật khẩu hay thông tin định danh cá nhân nhạy cảm.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeRecordDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private Long vocabId;
    private Long topicId;
    private String topicName;
    private String mode; // "JA_VI" (Nhật -> Việt) hoặc "VI_JA" (Việt -> Nhật)
    private String questionPrompt; // Từ tiếng Nhật hoặc nghĩa tiếng Việt đề bài
    private String userAnswer;
    private String expectedAnswer;
    private boolean correct;
    private String feedbackMessage;
    private LocalDateTime timestamp;
}
