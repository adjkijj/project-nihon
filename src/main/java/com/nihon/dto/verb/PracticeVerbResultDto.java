package com.nihon.dto.verb;

import com.nihon.conjugation.TargetForm;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * DTO chứa kết quả đánh giá câu trả lời chia động từ.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeVerbResultDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long verbId;
    private String prompt;
    private TargetForm targetForm;
    private String userAnswer;
    private String expectedKana;
    private String expectedKanji;
    private boolean correct;
    private String feedbackRule;
    private int verbGroup;
    private String meaningVi;
    private String mode;
    private Long topicId;
    private Integer group;
    private boolean retryWrongOnly;
    private int currentStreak;

    public String getExpectedDisplay() {
        if (expectedKanji != null && !expectedKanji.isBlank() && !expectedKanji.equals(expectedKana)) {
            return expectedKana + " (" + expectedKanji + ")";
        }
        return expectedKana != null ? expectedKana : "";
    }
}
