package com.nihon.dto;

import com.nihon.validation.NoControlChars;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form DTO nhận câu trả lời của người học gửi lên server để chấm điểm.
 */
@Getter
@Setter
@NoArgsConstructor
public class PracticeAnswerForm {

    @NotNull(message = "Mã từ vựng không được để trống.")
    @Positive(message = "Mã từ vựng không hợp lệ.")
    private Long vocabId;

    @Size(max = 255, message = "Cách đọc Hiragana không được vượt quá 255 ký tự.")
    @NoControlChars
    private String userReading;

    @Size(max = 500, message = "Nghĩa tiếng Việt không được vượt quá 500 ký tự.")
    @NoControlChars
    private String userMeaning;

    @Positive(message = "Mã chủ đề không hợp lệ.")
    private Long topicId;
}
