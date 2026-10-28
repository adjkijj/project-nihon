package com.nihon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form DTO để nhận và xác thực dữ liệu thêm/sửa từ vựng.
 * Áp dụng giới hạn độ dài và kiểm tra dữ liệu phía server.
 */
@Getter
@Setter
@NoArgsConstructor
public class VocabularyForm {

    private Long id;

    @NotNull(message = "Vui lòng chọn chủ đề hợp lệ.")
    private Long topicId;

    @NotBlank(message = "Tiếng Nhật không được để trống.")
    @Size(max = 255, message = "Tiếng Nhật không được vượt quá 255 ký tự.")
    private String japaneseText;

    @Size(max = 255, message = "Cách đọc Hiragana không được vượt quá 255 ký tự.")
    private String reading;

    @Size(max = 255, message = "Romaji không được vượt quá 255 ký tự.")
    private String romaji;

    @Size(max = 500, message = "Nghĩa tiếng Việt không được vượt quá 500 ký tự.")
    private String meaningVi;
}
