package com.nihon.dto.verb;

import com.nihon.validation.JapaneseText;
import com.nihon.validation.KanaReading;
import com.nihon.validation.VietnameseText;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form DTO để nhận và xác thực dữ liệu thêm/sửa Động từ.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerbForm {

    private Long id;

    @Positive(message = "Chủ đề không hợp lệ.")
    private Long topicId;

    @NotBlank(message = "Thể ます không được để trống.")
    @Size(max = 100, message = "Thể ます không được vượt quá 100 ký tự.")
    @KanaReading
    @Pattern(regexp = ".*ます$", message = "Thể ます phải kết thúc bằng 'ます'.")
    private String masuForm;

    @NotBlank(message = "Thể từ điển không được để trống.")
    @Size(max = 100, message = "Thể từ điển không được vượt quá 100 ký tự.")
    @KanaReading
    private String dictionaryForm;

    @Size(max = 100, message = "Chữ Hán không được vượt quá 100 ký tự.")
    @JapaneseText
    private String kanjiText;

    @NotNull(message = "Vui lòng chọn nhóm động từ.")
    @Min(value = 1, message = "Nhóm động từ phải từ 1 đến 3.")
    @Max(value = 3, message = "Nhóm động từ phải từ 1 đến 3.")
    private Integer verbGroup;

    @NotBlank(message = "Nghĩa tiếng Việt không được để trống.")
    @Size(max = 255, message = "Nghĩa tiếng Việt không được vượt quá 255 ký tự.")
    @VietnameseText
    private String meaningVi;
}
