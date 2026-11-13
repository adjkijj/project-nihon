package com.nihon.dto.exam;

import com.nihon.entity.ExamSection;
import com.nihon.validation.NoControlChars;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Form DTO để tạo mới hoặc cập nhật Phần thi (F41).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamSectionForm {

    private Long id;

    private Long paperId;

    @NotBlank(message = "Tiêu đề phần thi không được để trống.")
    @Size(max = 255, message = "Tiêu đề phần thi không được vượt quá 255 ký tự.")
    @NoControlChars(message = "Tiêu đề phần thi không được chứa ký tự điều khiển.")
    private String title;

    @Size(max = 1000, message = "Hướng dẫn phần thi không được vượt quá 1000 ký tự.")
    @NoControlChars(message = "Hướng dẫn phần thi không được chứa ký tự điều khiển.")
    private String instructions;

    private int sortOrder;

    public static ExamSectionForm fromEntity(ExamSection section) {
        if (section == null) {
            return null;
        }
        return ExamSectionForm.builder()
                .id(section.getId())
                .paperId(section.getPaper() != null ? section.getPaper().getId() : null)
                .title(section.getTitle())
                .instructions(section.getInstructions())
                .sortOrder(section.getSortOrder())
                .build();
    }
}

