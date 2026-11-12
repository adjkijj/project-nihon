package com.nihon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Một lựa chọn trong bài trắc nghiệm Kanji (F23).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KanjiChoiceOptionDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long kanjiId;
    private String character;
    private String displayText; // VD: "THÔNG - Đi qua, thông suốt"
}

