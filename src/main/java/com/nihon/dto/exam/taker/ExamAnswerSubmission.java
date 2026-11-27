package com.nihon.dto.exam.taker;

import com.nihon.validation.NoControlChars;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Form gửi câu trả lời tự động hoặc đánh dấu xem lại qua AJAX (F48).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamAnswerSubmission implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotNull(message = "Mã câu hỏi không được để trống.")
    @Positive(message = "Mã câu hỏi không hợp lệ.")
    private Long questionId;

    @Size(max = 1000, message = "Câu trả lời không được vượt quá 1000 ký tự.")
    @NoControlChars(message = "Câu trả lời không được chứa ký tự điều khiển.")
    private String answer;

    private Boolean markedForReview;
}
