package com.nihon.dto;

import com.nihon.entity.Topic;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * DTO chứa thông tin chi tiết một từ vựng trên trang GET /vocabulary/{id},
 * bao gồm danh sách mọi chủ đề chứa từ (gom nhóm theo VocabularyDeduplicationKey)
 * và danh sách các chữ Kanji xuất hiện trong từ (kèm liên kết tới /kanji/{id} nếu có).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VocabularyDetailDto {

    private Long id;
    private String japaneseText;
    private String reading;
    private String romaji;
    private String meaningVi;
    private boolean hasKanji;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder.Default
    private List<Topic> topics = Collections.emptyList();

    @Builder.Default
    private List<Long> allIds = Collections.emptyList();

    @Builder.Default
    private List<KanjiInWordDto> kanjis = Collections.emptyList();

    /**
     * Chuỗi văn bản phục vụ phát âm tiếng Nhật qua Web Speech API.
     */
    public String getSpeechText() {
        return com.nihon.service.SpeechHelper.resolveSpeechText(this.japaneseText, this.reading);
    }

    /**
     * Thông tin một chữ Kanji trích xuất từ japaneseText của từ vựng.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KanjiInWordDto {
        private String character;
        private Long kanjiId;
        private String hanViet;
        private String meaningVi;
    }
}

