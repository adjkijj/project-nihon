package com.nihon.dto;

import com.nihon.entity.Vocabulary;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO an toàn cho câu hỏi chế độ kiểm tra tiếng Việt sang tiếng Nhật.
 * Chỉ chứa nghĩa tiếng Việt và thông tin chủ đề.
 * Tuyệt đối không gửi cách viết tiếng Nhật hoặc cách đọc xuống trình duyệt.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PracticeViJaQuestionDto {

    private Long vocabId;
    private String meaningVi;
    private String topicName;
    private Long topicId;
    private boolean hasKanji;

    public static PracticeViJaQuestionDto fromEntity(Vocabulary v, Long currentTopicId) {
        if (v == null) {
            return null;
        }

        return PracticeViJaQuestionDto.builder()
                .vocabId(v.getId())
                .meaningVi(v.getMeaningVi())
                .topicName(v.getTopic() != null ? v.getTopic().getName() : "Tất cả chủ đề")
                .topicId(currentTopicId)
                .hasKanji(v.isHasKanji())
                .build();
    }
}
