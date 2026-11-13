package com.nihon.dto.exam;

import com.nihon.entity.ExamCategory;
import com.nihon.entity.ExamPaper;
import com.nihon.validation.NoControlChars;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Form DTO để tạo mới hoặc cập nhật thông tin Đề thi (F41).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamPaperForm {

    private Long id;

    @NotBlank(message = "Mã đề thi không được để trống.")
    @Size(max = 50, message = "Mã đề thi không được vượt quá 50 ký tự.")
    @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "Mã đề thi chỉ được chứa chữ cái, số, dấu gạch nối (-), gạch dưới (_) và dấu chấm (.).")
    @NoControlChars(message = "Mã đề thi không được chứa ký tự điều khiển.")
    private String code;

    @NotBlank(message = "Tên đề thi không được để trống.")
    @Size(max = 255, message = "Tên đề thi không được vượt quá 255 ký tự.")
    @NoControlChars(message = "Tên đề thi không được chứa ký tự điều khiển.")
    private String title;

    @Size(max = 1000, message = "Mô tả đề thi không được vượt quá 1000 ký tự.")
    @NoControlChars(message = "Mô tả đề thi không được chứa ký tự điều khiển.")
    private String description;

    @NotNull(message = "Thời lượng đề thi không được để trống.")
    @Min(value = 1, message = "Thời lượng làm bài phải từ 1 đến 300 phút.")
    @Max(value = 300, message = "Thời lượng làm bài phải từ 1 đến 300 phút.")
    private Integer durationMinutes;

    @NotBlank(message = "Vui lòng chọn loại đề thi.")
    private String category;

    @Positive(message = "Chủ đề liên kết không hợp lệ.")
    private Long topicId;

    private boolean published;

    @Builder.Default
    private boolean shuffleChoices = true;

    public static ExamPaperForm fromEntity(ExamPaper paper) {
        if (paper == null) {
            return null;
        }
        return ExamPaperForm.builder()
                .id(paper.getId())
                .code(paper.getCode())
                .title(paper.getTitle())
                .description(paper.getDescription())
                .durationMinutes(paper.getDurationMinutes())
                .category(paper.getCategory() != null ? paper.getCategory().name() : ExamCategory.REVIEW.name())
                .topicId(paper.getTopic() != null ? paper.getTopic().getId() : null)
                .published(paper.isPublished())
                .shuffleChoices(paper.isShuffleChoices())
                .build();
    }
}

