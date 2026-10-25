package com.nihon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * DTO chứa kết quả chấm điểm cho câu hỏi chế độ Việt → Nhật.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PracticeViJaResultDto {

    private Long vocabId;
    private String meaningVi;
    private String userJapanese;

    private boolean correct;

    private String expectedJapanese;
    private String expectedReading;
    private List<String> acceptedVariants;

    private String topicName;
    private Long topicId;

    private String feedbackMessage;
}
