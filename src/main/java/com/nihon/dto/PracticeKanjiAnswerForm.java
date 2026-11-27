package com.nihon.dto;

import com.nihon.validation.NoControlChars;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Form DTO nhận câu trả lời luyện tập Kanji (F22, F23).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeKanjiAnswerForm {

    @NotNull(message = "Mã Kanji không được để trống")
    @Positive(message = "Mã Kanji phải là số dương")
    private Long kanjiId;

    private String mode;

    @Positive(message = "Mã chủ đề phải là số dương")
    private Long topicId;

    @Size(max = 255, message = "Câu trả lời không được vượt quá 255 ký tự")
    @NoControlChars(message = "Câu trả lời không được chứa ký tự điều khiển")
    private String answer;

    private Long choiceKanjiId;

    private boolean retryWrongOnly;

    public KanjiPracticeMode getResolvedMode() {
        return KanjiPracticeMode.fromString(this.mode);
    }
}

