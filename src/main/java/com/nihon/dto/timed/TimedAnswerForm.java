package com.nihon.dto.timed;

import com.nihon.validation.NoControlChars;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Form gửi câu trả lời cho một câu hỏi trong phiên luyện tập tính giờ.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimedAnswerForm {

    @NotBlank(message = "Mã phiên làm bài không được để trống.")
    private String sessionId;

    @NotNull(message = "Số thứ tự câu hỏi không được để trống.")
    @Positive(message = "Số thứ tự câu hỏi phải là số dương.")
    private Integer questionIndex;

    private Long vocabId;

    private Long kanjiId;

    @Size(max = 255, message = "Cách đọc Hiragana không được vượt quá 255 ký tự.")
    @NoControlChars(message = "Cách đọc không được chứa ký tự điều khiển.")
    private String userReading;

    @Size(max = 500, message = "Nghĩa tiếng Việt không được vượt quá 500 ký tự.")
    @NoControlChars(message = "Nghĩa tiếng Việt không được chứa ký tự điều khiển.")
    private String userMeaning;

    @Size(max = 255, message = "Từ tiếng Nhật không được vượt quá 255 ký tự.")
    @NoControlChars(message = "Từ tiếng Nhật không được chứa ký tự điều khiển.")
    private String userJapanese;

    @Positive(message = "Mã lựa chọn phải là số nguyên dương.")
    private Long choiceVocabId;

    @Size(max = 255, message = "Âm đọc Kanji không được vượt quá 255 ký tự.")
    @NoControlChars(message = "Âm đọc không được chứa ký tự điều khiển.")
    private String kanjiAnswer;

    @Size(max = 500, message = "Câu trả lời không được vượt quá 500 ký tự.")
    @NoControlChars(message = "Câu trả lời không được chứa ký tự điều khiển.")
    private String answer;
}
