package com.nihon.dto.timed;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Bản ghi kết quả trả lời của một câu hỏi trong phiên luyện tập tính giờ.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimedAnswerRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private int questionIndex;
    private Long vocabId;
    private Long kanjiId;
    private Long verbId;
    private String prompt;
    private String userAnswer;
    private String expectedAnswer;
    private boolean correct;
    private String feedbackMessage;
    private Instant answeredAt;
}
