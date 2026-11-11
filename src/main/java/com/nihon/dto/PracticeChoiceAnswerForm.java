package com.nihon.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form DTO nhận câu trả lời trắc nghiệm 4 lựa chọn được gửi từ máy khách.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PracticeChoiceAnswerForm {

    @NotNull(message = "Mã câu hỏi không được để trống.")
    @Positive(message = "Mã câu hỏi phải là số nguyên dương.")
    private Long vocabId;

    @NotNull(message = "Vui lòng chọn một đáp án.")
    @Positive(message = "Mã đáp án lựa chọn phải là số nguyên dương.")
    private Long choiceVocabId;

    /**
     * Hướng câu hỏi: JA_VI hoặc VI_JA.
     */
    private String direction;

    @Positive(message = "Mã chủ đề không hợp lệ.")
    private Long topicId;

    /**
     * Lấy hướng câu hỏi an toàn theo whitelist (mặc định JA_VI).
     */
    public String getSafeDirection() {
        if (direction != null && "VI_JA".equalsIgnoreCase(direction.trim())) {
            return "VI_JA";
        }
        return "JA_VI";
    }
}

