package com.nihon.dto.verb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * DTO chứa kết quả phân tích và chia thể tại Phòng biến đổi động từ (/verbs/lab).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerbLabResultDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private String input;
    private String selectedGroup; // AUTO, 1, 2, 3
    private Integer inferredGroup;
    private String dictionaryForm;
    private String masuForm;
    private String kanjiText;

    private String teForm;
    private String taForm;
    private String naiForm;

    private String kanjiDictionaryForm;
    private String kanjiMasuForm;
    private String kanjiTeForm;
    private String kanjiTaForm;
    private String kanjiNaiForm;

    private boolean ambiguous;
    private String errorMessage;
    private String explanation;
    private boolean hasResult;

    public static VerbLabResultDto error(String input, String selectedGroup, String errorMessage) {
        return VerbLabResultDto.builder()
                .input(input)
                .selectedGroup(selectedGroup)
                .errorMessage(errorMessage)
                .hasResult(false)
                .build();
    }

    public static VerbLabResultDto ambiguous(String input, String selectedGroup, String explanation) {
        return VerbLabResultDto.builder()
                .input(input)
                .selectedGroup(selectedGroup)
                .ambiguous(true)
                .explanation(explanation)
                .hasResult(false)
                .build();
    }
}
