package com.nihon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO đại diện cho một phương án lựa chọn trong câu hỏi trắc nghiệm 4 đáp án.
 * Không chứa thông tin đáp án đúng/sai để chống lộ dữ liệu phía client.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PracticeChoiceOptionDto {

    /**
     * Mã định danh của từ vựng tương ứng với lựa chọn này.
     */
    private Long vocabId;

    /**
     * Nội dung hiển thị chính (Nghĩa tiếng Việt nếu hướng JA_VI, hoặc Tiếng Nhật nếu hướng VI_JA).
     */
    private String displayText;

    /**
     * Phần chữ tiếng Nhật gốc đã tách ngoặc đọc (nếu có, dùng cho hướng VI_JA).
     */
    private String baseJapaneseText;

    /**
     * Cách đọc Hiragana (dùng cho hướng VI_JA để hiển thị ruby hoặc trợ giúp).
     */
    private String reading;

    /**
     * Romaji (nếu có).
     */
    private String romaji;

    /**
     * Văn bản phát âm Web Speech API (dùng cho hướng VI_JA).
     */
    private String speechText;
}

