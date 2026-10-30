package com.nihon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO tóm tắt từ vựng cần nhớ của một ký tự Kanji (hiển thị trên thẻ Kanji).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KanjiWordSummaryDto {
    private String japaneseText;
    private String reading;
    private String romaji;
    private String meaningVi;
}
