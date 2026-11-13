package com.nihon.dto.exam.taker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Đề thi đã được lọc sạch thông tin đáp án và lời giải, an toàn hiển thị cho thí sinh.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamTakerPaperDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String code;
    private String title;
    private String description;
    private int durationMinutes;
    private String category;
    private int totalQuestions;
    private BigDecimal totalPoints;
    private boolean shuffleChoices;

    @Builder.Default
    private List<ExamTakerSectionDto> sections = new ArrayList<>();
}
