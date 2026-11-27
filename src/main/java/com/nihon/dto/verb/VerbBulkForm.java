package com.nihon.dto.verb;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form DTO để thêm hàng loạt động từ từ textarea (tối đa 100 dòng).
 * Định dạng mỗi dòng: "ひらがなます,nghĩa"
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerbBulkForm {

    @NotBlank(message = "Dữ liệu hàng loạt không được để trống.")
    private String text;

    @Positive(message = "Chủ đề không hợp lệ.")
    private Long topicId;
}
