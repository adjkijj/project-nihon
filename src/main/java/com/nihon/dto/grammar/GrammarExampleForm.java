package com.nihon.dto.grammar;

import com.nihon.entity.GrammarExample;
import com.nihon.validation.JapaneseText;
import com.nihon.validation.KanaReading;
import com.nihon.validation.NoControlChars;
import com.nihon.validation.VietnameseText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Form DTO để thêm mới hoặc sửa câu ví dụ ngữ pháp (F30).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarExampleForm {

    private Long id;

    @Builder.Default
    private Integer sortOrder = 1;

    @NotBlank(message = "Câu ví dụ tiếng Nhật không được để trống.")
    @Size(max = 500, message = "Câu ví dụ tiếng Nhật không được vượt quá 500 ký tự.")
    @JapaneseText(message = "Câu ví dụ tiếng Nhật phải chứa ít nhất một ký tự tiếng Nhật hợp lệ.")
    private String japanese;

    @Size(max = 500, message = "Cách đọc không được vượt quá 500 ký tự.")
    @KanaReading(message = "Cách đọc chỉ được chứa ký tự Kana hợp lệ.")
    private String reading;

    @NotBlank(message = "Nghĩa tiếng Việt không được để trống.")
    @Size(max = 500, message = "Nghĩa tiếng Việt không được vượt quá 500 ký tự.")
    @NoControlChars(message = "Nghĩa tiếng Việt không được chứa ký tự điều khiển.")
    @VietnameseText(message = "Nghĩa tiếng Việt chứa ký tự không hợp lệ hoặc chứa mã HTML.")
    private String meaningVi;

    public static GrammarExampleForm fromEntity(GrammarExample example) {
        if (example == null) {
            return new GrammarExampleForm();
        }
        return GrammarExampleForm.builder()
                .id(example.getId())
                .sortOrder(example.getSortOrder())
                .japanese(example.getJapanese())
                .reading(example.getReading())
                .meaningVi(example.getMeaningVi())
                .build();
    }
}

