package com.nihon.dto.timed;

import com.nihon.dto.PracticeChoiceOptionDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Mục câu hỏi đã sinh trong danh sách câu hỏi của một phiên luyện tập tính giờ.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimedQuestionItem implements Serializable {
    private static final long serialVersionUID = 1L;

    private int index;
    private Long vocabId;
    private Long kanjiId;
    private Long verbId;
    private String prompt;
    private String baseJapaneseText;
    private String reading;
    private String meaningVi;
    private String speechText;
    private boolean hasKanji;
    private boolean requireReading;
    private List<PracticeChoiceOptionDto> choices;
}
