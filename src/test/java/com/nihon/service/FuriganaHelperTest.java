package com.nihon.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm thử đơn vị cho {@link FuriganaHelper}:
 * Đảm bảo thuật toán bóc tách dấu ngoặc hoạt động chính xác với định dạng nguồn tiếng Nhật.
 */
class FuriganaHelperTest {

    @Test
    @DisplayName("Tách ngoặc với đầu vào null trả về null")
    void extractBaseTextWithNullReturnsNull() {
        assertNull(FuriganaHelper.extractBaseText(null));
        assertNull(FuriganaHelper.stripParentheses(null));
    }

    @Test
    @DisplayName("Tách ngoặc với chuỗi rỗng trả về chuỗi rỗng")
    void extractBaseTextWithEmptyStringReturnsEmptyString() {
        assertEquals("", FuriganaHelper.extractBaseText(""));
        assertEquals("", FuriganaHelper.extractBaseText("   "));
        assertEquals("", FuriganaHelper.stripParentheses(""));
    }

    @ParameterizedTest(name = "Tách ngoặc: \"{0}\" -> \"{1}\"")
    @CsvSource(value = {
            "私（わたし）:私",
            "1歳:1歳",
            "日本酒（にほんしゅ）:日本酒",
            "先生:先生",
            "お酒（おさけ）:お酒",
            "私(わたし):私",
            "日本(にほん):日本",
            "初(はじ)めまして:初めまして"
    }, delimiter = ':')
    @DisplayName("Tách ngoặc chuẩn xác theo các trường hợp yêu cầu và mở rộng")
    void extractBaseTextParameterized(String input, String expected) {
        assertEquals(expected, FuriganaHelper.extractBaseText(input));
        assertEquals(expected, FuriganaHelper.stripParentheses(input));
    }

    @Test
    @DisplayName("Trích xuất cách đọc nằm trong ngoặc")
    void extractParenthesesReadingTests() {
        assertNull(FuriganaHelper.extractParenthesesReading(null));
        assertNull(FuriganaHelper.extractParenthesesReading(""));
        assertNull(FuriganaHelper.extractParenthesesReading("1歳"));
        assertEquals("わたし", FuriganaHelper.extractParenthesesReading("私（わたし）"));
        assertEquals("にほんしゅ", FuriganaHelper.extractParenthesesReading("日本酒（にほんしゅ）"));
        assertEquals("わたし", FuriganaHelper.extractParenthesesReading("私(わたし)"));
    }

    @Test
    @DisplayName("Kiểm tra hasReading")
    void hasReadingTests() {
        assertFalse(FuriganaHelper.hasReading(null));
        assertFalse(FuriganaHelper.hasReading(""));
        assertFalse(FuriganaHelper.hasReading("   "));
        assertTrue(FuriganaHelper.hasReading("わたし"));
    }
}

