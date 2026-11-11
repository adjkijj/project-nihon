package com.nihon.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KanjiExtractorTest {

    @Test
    @DisplayName("Tách Kanji thông thường: '日本酒（にほんしゅ）' cho ['日', '本', '酒']")
    void testExtractKanji_regularString() {
        List<String> result = KanjiExtractor.extractKanji("日本酒（にほんしゅ）");
        assertThat(result).containsExactly("日", "本", "酒");
    }

    @Test
    @DisplayName("Chuỗi chỉ có Hiragana/Katakana: 'あいう' cho danh sách rỗng")
    void testExtractKanji_kanaOnlyReturnsEmpty() {
        List<String> result = KanjiExtractor.extractKanji("あいう");
        assertThat(result).isEmpty();

        List<String> katakanaResult = KanjiExtractor.extractKanji("コーヒー");
        assertThat(katakanaResult).isEmpty();
    }

    @Test
    @DisplayName("Chuỗi có Kanji ngoài BMP (Supplementary Ideograph): '𠮟る' cho ['𠮟']")
    void testExtractKanji_outsideBmpKanji() {
        // "𠮟" có code point U+20B9F (134047), nằm ngoài Basic Multilingual Plane
        List<String> result = KanjiExtractor.extractKanji("𠮟る");
        assertThat(result).containsExactly("𠮟");
    }

    @Test
    @DisplayName("Chuỗi null hoặc rỗng: trả về danh sách rỗng")
    void testExtractKanji_nullAndEmptyString() {
        assertThat(KanjiExtractor.extractKanji(null)).isEmpty();
        assertThat(KanjiExtractor.extractKanji("")).isEmpty();
        assertThat(KanjiExtractor.extractKanji("   ")).isEmpty();
    }

    @Test
    @DisplayName("Loại bỏ trùng lặp và bảo toàn thứ tự xuất hiện")
    void testExtractKanji_deduplicatesAndPreservesOrder() {
        // "日曜日" có 2 chữ "日", chỉ lấy 1 chữ "日" ở vị trí đầu
        List<String> result = KanjiExtractor.extractKanji("日曜日");
        assertThat(result).containsExactly("日", "曜");
    }

    @Test
    @DisplayName("Escape ký tự đặc biệt của LIKE: %, _ và \\")
    void testEscapeLikePattern() {
        assertThat(KanjiExtractor.escapeLikePattern("100%")).isEqualTo("100\\%");
        assertThat(KanjiExtractor.escapeLikePattern("a_b")).isEqualTo("a\\_b");
        assertThat(KanjiExtractor.escapeLikePattern("path\\file")).isEqualTo("path\\\\file");
        assertThat(KanjiExtractor.escapeLikePattern("%_\\")).isEqualTo("\\%\\_\\\\");
        assertThat(KanjiExtractor.escapeLikePattern(null)).isEqualTo("");
    }
}

