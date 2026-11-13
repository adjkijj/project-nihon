package com.nihon.dto.grammar;

import com.nihon.entity.GrammarCategory;
import com.nihon.entity.GrammarTopic;
import com.nihon.validation.NoControlChars;
import com.nihon.validation.VietnameseText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Form DTO để tạo mới hoặc cập nhật chủ đề ngữ pháp (F30).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarTopicForm {

    private Long id;

    @NotBlank(message = "Mã chủ đề ngữ pháp không được để trống.")
    @Size(max = 50, message = "Mã chủ đề không được vượt quá 50 ký tự.")
    @NoControlChars(message = "Mã chủ đề không được chứa ký tự điều khiển.")
    private String code;

    @NotBlank(message = "Tiêu đề ngữ pháp không được để trống.")
    @Size(max = 255, message = "Tiêu đề không được vượt quá 255 ký tự.")
    @NoControlChars(message = "Tiêu đề không được chứa ký tự điều khiển.")
    private String title;

    @NotBlank(message = "Vui lòng chọn phân loại.")
    @Pattern(regexp = "^(GRAMMAR|SUPPLEMENT)$", message = "Phân loại phải là GRAMMAR hoặc SUPPLEMENT.")
    private String category;

    private Long topicId;

    @Size(max = 500, message = "Công thức không được vượt quá 500 ký tự.")
    @NoControlChars(message = "Công thức không được chứa ký tự điều khiển.")
    private String formula;

    @Size(max = 5000, message = "Giải thích không được vượt quá 5000 ký tự.")
    @VietnameseText(allowNewlines = true, allowJapanese = true, message = "Giải thích chứa ký tự không hợp lệ hoặc chứa mã HTML.")
    private String explanation;

    @Size(max = 1000, message = "Ghi chú không được vượt quá 1000 ký tự.")
    @NoControlChars(message = "Ghi chú không được chứa ký tự điều khiển.")
    private String notes;

    @Builder.Default
    private Integer sortOrder = 1;

    public static GrammarTopicForm fromEntity(GrammarTopic topic) {
        if (topic == null) {
            return new GrammarTopicForm();
        }
        return GrammarTopicForm.builder()
                .id(topic.getId())
                .code(topic.getCode())
                .title(topic.getTitle())
                .category(topic.getCategory() != null ? topic.getCategory().name() : GrammarCategory.GRAMMAR.name())
                .topicId(topic.getTopic() != null ? topic.getTopic().getId() : null)
                .formula(topic.getFormula())
                .explanation(topic.getExplanation())
                .notes(topic.getNotes())
                .sortOrder(topic.getSortOrder())
                .build();
    }
}

