package com.nihon.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form DTO nhận câu trả lời tiếng Nhật của người học trong chế độ Việt → Nhật.
 */
@Getter
@Setter
@NoArgsConstructor
public class PracticeViJaAnswerForm {

    @NotNull(message = "Mã từ vựng không được để trống.")
    private Long vocabId;

    @Size(max = 255, message = "Từ tiếng Nhật không được vượt quá 255 ký tự.")
    private String userJapanese;

    private Long topicId;
}
