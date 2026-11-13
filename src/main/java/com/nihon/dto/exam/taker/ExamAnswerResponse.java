package com.nihon.dto.exam.taker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Phản hồi AJAX khi lưu câu trả lời (F48).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamAnswerResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private String status; // "OK", "ERROR", "EXPIRED"
    private Long questionId;
    private String message;
    private long remainingSeconds;
    private int answeredCount;
}
