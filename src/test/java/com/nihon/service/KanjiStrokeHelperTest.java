package com.nihon.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class KanjiStrokeHelperTest {

    @Test
    @DisplayName("F19 - Test 1: Hàm tạo tên file cho 酒 trả về 0916f.svg theo đặc tả prompt")
    void computeStrokeFileName_sakeCharacter_returns0916f() {
        String fileName = KanjiStrokeHelper.computeStrokeFileName("酒");
        assertThat(fileName).isEqualTo("0916f.svg");
    }

    @Test
    @DisplayName("F19 - Test 1: Ký tự ngoài BMP (Supplementary) trả về 5 chữ số thập lục phân")
    void computeStrokeFileName_supplementaryCharacter_returns5HexDigits() {
        // "𠮟" có mã Unicode U+20B9F (134047)
        String characterOutsideBmp = "𠮟";
        assertThat(characterOutsideBmp.codePoints().count()).isEqualTo(1);

        String fileName = KanjiStrokeHelper.computeStrokeFileName(characterOutsideBmp);
        assertThat(fileName).isEqualTo("20b9f.svg");
    }

    @Test
    @DisplayName("F19 - Test 1: Ký tự BMP thông thường trả về 5 chữ số thập lục phân đệm số 0")
    void computeStrokeFileName_normalBmpCharacter_returns5HexDigitsWithLeadingZero() {
        // "一" có mã Unicode U+4E00 -> 04e00.svg
        assertThat(KanjiStrokeHelper.computeStrokeFileName("一")).isEqualTo("04e00.svg");
        // "魚" có mã Unicode U+9B5A -> 09b5a.svg
        assertThat(KanjiStrokeHelper.computeStrokeFileName("魚")).isEqualTo("09b5a.svg");
        // "日" có mã Unicode U+65E5 -> 065e5.svg
        assertThat(KanjiStrokeHelper.computeStrokeFileName("日")).isEqualTo("065e5.svg");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "日本", "abc", "魚（さかな）", "🔥✨"})
    @DisplayName("F19 - Test 1: Chuỗi nhiều ký tự, rỗng hoặc null không có tên file (trả về null)")
    void computeStrokeFileName_invalidInputs_returnsNull(String invalidInput) {
        String fileName = KanjiStrokeHelper.computeStrokeFileName(invalidInput);
        assertThat(fileName).isNull();
    }

    @Test
    @DisplayName("F19 - Test 2: hasStrokeData đúng khi có file trong classpath static/kanjivg/")
    void hasStrokeData_existingFiles_returnsTrue() {
        assertThat(KanjiStrokeHelper.hasStrokeData("0916f.svg")).isTrue();
        assertThat(KanjiStrokeHelper.hasStrokeData("04e00.svg")).isTrue();
        assertThat(KanjiStrokeHelper.hasStrokeData("09b5a.svg")).isTrue();
        assertThat(KanjiStrokeHelper.hasStrokeData("065e5.svg")).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"nonexistent.svg", "00000.svg", "../LICENSE.txt", "0916f.png", "eval.js", "123.svg"})
    @DisplayName("F19 - Test 2: hasStrokeData đúng khi không có file hoặc tên file bất thường")
    void hasStrokeData_nonExistentOrMaliciousFiles_returnsFalse(String file) {
        assertThat(KanjiStrokeHelper.hasStrokeData(file)).isFalse();
    }
}

