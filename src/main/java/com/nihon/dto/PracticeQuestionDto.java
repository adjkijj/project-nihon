package com.nihon.dto;

import com.nihon.entity.Vocabulary;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO an toàn chỉ chứa thông tin đề bài câu hỏi gửi tới trình duyệt.
 * Tuyệt đối không chứa đáp án chuẩn (cách đọc chuẩn hoặc nghĩa tiếng Việt)
 * nhằm ngăn chặn gian lận xem trước đáp án qua HTML/DOM.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PracticeQuestionDto {

    private Long vocabId;
    private String japaneseText;
    private String topicName;
    private Long topicId;
    private boolean hasKanji;
    private boolean requireReading;
    private boolean readingMissingInSource;

    public static PracticeQuestionDto fromEntity(Vocabulary v, Long currentTopicId) {
        if (v == null) {
            return null;
        }

        boolean hasKanji = v.isHasKanji();
        boolean hasReadingInSource = v.getReading() != null && !v.getReading().isBlank();

        // Nếu từ có chữ Kanji:
        // - Có cách đọc trong nguồn -> bắt buộc nhập cách đọc Hiragana
        // - Thiếu cách đọc trong nguồn -> không tự đoán, đánh dấu chưa đủ dữ liệu để kiểm tra cách đọc
        boolean requireReading = hasKanji && hasReadingInSource;
        boolean readingMissingInSource = hasKanji && !hasReadingInSource;

        return PracticeQuestionDto.builder()
                .vocabId(v.getId())
                .japaneseText(v.getJapaneseText())
                .topicName(v.getTopic() != null ? v.getTopic().getName() : "Tất cả chủ đề")
                .topicId(currentTopicId)
                .hasKanji(hasKanji)
                .requireReading(requireReading)
                .readingMissingInSource(readingMissingInSource)
                .build();
    }
}
