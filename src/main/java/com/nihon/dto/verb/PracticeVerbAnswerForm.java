package com.nihon.dto.verb;

import com.nihon.conjugation.TargetForm;
import com.nihon.validation.NoControlChars;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Form DTO nhận câu trả lời luyện chia động từ tiếng Nhật.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeVerbAnswerForm {

    private static final Set<String> ALLOWED_TARGET_FORMS = Set.of(
            "TE", "RU", "TA", "NAI", "MASU", "RANDOM"
    );

    @NotNull(message = "Mã động từ không được để trống")
    @Positive(message = "Mã động từ phải là số dương")
    private Long verbId;

    @NotNull(message = "Thể đích không được để trống")
    private String targetForm; // Whitelist: TE, RU, TA, NAI, MASU

    @Size(max = 100, message = "Câu trả lời không được vượt quá 100 ký tự")
    @NoControlChars(message = "Câu trả lời không được chứa ký tự điều khiển")
    private String answer;

    private String mode; // INPUT hoặc CHOICE

    private Integer choiceIndex; // Vị trí phương án trắc nghiệm đã chọn (0..3)

    private Integer group; // Nhóm động từ lọc (1, 2, 3)

    private Long topicId; // Chủ đề lọc

    private boolean retryWrongOnly;

    public TargetForm getResolvedTargetForm() {
        if (targetForm == null || !ALLOWED_TARGET_FORMS.contains(targetForm.trim().toUpperCase())) {
            return TargetForm.TE;
        }
        return TargetForm.fromString(targetForm);
    }

    public boolean isValidTargetForm() {
        if (targetForm == null) {
            return false;
        }
        return ALLOWED_TARGET_FORMS.contains(targetForm.trim().toUpperCase());
    }
}
