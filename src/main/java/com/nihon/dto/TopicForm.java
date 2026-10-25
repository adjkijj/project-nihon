package com.nihon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form DTO để nhận và xác thực dữ liệu tạo/chỉnh sửa chủ đề.
 */
@Getter
@Setter
@NoArgsConstructor
public class TopicForm {

    private Long id;

    @NotBlank(message = "Tên chủ đề không được để trống.")
    @Size(max = 255, message = "Tên chủ đề không được vượt quá 255 ký tự.")
    private String name;

    private Long parentId;

    private boolean kanjiSection;
}
