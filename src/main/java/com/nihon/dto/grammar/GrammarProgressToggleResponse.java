package com.nihon.dto.grammar;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * DTO phản hồi sau khi đánh dấu hoặc bỏ đánh dấu 'Đã học' (F30).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarProgressToggleResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long grammarId;
    private boolean learned;
    private long totalLearned;
    private long totalTopics;
    private int progressPercent;
    private String message;
}

