package com.nihon.dto;

import com.nihon.entity.KanjiEntry;
import com.nihon.entity.Topic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KanjiDetailDtoTest {

    @Test
    @DisplayName("Chuyển đổi từ Entity sang DTO đầy đủ thông tin")
    void testFromEntity() {
        Topic parent = new Topic();
        parent.setId(10L);
        parent.setName("13. Kanji");

        Topic topic = new Topic();
        topic.setId(11L);
        topic.setName("Kanji Lesson 5");
        topic.setParent(parent);

        KanjiEntry entity = new KanjiEntry();
        entity.setId(1L);
        entity.setCharacter("魚");
        entity.setHanViet("NGƯ");
        entity.setMeaningVi("Cá");
        entity.setOnyomi("ギョ");
        entity.setKunyomi("さかな");
        entity.setTopic(topic);
        entity.setSourceLine(1244);

        KanjiDetailDto dto = KanjiDetailDto.fromEntity(entity);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getCharacter()).isEqualTo("魚");
        assertThat(dto.getHanViet()).isEqualTo("NGƯ");
        assertThat(dto.getMeaningVi()).isEqualTo("Cá");
        assertThat(dto.getTopicName()).isEqualTo("Kanji Lesson 5");
        assertThat(dto.getParentTopicName()).isEqualTo("13. Kanji");
        assertThat(dto.getSourceLine()).isEqualTo(1244);
        assertThat(dto.isOnyomiMissing()).isFalse();
        assertThat(dto.isKunyomiMissing()).isFalse();
        assertThat(dto.getOnyomiList()).containsExactly("ギョ");
        assertThat(dto.getKunyomiList()).containsExactly("さかな");
    }

    @Test
    @DisplayName("Xử lý đúng cấu trúc khi Kanji có nhiều cách đọc On và Kun")
    void testMultipleReadings() {
        KanjiDetailDto dto = KanjiDetailDto.builder()
                .character("小")
                .hanViet("TIỂU")
                .onyomi("ショウ")
                .kunyomi("ちい-さい、こ、お")
                .build();

        assertThat(dto.getOnyomiList()).containsExactly("ショウ");
        assertThat(dto.getKunyomiList()).containsExactly("ちい-さい", "こ", "お");
        assertThat(dto.isKunyomiMissing()).isFalse();
    }

    @Test
    @DisplayName("Xử lý nhiều âm On phân tách bằng dấu phẩy tiếng Nhật")
    void testMultipleOnyomi() {
        KanjiDetailDto dto = KanjiDetailDto.builder()
                .character("分")
                .hanViet("PHÂN")
                .onyomi("ふん、ぶん、ぶ")
                .kunyomi("わ-かります、わ-けます")
                .build();

        assertThat(dto.getOnyomiList()).containsExactly("ふん", "ぶん", "ぶ");
        assertThat(dto.getKunyomiList()).containsExactly("わ-かります", "わ-けます");
    }

    @Test
    @DisplayName("Xử lý dữ liệu thiếu: Nguồn ghi âm Kun là '-' (không có âm Kun)")
    void testMissingKunyomiWithHyphen() {
        KanjiDetailDto dto = KanjiDetailDto.builder()
                .character("校")
                .hanViet("GIÁO, HIỆU")
                .onyomi("こう、きょう")
                .kunyomi("-")
                .build();

        assertThat(dto.isKunyomiMissing()).isTrue();
        assertThat(dto.getKunyomiList()).isEmpty();
        assertThat(dto.isOnyomiMissing()).isFalse();
        assertThat(dto.getOnyomiList()).containsExactly("こう", "きょう");
    }

    @Test
    @DisplayName("Xử lý dữ liệu thiếu: Âm On hoặc Kun là null hoặc chuỗi trắng")
    void testMissingReadingsNullOrBlank() {
        KanjiDetailDto dtoNull = KanjiDetailDto.builder()
                .character("Test")
                .onyomi(null)
                .kunyomi("   ")
                .build();

        assertThat(dtoNull.isOnyomiMissing()).isTrue();
        assertThat(dtoNull.isKunyomiMissing()).isTrue();
        assertThat(dtoNull.getOnyomiList()).isEmpty();
        assertThat(dtoNull.getKunyomiList()).isEmpty();
    }

    @Test
    @DisplayName("Bảo toàn dấu nối okurigana '-' và '・' nguyên bản từ file dữ liệu")
    void testPreserveOkuriganaDelimiters() {
        KanjiDetailDto dto = KanjiDetailDto.builder()
                .character("食")
                .kunyomi("た・べる、く・う")
                .build();

        List<String> kunList = dto.getKunyomiList();
        assertThat(kunList).containsExactly("た・べる", "く・う");
        assertThat(kunList.get(0)).isEqualTo("た・べる");
        assertThat(kunList.get(1)).isEqualTo("く・う");
    }
}
