package com.nihon.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpeechHelperTest {

    @Test
    @DisplayName("1. Có reading: ưu tiên lấy reading để phát âm chuẩn xác")
    void resolveSpeechText_hasReading() {
        assertThat(SpeechHelper.resolveSpeechText("私", "わたし")).isEqualTo("わたし");
        assertThat(SpeechHelper.resolveSpeechText("食べる", "たべる")).isEqualTo("たべる");
    }

    @Test
    @DisplayName("2. Không có reading: lấy japaneseText làm văn bản đọc")
    void resolveSpeechText_noReading() {
        assertThat(SpeechHelper.resolveSpeechText("テーブル", null)).isEqualTo("テーブル");
        assertThat(SpeechHelper.resolveSpeechText("ありがとう", "")).isEqualTo("ありがとう");
        assertThat(SpeechHelper.resolveSpeechText("こんにちは", "   ")).isEqualTo("こんにちは");
    }

    @Test
    @DisplayName("3. japaneseText có chứa ngoặc đọc nguồn: loại bỏ ngoặc trước khi đọc nếu không có reading")
    void resolveSpeechText_hasParenthesesInJapaneseText() {
        assertThat(SpeechHelper.resolveSpeechText("私（わたし）", null)).isEqualTo("私");
        assertThat(SpeechHelper.resolveSpeechText("日本酒（にほんしゅ）", "")).isEqualTo("日本酒");
        // Nếu vừa có ngoặc trong japaneseText vừa có reading riêng biệt: ưu tiên reading
        assertThat(SpeechHelper.resolveSpeechText("日本酒（にほんしゅ）", "にほんしゅ")).isEqualTo("にほんしゅ");
    }

    @Test
    @DisplayName("4. Chuỗi rỗng hoặc chỉ có khoảng trắng")
    void resolveSpeechText_emptyString() {
        assertThat(SpeechHelper.resolveSpeechText("", "")).isEqualTo("");
        assertThat(SpeechHelper.resolveSpeechText("   ", "   ")).isEqualTo("");
    }

    @Test
    @DisplayName("5. Đầu vào null an toàn không gây ngoại lệ NullPointerException")
    void resolveSpeechText_nullHandling() {
        assertThat(SpeechHelper.resolveSpeechText(null, null)).isEqualTo("");
        assertThat(SpeechHelper.resolveSpeechText(null, "わたし")).isEqualTo("わたし");
        assertThat(SpeechHelper.resolveSpeechText("私", null)).isEqualTo("私");
    }
}

