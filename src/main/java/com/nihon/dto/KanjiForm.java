package com.nihon.dto;

import com.nihon.validation.HanVietText;
import com.nihon.validation.KanaReading;
import com.nihon.validation.KanjiLimits;
import com.nihon.validation.OnyomiReading;
import com.nihon.validation.SingleKanji;
import com.nihon.validation.VietnameseText;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form DTO để nhận và xác thực dữ liệu thêm/sửa Kanji.
 * Sử dụng các hằng số giới hạn độ dài từ {@link KanjiLimits}.
 */
@Getter
@Setter
@NoArgsConstructor
public class KanjiForm {

    /**
     * ID của Kanji (không cho phép bind từ client qua @InitBinder).
     */
    private Long id;

    @NotNull(message = "Vui lòng chọn chủ đề hợp lệ.")
    @Positive(message = "Vui lòng chọn chủ đề hợp lệ.")
    private Long topicId;

    @SingleKanji
    private String character;

    @Size(max = KanjiLimits.MAX_HAN_VIET_LENGTH, message = "Âm Hán Việt không được vượt quá 255 ký tự.")
    @HanVietText
    private String hanViet;

    @Size(max = KanjiLimits.MAX_KANJI_MEANING_LENGTH, message = "Ý nghĩa tiếng Việt không được vượt quá 255 ký tự.")
    @VietnameseText
    private String meaningVi;

    @Size(max = KanjiLimits.MAX_ONYOMI_LENGTH, message = "Âm On không được vượt quá 255 ký tự.")
    @OnyomiReading
    private String onyomi;

    @Size(max = KanjiLimits.MAX_KUNYOMI_LENGTH, message = "Âm Kun không được vượt quá 255 ký tự.")
    @KanaReading
    private String kunyomi;
}

