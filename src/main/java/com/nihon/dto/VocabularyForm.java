package com.nihon.dto;

import com.nihon.validation.JapaneseText;
import com.nihon.validation.KanaReading;
import com.nihon.validation.NoControlChars;
import com.nihon.validation.RomajiText;
import com.nihon.validation.VietnameseText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

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
    @JapaneseText
    @NoControlChars
    private String japaneseText;

    @Size(max = 255, message = "Cách đọc Hiragana không được vượt quá 255 ký tự.")
    @KanaReading
    private String reading;

    @Size(max = 255, message = "Romaji không được vượt quá 255 ký tự.")
    @RomajiText
    private String romaji;

    @Size(max = 500, message = "Nghĩa tiếng Việt không được vượt quá 500 ký tự.")
    @VietnameseText
    private String meaningVi;

    /**
     * Danh sách ID của nhóm từ gộp (tùy chọn). Server sẽ tự tính lại nhóm từ ID đại diện
     * và đối chiếu với danh sách này, bỏ qua mọi ID không thuộc nhóm.
     */
    private List<Long> allIds;

    /**
     * Phạm vi thao tác (TOPIC hoặc ALL, tùy chọn).
     */
    private String scope;
}
