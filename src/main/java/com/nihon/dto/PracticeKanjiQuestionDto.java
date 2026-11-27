package com.nihon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO chứa thông tin câu hỏi luyện tập Kanji chuyển sang giao diện (F22, F23).
 * Tuyệt đối không chứa đáp án chuẩn để tránh rò rỉ tại mã nguồn HTML.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeKanjiQuestionDto {

    private Long kanjiId;

    /** Ký tự Kanji cho dạng KANJI_TO_READING và KANJI_CHOICE. */
    private String character;

    /** Âm Hán Việt làm đề bài cho dạng MEANING_TO_KANJI. */
    private String promptHanViet;

    /** Nghĩa tiếng Việt làm đề bài cho dạng MEANING_TO_KANJI. */
    private String promptMeaningVi;

    private KanjiPracticeMode mode;
    private Long topicId;
    private String topicName;

    /** Số lượng âm đọc hợp lệ (dùng làm gợi ý số lượng cho dạng KANJI_TO_READING). */
    private int validReadingsCount;

    /** 4 lựa chọn cho dạng KANJI_CHOICE. */
    @Builder.Default
    private List<KanjiChoiceOptionDto> options = new ArrayList<>();
}

