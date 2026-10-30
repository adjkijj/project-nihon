package com.nihon.dto;

import com.nihon.entity.KanjiEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * DTO chứa thông tin hiển thị Kanji, xử lý danh sách nhiều cách đọc On/Kun và tình trạng thiếu dữ liệu.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KanjiDetailDto {

    private Long id;
    private String character;
    private String hanViet;
    private String meaningVi;
    private String onyomi;
    private String kunyomi;
    private Long topicId;
    private String topicName;
    private String parentTopicName;
    private Integer sourceLine;

    @Builder.Default
    private List<KanjiWordSummaryDto> words = new ArrayList<>();

    public static KanjiDetailDto fromEntity(KanjiEntry entity) {
        if (entity == null) {
            return null;
        }
        String parentName = null;
        if (entity.getTopic() != null && entity.getTopic().getParent() != null) {
            parentName = entity.getTopic().getParent().getName();
        }

        return KanjiDetailDto.builder()
                .id(entity.getId())
                .character(entity.getCharacter())
                .hanViet(entity.getHanViet())
                .meaningVi(entity.getMeaningVi())
                .onyomi(entity.getOnyomi())
                .kunyomi(entity.getKunyomi())
                .topicId(entity.getTopic() != null ? entity.getTopic().getId() : null)
                .topicName(entity.getTopic() != null ? entity.getTopic().getName() : null)
                .parentTopicName(parentName)
                .sourceLine(entity.getSourceLine())
                .words(new ArrayList<>())
                .build();
    }

    /**
     * Tách danh sách nhiều cách đọc On theo dấu phẩy tiếng Nhật hoặc phương Tây.
     * Trả về danh sách rỗng nếu nguồn không có hoặc ghi "-".
     */
    public List<String> getOnyomiList() {
        if (isOnyomiMissing()) {
            return Collections.emptyList();
        }
        return Arrays.stream(onyomi.split("[、,]"))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Tách danh sách nhiều cách đọc Kun theo dấu phẩy tiếng Nhật hoặc phương Tây.
     * Trả về danh sách rỗng nếu nguồn không có hoặc ghi "-".
     */
    public List<String> getKunyomiList() {
        if (isKunyomiMissing()) {
            return Collections.emptyList();
        }
        return Arrays.stream(kunyomi.split("[、,]"))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Kiểm tra xem âm Kun có bị thiếu theo dữ liệu nguồn hay không (null, trống, hoặc ghi rõ "-").
     */
    public boolean isKunyomiMissing() {
        return kunyomi == null || kunyomi.isBlank() || "-".equals(kunyomi.strip());
    }

    /**
     * Kiểm tra xem âm On có bị thiếu theo dữ liệu nguồn hay không (null, trống, hoặc ghi rõ "-").
     */
    public boolean isOnyomiMissing() {
        return onyomi == null || onyomi.isBlank() || "-".equals(onyomi.strip());
    }
}
