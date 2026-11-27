package com.nihon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO đại diện cho câu hỏi trắc nghiệm 4 lựa chọn gửi ra giao diện.
 * Trang câu hỏi chỉ nhận danh sách các lựa chọn đã xáo trộn ngẫu nhiên và
 * tuyệt đối không chứa dấu hiệu nhận biết đáp án đúng.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PracticeChoiceQuestionDto {

    /**
     * Mã định danh từ vựng của câu hỏi hiện tại.
     */
    private Long vocabId;

    /**
     * Nội dung đề bài:
     * - Với JA_VI: Tiếng Nhật (kèm reading nếu có).
     * - Với VI_JA: Nghĩa tiếng Việt.
     */
    private String questionPrompt;

    /**
     * Tiếng Nhật gốc đã tách ngoặc đọc (phục vụ render ruby nếu JA_VI).
     */
    private String baseJapaneseText;

    /**
     * Cách đọc Hiragana của câu hỏi (dùng render ruby ở JA_VI).
     */
    private String reading;

    /**
     * Văn bản phát âm Web Speech API của câu hỏi (ở JA_VI).
     */
    private String speechText;

    /**
     * Hướng câu hỏi: JA_VI hoặc VI_JA.
     */
    private String direction;

    /**
     * Mã chủ đề đang lọc (nếu có).
     */
    private Long topicId;

    /**
     * Tên chủ đề hiển thị.
     */
    private String topicName;

    /**
     * Danh sách 4 lựa chọn ngẫu nhiên (1 đúng + 3 nhiễu).
     */
    @Builder.Default
    private List<PracticeChoiceOptionDto> options = new ArrayList<>();
}

