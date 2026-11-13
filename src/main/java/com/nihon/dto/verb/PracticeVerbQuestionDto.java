package com.nihon.dto.verb;

import com.nihon.conjugation.TargetForm;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * DTO chứa thông tin câu hỏi luyện chia thể động từ gửi đến người học.
 * Tuyệt đối không chứa đáp án đúng nhằm chống gian lận và rò rỉ mã nguồn phía máy khách.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeVerbQuestionDto {

    private Long verbId;
    private String masuForm;
    private String dictionaryForm;
    private String kanjiText;
    private String meaningVi;
    private int verbGroup;
    private TargetForm targetForm;
    private String prompt;
    private String speechText;
    private String mode; // INPUT hoặc CHOICE

    @Builder.Default
    private List<String> choices = Collections.emptyList();

    private Long topicId;
    private Integer group;
    private boolean retryWrongOnly;
    private int currentStreak;
}
