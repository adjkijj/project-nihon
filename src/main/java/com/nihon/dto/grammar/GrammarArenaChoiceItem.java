package com.nihon.dto.grammar;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Lựa chọn trắc nghiệm gửi ra client cho câu hỏi trong đấu trường (không lộ cờ đúng/sai).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarArenaChoiceItem implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long choiceId;
    private int sortOrder;
    private String text;
}

