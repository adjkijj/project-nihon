package com.nihon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * DTO chứa kết quả chấm điểm chi tiết sau khi người học nộp câu trả lời.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PracticeResultDto {

    private Long vocabId;
    private String japaneseText;
    private String topicName;
    private Long topicId;

    private boolean hasKanji;
    private boolean requireReading;
    private boolean readingMissingInSource;

    private String userReading;
    private String userMeaning;

    /**
     * true: đúng; false: sai; null: không yêu cầu kiểm tra cách đọc (do từ không có Kanji hoặc thiếu trong nguồn).
     */
    private Boolean readingCorrect;

    /**
     * true: đúng; false: sai.
     */
    private boolean meaningCorrect;

    /**
     * Kết quả tổng thể: đúng cả các phần được yêu cầu.
     */
    private boolean overallCorrect;

    private String expectedReading;
    private String expectedMeaning;

    private List<String> acceptedReadings;
    private List<String> acceptedMeanings;

    private String feedbackMessage;
}
