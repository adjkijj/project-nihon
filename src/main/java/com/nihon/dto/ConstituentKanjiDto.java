package com.nihon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO chứa thông tin chữ Kanji cấu thành một từ vựng kèm âm Hán Việt.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConstituentKanjiDto {

    private String character;
    private String hanViet;
    private Long kanjiId;
    private boolean existsInKanjiEntry;
    private String strokeFileName;
    private Boolean hasStrokeData;

    public String getDisplayHanViet() {
        if (hanViet != null && !hanViet.isBlank()) {
            return hanViet;
        }
        return "-";
    }

    public String getStrokeFileName() {
        if (strokeFileName != null && !strokeFileName.isBlank()) {
            return strokeFileName;
        }
        return com.nihon.service.KanjiStrokeHelper.computeStrokeFileName(this.character);
    }

    public boolean isHasStrokeData() {
        if (hasStrokeData != null) {
            return hasStrokeData;
        }
        return com.nihon.service.KanjiStrokeHelper.hasStrokeData(getStrokeFileName());
    }
}

