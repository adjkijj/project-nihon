package com.nihon.dto.exam.taker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Lựa chọn hiển thị cho thí sinh.
 * TUYỆT ĐỐI KHÔNG chứa cờ correct.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamTakerChoiceDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private int sortOrder;
    private String text;
}
