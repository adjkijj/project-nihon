package com.nihon.dto.exam.taker;

import com.nihon.entity.ExamQuestionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Câu hỏi hiển thị cho thí sinh trong phòng thi.
 * TUYỆT ĐỐI KHÔNG chứa explanation, correct hay answerKeys!
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamTakerQuestionDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long sectionId;
    private int questionIndex; // Thứ tự tổng thể từ 1 đến N
    private int sortOrder;
    private ExamQuestionType type;
    private String promptJa;
    private String promptVi;
    private BigDecimal points;

    @Builder.Default
    private List<ExamTakerChoiceDto> choices = new ArrayList<>();
}
