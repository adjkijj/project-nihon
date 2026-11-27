package com.nihon.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RomajiConverterTest {

    @Test
    @DisplayName("Chuyển đổi các âm cơ bản: shu -> しゅ, kyo -> きょ, tsu -> つ")
    void testBasicHepburnPatterns() {
        assertThat(RomajiConverter.toHiragana("shu")).isEqualTo("しゅ");
        assertThat(RomajiConverter.toHiragana("kyo")).isEqualTo("きょ");
        assertThat(RomajiConverter.toHiragana("tsu")).isEqualTo("つ");
        assertThat(RomajiConverter.toHiragana("chi")).isEqualTo("ち");
        assertThat(RomajiConverter.toHiragana("shi")).isEqualTo("し");
        assertThat(RomajiConverter.toHiragana("fu")).isEqualTo("ふ");
    }

    @Test
    @DisplayName("Chuyển đổi nguyên âm dài (long vowels: ō, ū, ā, ī, ē, ou)")
    void testLongVowels() {
        // ō -> ou -> おう
        assertThat(RomajiConverter.toHiragana("tōkyō")).isEqualTo("とうきょう");
        assertThat(RomajiConverter.toHiragana("kyū")).isEqualTo("きゅう");
        assertThat(RomajiConverter.toHiragana("ō")).isEqualTo("おう");
        assertThat(RomajiConverter.toHiragana("shū")).isEqualTo("しゅう");
        assertThat(RomajiConverter.toHiragana("gyō")).isEqualTo("ぎょう");
        assertThat(RomajiConverter.toHiragana("kō")).isEqualTo("こう");
        assertThat(RomajiConverter.toHiragana("hō")).isEqualTo("ほう");
        assertThat(RomajiConverter.toHiragana("obāsan")).isEqualTo("おばあさん");
    }

    @Test
    @DisplayName("Chuyển đổi phụ âm kép (sokuon っ)")
    void testSokuon() {
        assertThat(RomajiConverter.toHiragana("kitte")).isEqualTo("きって");
        assertThat(RomajiConverter.toHiragana("gakkou")).isEqualTo("がっこう");
        assertThat(RomajiConverter.toHiragana("matcha")).isEqualTo("まっちゃ");
        assertThat(RomajiConverter.toHiragana("ippon")).isEqualTo("いっぽん");
        assertThat(RomajiConverter.toHiragana("zasshi")).isEqualTo("ざっし");
    }

    @Test
    @DisplayName("Chữ n đứng cuối hoặc trước phụ âm: n -> ん")
    void testSpecialN() {
        assertThat(RomajiConverter.toHiragana("nihon")).isEqualTo("にほん");
        assertThat(RomajiConverter.toHiragana("kanji")).isEqualTo("かんじ");
        assertThat(RomajiConverter.toHiragana("konnichiwa")).isEqualTo("こんにちわ");
        assertThat(RomajiConverter.toHiragana("konnichiha")).isEqualTo("こんにちは");
        assertThat(RomajiConverter.toHiragana("kin'gyo")).isEqualTo("きんぎょ");
    }

    @Test
    @DisplayName("Ký tự lạ, số, ký tự đặc biệt, kana có sẵn và emoji được bảo toàn")
    void testSpecialAndNonRomajiCharacters() {
        // Giữ nguyên ký tự đặc biệt
        assertThat(RomajiConverter.toHiragana("shu-123!")).isEqualTo("しゅ-123!");
        assertThat(RomajiConverter.toHiragana("魚 (sakana)")).isEqualTo("魚 (さかな)");
        assertThat(RomajiConverter.toHiragana("さかな")).isEqualTo("さかな");
        assertThat(RomajiConverter.toHiragana("ギョ")).isEqualTo("ギョ");
        assertThat(RomajiConverter.toHiragana("123 @#$")).isEqualTo("123 @#$");
        assertThat(RomajiConverter.toHiragana("")).isEmpty();
        assertThat(RomajiConverter.toHiragana("   ")).isEmpty();
        assertThat(RomajiConverter.toHiragana(null)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "gyo, ぎょ, ギョ",
            "sakana, さかな, サカナ",
            "mizu, みず, ミズ",
            "niku, にく, ニク",
            "ran, らん, ラン",
            "sui, すい, スイ",
            "cha, ちゃ, チャ",
            "nichi, にち, ニチ"
    })
    @DisplayName("Chuyển đổi 2 chiều Hiragana <-> Katakana phục vụ tìm kiếm On và Kun")
    void testHiraganaKatakanaConversion(String romaji, String expectedHiragana, String expectedKatakana) {
        String hiragana = RomajiConverter.toHiragana(romaji);
        assertThat(hiragana).isEqualTo(expectedHiragana);

        String katakana = RomajiConverter.toKatakana(hiragana);
        assertThat(katakana).isEqualTo(expectedKatakana);

        String backToHiragana = RomajiConverter.katakanaToHiragana(katakana);
        assertThat(backToHiragana).isEqualTo(expectedHiragana);
    }
}

