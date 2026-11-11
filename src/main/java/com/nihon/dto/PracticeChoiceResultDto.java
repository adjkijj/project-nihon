package com.nihon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO chứa kết quả chấm điểm câu hỏi trắc nghiệm phía máy chủ.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PracticeChoiceResultDto {

    private Long vocabId;
    private Long selectedVocabId;
    private boolean correct;
    private String direction;
    private Long topicId;
    private String topicName;

    private String questionPrompt;
    private String selectedAnswerText;
    private String correctAnswerText;

    private String correctJapaneseText;
    private String baseJapaneseText;
    private String correctReading;
    private String correctMeaningVi;
    private String correctRomaji;
    private String speechText;

    private String feedbackMessage;
}

