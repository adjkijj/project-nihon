package com.nihon.dto.verb;

import com.nihon.conjugation.ConjugatedForms;
import com.nihon.entity.Verb;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * DTO chi tiết động từ bao gồm 5 thể cơ bản (từ điển, masu, te, ta, nai)
 * sinh bởi bộ chia động từ VerbConjugator.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerbDetailDto {

    private Long id;
    private Long topicId;
    private String topicName;
    private Integer verbGroup;
    private String meaningVi;
    private String kanjiText;
    private boolean hasKanji;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 5 thể dạng Kana
    private String dictionaryForm;
    private String masuForm;
    private String teForm;
    private String taForm;
    private String naiForm;

    // 5 thể dạng Kanji (nếu có Kanji)
    private String kanjiDictionaryForm;
    private String kanjiMasuForm;
    private String kanjiTeForm;
    private String kanjiTaForm;
    private String kanjiNaiForm;

    public static VerbDetailDto fromEntity(Verb verb, ConjugatedForms forms) {
        if (verb == null) {
            return null;
        }
        return VerbDetailDto.builder()
                .id(verb.getId())
                .topicId(verb.getTopic() != null ? verb.getTopic().getId() : null)
                .topicName(verb.getTopic() != null ? verb.getTopic().getName() : null)
                .verbGroup(verb.getVerbGroup())
                .meaningVi(verb.getMeaningVi())
                .kanjiText(verb.getKanjiText())
                .hasKanji(verb.getKanjiText() != null && !verb.getKanjiText().isBlank())
                .createdAt(verb.getCreatedAt())
                .updatedAt(verb.getUpdatedAt())
                .dictionaryForm(forms != null ? forms.getDictionaryForm() : verb.getDictionaryForm())
                .masuForm(forms != null ? forms.getMasuForm() : verb.getMasuForm())
                .teForm(forms != null ? forms.getTeForm() : null)
                .taForm(forms != null ? forms.getTaForm() : null)
                .naiForm(forms != null ? forms.getNaiForm() : null)
                .kanjiDictionaryForm(forms != null ? forms.getKanjiDictionaryForm() : null)
                .kanjiMasuForm(forms != null ? forms.getKanjiMasuForm() : null)
                .kanjiTeForm(forms != null ? forms.getKanjiTeForm() : null)
                .kanjiTaForm(forms != null ? forms.getKanjiTaForm() : null)
                .kanjiNaiForm(forms != null ? forms.getKanjiNaiForm() : null)
                .build();
    }
}
