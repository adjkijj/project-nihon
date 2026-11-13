package com.nihon.dto.exam.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO điểm số theo từng phần thi (F40 - F45).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamSectionResultDto {
    private Long sectionId;
    private String title;
    private int sortOrder;
    private BigDecimal pointsEarned;
    private BigDecimal maxPoints;
    private double percentage;
}

