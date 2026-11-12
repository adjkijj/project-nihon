package com.nihon.dto.timed;

import com.nihon.dto.PracticeChoiceOptionDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * DTO đại diện cho câu hỏi hiện tại được gửi ra view cho người học.
 * Tuyệt đối không chứa hoặc làm lộ đáp án đúng của câu hỏi hiện tại.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimedQuestionDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sessionId;
    private int questionIndex; // 1-based
    private int totalQuestions;
    private long remainingSeconds;
    private TimedPracticeType type;
    private String prompt;
    private String baseJapaneseText;
    private String speechText;
    private boolean hasKanji;
    private boolean requireReading;
    private List<PracticeChoiceOptionDto> choices;
    private TimedEvaluationMode evaluationMode;
    private int currentStreak;
    private boolean isSpeedPreset;

    /**
     * Kết quả câu hỏi trước đó nếu ở chế độ INSTANT.
     * Là null nếu ở chế độ AT_END hoặc chưa làm câu nào.
     */
    private TimedAnswerRecord lastResult;
}
