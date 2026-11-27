package com.nihon.service.exam.grading;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class LevenshteinDistanceTest {

    @Test
    @DisplayName("Chuỗi rỗng hoặc null: xử lý an toàn không ném ngoại lệ")
    void testEmptyAndNullStrings() {
        assertThat(LevenshteinDistance.computeDistance("", "")).isZero();
        assertThat(LevenshteinDistance.computeDistance(null, null)).isZero();
        assertThat(LevenshteinDistance.computeDistance("abc", null)).isEqualTo(3);
        assertThat(LevenshteinDistance.computeDistance(null, "abcd")).isEqualTo(4);

        assertThat(LevenshteinDistance.computeSimilarity("", "")).isEqualTo(1.0);
        assertThat(LevenshteinDistance.computeSimilarity(null, null)).isEqualTo(1.0);
        assertThat(LevenshteinDistance.computeSimilarity("abc", "")).isZero();
        assertThat(LevenshteinDistance.computeSimilarity("", "xyz")).isZero();
    }

    @ParameterizedTest(name = "[{index}] '{0}' vs '{1}' -> Khoảng cách: {2}")
    @CsvSource({
            "kitten, sitting, 3",
            "flaw, lawn, 2",
            "saturday, sunday, 3",
            "こんにちは, こんにちは, 0",
            "こんにちは, こんにちわ, 1",
            "わたしはがくせいです, わたしはがくせいだ, 2"
    })
    @DisplayName("Tính khoảng cách Levenshtein chuẩn xác trên chuỗi Latin và tiếng Nhật")
    void testComputeDistance(String s1, String s2, int expectedDistance) {
        int distance = LevenshteinDistance.computeDistance(s1, s2);
        assertThat(distance).isEqualTo(expectedDistance);
    }

    @ParameterizedTest(name = "[{index}] '{0}' vs '{1}' -> Tỷ lệ tương đồng: {2}")
    @CsvSource({
            "abcde, abcde, 1.00",
            "abcde, abcd, 0.80",
            "1234567890, 123456789X, 0.90",
            "1234, 1234, 1.00"
    })
    @DisplayName("Tính tỷ lệ tương đồng chuẩn hóa từ 0.0 đến 1.0")
    void testComputeSimilarity(String s1, String s2, double expectedSimilarity) {
        double similarity = LevenshteinDistance.computeSimilarity(s1, s2);
        assertThat(similarity).isCloseTo(expectedSimilarity, offset(0.01));
    }
}

