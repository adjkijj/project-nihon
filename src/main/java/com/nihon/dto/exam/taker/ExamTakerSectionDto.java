package com.nihon.dto.exam.taker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Phần thi hiển thị cho thí sinh.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamTakerSectionDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String title;
    private int sortOrder;
    private String instructions;

    @Builder.Default
    private List<ExamTakerQuestionDto> questions = new ArrayList<>();
}
