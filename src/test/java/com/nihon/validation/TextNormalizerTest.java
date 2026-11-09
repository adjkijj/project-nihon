package com.nihon.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.text.Normalizer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm thử đơn vị cho {@link TextNormalizer}.
 */
class TextNormalizerTest {

    @Test
    @DisplayName("normalize(null) trả về null")
    void normalize_null_returnsNull() {
        assertThat(TextNormalizer.normalize(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "\t\r\n",
            "\u3000\u3000",
            "\u200B\u200C\u200D\u2060\uFEFF",
            " \u3000 \u200B \uFEFF "
    })
    @DisplayName("normalize chuỗi trống, chỉ khoảng trắng (ASCII/U+3000) hoặc chỉ zero-width trả về null")
    void normalize_blankOrZeroWidthOnly_returnsNull(String input) {
        assertThat(TextNormalizer.normalize(input)).isNull();
    }

    @Test
    @DisplayName("NFC của chữ có dấu tiếng Việt dạng tổ hợp (NFD) và dạng dựng sẵn (NFC) cho cùng kết quả")
    void normalize_vietnameseComposedAndDecomposed_produceSameResult() {
        String composed = "Tiếng Việt - Học từ vựng tiếng Nhật mỗi ngày";
        String decomposed = Normalizer.normalize(composed, Normalizer.Form.NFD);

        // Khẳng định hai chuỗi đầu vào khác nhau ở mức mảng char trước khi chuẩn hóa
        assertThat(decomposed).isNotEqualTo(composed);

        String normalizedFromComposed = TextNormalizer.normalize(composed);
        String normalizedFromDecomposed = TextNormalizer.normalize(decomposed);

        assertThat(normalizedFromDecomposed)
                .isNotNull()
                .isEqualTo(normalizedFromComposed)
                .isEqualTo("Tiếng Việt - Học từ vựng tiếng Nhật mỗi ngày");
    }

    @Test
    @DisplayName("normalize cắt khoảng trắng đầu cuối (kể cả U+3000), gộp khoảng trắng liên tiếp và loại bỏ zero-width")
    void normalize_stripsCollapseSpacesAndRemovesZeroWidth() {
        String raw = "\u3000\uFEFF  私（わたし）\u200B   \u3000  日本語\u200C\u200D\u2060  \u3000";
        assertThat(TextNormalizer.normalize(raw)).isEqualTo("私（わたし） 日本語");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "a\u0000b",
            "a\u0007b",
            "a\u0008b",
            "a\tb",
            "a\nb",
            "a\rb",
            "a\u001Bb",
            "a\u007Fb",
            "a\u009Fb"
    })
    @DisplayName("containsControlChars(String) trả về true khi có ký tự điều khiển hoặc dấu xuống dòng")
    void containsControlChars_withControlOrNewline_returnsTrue(String input) {
        assertThat(TextNormalizer.containsControlChars(input)).isTrue();
    }

    @Test
    @DisplayName("containsControlChars hỗ trợ cho phép dấu xuống dòng khi allowNewlines = true")
    void containsControlChars_allowNewlines() {
        assertThat(TextNormalizer.containsControlChars("Dòng 1\nDòng 2\r\nDòng 3", true)).isFalse();
        assertThat(TextNormalizer.containsControlChars("Dòng 1\nDòng 2\u0007", true)).isTrue();
        assertThat(TextNormalizer.containsControlChars(null, false)).isFalse();
        assertThat(TextNormalizer.containsControlChars("", false)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "'Xin chào', false",
            "'私（わたし）', false",
            "'watashi', false"
    })
    @DisplayName("containsControlChars trả về false cho văn bản bình thường")
    void containsControlChars_normalText_returnsFalse(String input, boolean expected) {
        assertThat(TextNormalizer.containsControlChars(input)).isEqualTo(expected);
    }
}

