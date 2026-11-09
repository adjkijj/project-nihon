package com.nihon.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm thử tham số hóa (Parameterized Tests) cho toàn bộ 8 annotation kiểm tra văn bản tiếng Nhật và tiếng Việt.
 * Mỗi annotation được kiểm thử với ít nhất 5 giá trị hợp lệ và 8-10 giá trị không hợp lệ.
 */
class ValidationAnnotationsTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    // Các lớp bean phục vụ kiểm thử từng annotation độc lập
    private record NoControlCharsBean(@NoControlChars String value) {}
    private record JapaneseTextBean(@JapaneseText String value) {}
    private record KanaReadingBean(@KanaReading String value) {}
    private record OnyomiReadingBean(@OnyomiReading String value) {}
    private record RomajiTextBean(@RomajiText String value) {}
    private record VietnameseTextBean(@VietnameseText String value) {}
    private record HanVietTextBean(@HanVietText String value) {}
    private record SingleKanjiBean(@SingleKanji String value) {}

    // =========================================================================
    // 1. @NoControlChars
    // =========================================================================
    static Stream<String> validNoControlChars() {
        return Stream.of(
                null,
                "",
                "Bài 1: Chào hỏi",
                "私（わたし）",
                "watashi",
                "Xin chào 123!"
        );
    }

    static Stream<String> invalidNoControlChars() {
        return Stream.of(
                "\u0000",
                "văn bản\u0007chuông",
                "văn bản\u0008backspace",
                "văn bản\ttab",
                "dòng 1\ndòng 2",
                "dòng 1\rdòng 2",
                "dòng 1\r\ndòng 2",
                "escape\u001Bchar",
                "delete\u007Fchar",
                "c1control\u009Fchar"
        );
    }

    @ParameterizedTest
    @MethodSource("validNoControlChars")
    @DisplayName("@NoControlChars: Chấp nhận các chuỗi hợp lệ")
    void noControlChars_validValues(String input) {
        Set<ConstraintViolation<NoControlCharsBean>> violations = validator.validate(new NoControlCharsBean(input));
        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("invalidNoControlChars")
    @DisplayName("@NoControlChars: Từ chối các chuỗi chứa ký tự điều khiển hoặc xuống dòng")
    void noControlChars_invalidValues(String input) {
        Set<ConstraintViolation<NoControlCharsBean>> violations = validator.validate(new NoControlCharsBean(input));
        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getMessage()).isEqualTo(ValidationMessages.NO_CONTROL_CHARS);
    }

    // =========================================================================
    // 2. @JapaneseText
    // =========================================================================
    static Stream<String> validJapaneseText() {
        return Stream.of(
                "私（わたし）",
                "コーヒー",
                "すみません",
                "1歳（いっさい）",
                "〜さい（〜歳）",
                "はい / ええ",
                "父（ちち、とう）",
                "お・さけ"
        );
    }

    static Stream<String> invalidJapaneseText() {
        return Stream.of(
                "",                     // 1. chuỗi trống
                "   ",                  // 2. chỉ khoảng trắng
                "\u3000\u3000",         // 3. chỉ khoảng trắng U+3000
                "\u200B\u200C\uFEFF",   // 4. chỉ zero-width
                "私\u0007",             // 5. ký tự điều khiển
                "私\nわたし",           // 6. xuống dòng
                "<b>x</b>",             // 7. HTML
                "私😀",                 // 8. emoji
                "watashi",              // 9. chuỗi sai loại chữ (Latin)
                "12345"                 // 10. chỉ có số, không có Hiragana/Katakana/Hán
        );
    }

    @ParameterizedTest
    @MethodSource("validJapaneseText")
    @DisplayName("@JapaneseText: Chấp nhận các chuỗi tiếng Nhật hợp lệ")
    void japaneseText_validValues(String input) {
        Set<ConstraintViolation<JapaneseTextBean>> violations = validator.validate(new JapaneseTextBean(input));
        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("invalidJapaneseText")
    @DisplayName("@JapaneseText: Từ chối chuỗi trống, khoảng trắng, zero-width, control, xuống dòng, HTML, emoji, sai loại chữ")
    void japaneseText_invalidValues(String input) {
        Set<ConstraintViolation<JapaneseTextBean>> violations = validator.validate(new JapaneseTextBean(input));
        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getMessage()).isEqualTo(ValidationMessages.JAPANESE_TEXT);
    }

    // =========================================================================
    // 3. @KanaReading
    // =========================================================================
    static Stream<String> validKanaReading() {
        return Stream.of(
                null,
                "",
                "わたし",
                "コーヒー",
                "ちち、とう",
                "はい / ええ",
                "お・さけ",
                "-"
        );
    }

    static Stream<String> invalidKanaReading() {
        return Stream.of(
                "   ",                  // 1. chỉ khoảng trắng
                "\u3000\u3000",         // 2. chỉ khoảng trắng U+3000
                "\u200B\u200D\uFEFF",   // 3. chỉ zero-width
                "わたし\u0007",         // 4. ký tự điều khiển
                "わたし\nあなた",       // 5. xuống dòng
                "<b>x</b>",             // 6. HTML
                "わたし😀",             // 7. emoji
                "私",                   // 8. chuỗi sai loại chữ (Kanji)
                "watashi"               // 9. chuỗi sai loại chữ (Latin)
        );
    }

    @ParameterizedTest
    @MethodSource("validKanaReading")
    @DisplayName("@KanaReading: Chấp nhận các cách đọc Kana hợp lệ và giá trị null/trống")
    void kanaReading_validValues(String input) {
        Set<ConstraintViolation<KanaReadingBean>> violations = validator.validate(new KanaReadingBean(input));
        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("invalidKanaReading")
    @DisplayName("@KanaReading: Từ chối chỉ khoảng trắng, U+3000, zero-width, control, xuống dòng, HTML, emoji, sai loại chữ")
    void kanaReading_invalidValues(String input) {
        Set<ConstraintViolation<KanaReadingBean>> violations = validator.validate(new KanaReadingBean(input));
        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getMessage()).isEqualTo(ValidationMessages.KANA_READING);
    }

    // =========================================================================
    // 4. @OnyomiReading
    // =========================================================================
    static Stream<String> validOnyomiReading() {
        return Stream.of(
                null,
                "",
                "スイ",
                "コウ、キョウ",
                "こう、きょう",
                "シュー",
                "-"
        );
    }

    static Stream<String> invalidOnyomiReading() {
        return Stream.of(
                "   ",                  // 1. chỉ khoảng trắng
                "\u3000\u3000",         // 2. chỉ khoảng trắng U+3000
                "\u200B\u2060\uFEFF",   // 3. chỉ zero-width
                "スイ\u0007",           // 4. ký tự điều khiển
                "スイ\nコウ",           // 5. xuống dòng
                "<b>x</b>",             // 6. HTML
                "スイ😀",               // 7. emoji
                "水",                   // 8. chuỗi sai loại chữ (Kanji)
                "SUI"                   // 9. chuỗi sai loại chữ (Latin)
        );
    }

    @ParameterizedTest
    @MethodSource("validOnyomiReading")
    @DisplayName("@OnyomiReading: Chấp nhận các âm On hợp lệ và giá trị trống")
    void onyomiReading_validValues(String input) {
        Set<ConstraintViolation<OnyomiReadingBean>> violations = validator.validate(new OnyomiReadingBean(input));
        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("invalidOnyomiReading")
    @DisplayName("@OnyomiReading: Từ chối chỉ khoảng trắng, U+3000, zero-width, control, xuống dòng, HTML, emoji, sai loại chữ")
    void onyomiReading_invalidValues(String input) {
        Set<ConstraintViolation<OnyomiReadingBean>> violations = validator.validate(new OnyomiReadingBean(input));
        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getMessage()).isEqualTo(ValidationMessages.ONYOMI_READING);
    }

    // =========================================================================
    // 5. @RomajiText
    // =========================================================================
    static Stream<String> validRomajiText() {
        return Stream.of(
                null,
                "",
                "watashi",
                "tōkyō",
                "Ā Ī Ū Ē Ō ā ī ū ē ō",
                "shin'ichi",
                "chichi, tou / o-cha (ryokucha)."
        );
    }

    static Stream<String> invalidRomajiText() {
        return Stream.of(
                "   ",                  // 1. chỉ khoảng trắng
                "\u3000\u3000",         // 2. chỉ khoảng trắng U+3000
                "\u200B\u200C\uFEFF",   // 3. chỉ zero-width
                "watashi\u0007",        // 4. ký tự điều khiển
                "watashi\nanata",       // 5. xuống dòng
                "<b>x</b>",             // 6. HTML
                "watashi😀",            // 7. emoji
                "わたし",               // 8. chuỗi sai loại chữ (Hiragana)
                "Tiếng Việt"            // 9. chuỗi sai loại chữ (dấu tiếng Việt)
        );
    }

    @ParameterizedTest
    @MethodSource("validRomajiText")
    @DisplayName("@RomajiText: Chấp nhận các chuỗi Romaji hợp lệ và giá trị trống")
    void romajiText_validValues(String input) {
        Set<ConstraintViolation<RomajiTextBean>> violations = validator.validate(new RomajiTextBean(input));
        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("invalidRomajiText")
    @DisplayName("@RomajiText: Từ chối chỉ khoảng trắng, U+3000, zero-width, control, xuống dòng, HTML, emoji, sai loại chữ")
    void romajiText_invalidValues(String input) {
        Set<ConstraintViolation<RomajiTextBean>> violations = validator.validate(new RomajiTextBean(input));
        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getMessage()).isEqualTo(ValidationMessages.ROMAJI_TEXT);
    }

    // =========================================================================
    // 6. @VietnameseText
    // =========================================================================
    static Stream<String> validVietnameseText() {
        return Stream.of(
                null,
                "",
                "Tôi (đại từ xưng hô)",
                "Xin chào, rất vui được gặp bạn!",
                "Bài 1: Chào hỏi & Giới thiệu (Phần 1/2)",
                "Bố, cha, ba; mẹ, má",
                "100% chính xác - Đúng hoàn toàn"
        );
    }

    static Stream<String> invalidVietnameseText() {
        return Stream.of(
                "   ",                  // 1. chỉ khoảng trắng
                "\u3000\u3000",         // 2. chỉ khoảng trắng U+3000
                "\u200B\u200C\uFEFF",   // 3. chỉ zero-width
                "Xin chào\u0007",       // 4. ký tự điều khiển
                "Xin chào\nTạm biệt",   // 5. xuống dòng
                "<b>x</b>",             // 6. HTML
                "Xin chào 😀",          // 7. emoji
                "わたし",               // 8. chuỗi sai loại chữ (Hiragana)
                "Привет"                // 9. chuỗi sai loại chữ (Cyrillic)
        );
    }

    @ParameterizedTest
    @MethodSource("validVietnameseText")
    @DisplayName("@VietnameseText: Chấp nhận văn bản tiếng Việt hợp lệ và giá trị trống")
    void vietnameseText_validValues(String input) {
        Set<ConstraintViolation<VietnameseTextBean>> violations = validator.validate(new VietnameseTextBean(input));
        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("invalidVietnameseText")
    @DisplayName("@VietnameseText: Từ chối chỉ khoảng trắng, U+3000, zero-width, control, xuống dòng, HTML, emoji, sai loại chữ")
    void vietnameseText_invalidValues(String input) {
        Set<ConstraintViolation<VietnameseTextBean>> violations = validator.validate(new VietnameseTextBean(input));
        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getMessage()).isEqualTo(ValidationMessages.VIETNAMESE_TEXT);
    }

    // =========================================================================
    // 7. @HanVietText
    // =========================================================================
    static Stream<String> validHanVietText() {
        return Stream.of(
                null,
                "",
                "THỦY",
                "NGƯ",
                "GIÁO, HIỆU",
                "ĐẠO, ĐÁO",
                "QUỐC, QUỐT"
        );
    }

    static Stream<String> invalidHanVietText() {
        return Stream.of(
                "   ",                  // 1. chỉ khoảng trắng
                "\u3000\u3000",         // 2. chỉ khoảng trắng U+3000
                "\u200B\u200C\uFEFF",   // 3. chỉ zero-width
                "THỦY\u0007",           // 4. ký tự điều khiển
                "THỦY\nHỎA",            // 5. xuống dòng
                "<b>x</b>",             // 6. HTML
                "THỦY😀",               // 7. emoji
                "thủy",                 // 8. chuỗi sai loại chữ (chữ thường)
                "水"                    // 9. chuỗi sai loại chữ (Kanji)
        );
    }

    @ParameterizedTest
    @MethodSource("validHanVietText")
    @DisplayName("@HanVietText: Chấp nhận chữ Hán Việt in hoa hợp lệ và giá trị trống")
    void hanVietText_validValues(String input) {
        Set<ConstraintViolation<HanVietTextBean>> violations = validator.validate(new HanVietTextBean(input));
        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("invalidHanVietText")
    @DisplayName("@HanVietText: Từ chối chỉ khoảng trắng, U+3000, zero-width, control, xuống dòng, HTML, emoji, sai loại chữ")
    void hanVietText_invalidValues(String input) {
        Set<ConstraintViolation<HanVietTextBean>> violations = validator.validate(new HanVietTextBean(input));
        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getMessage()).isEqualTo(ValidationMessages.HAN_VIET_TEXT);
    }

    // =========================================================================
    // 8. @SingleKanji
    // =========================================================================
    static Stream<String> validSingleKanji() {
        return Stream.of(
                "道",
                "水",
                "魚",
                "校",
                "𠮟", // Kanji hiếm nằm ngoài BMP (U+20B9F, length() == 2 nhưng codePointCount == 1)
                "𠮷"  // Kanji ngoài BMP (U+20BB7)
        );
    }

    static Stream<String> invalidSingleKanji() {
        return Stream.of(
                "",                     // 1. chuỗi trống
                "   ",                  // 2. chỉ khoảng trắng
                "\u3000\u3000",         // 3. chỉ khoảng trắng U+3000
                "\u200B\u200C\uFEFF",   // 4. chỉ zero-width
                "道\u0007",             // 5. ký tự điều khiển
                "道\n",                 // 6. xuống dòng
                "<b>x</b>",             // 7. HTML
                "😀",                   // 8. emoji
                "道路",                 // 9. nhiều hơn 1 Kanji
                "あ",                   // 10. Hiragana (sai loại chữ)
                "A"                     // 11. Latin (sai loại chữ)
        );
    }

    @ParameterizedTest
    @MethodSource("validSingleKanji")
    @DisplayName("@SingleKanji: Chấp nhận đúng 1 ký tự Kanji (bao gồm cả Kanji ngoài BMP như '𠮟')")
    void singleKanji_validValues(String input) {
        Set<ConstraintViolation<SingleKanjiBean>> violations = validator.validate(new SingleKanjiBean(input));
        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("invalidSingleKanji")
    @DisplayName("@SingleKanji: Từ chối chuỗi trống, khoảng trắng, zero-width, control, xuống dòng, HTML, emoji, '道路', 'あ', 'A'")
    void singleKanji_invalidValues(String input) {
        Set<ConstraintViolation<SingleKanjiBean>> violations = validator.validate(new SingleKanjiBean(input));
        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getMessage()).isEqualTo(ValidationMessages.SINGLE_KANJI);
    }

    @Test
    @DisplayName("@SingleKanji: Kiểm tra trực tiếp các trường hợp đặc biệt '道', '道路', 'あ', 'A', '𠮟' và null")
    void singleKanji_specificCases() {
        assertThat(validator.validate(new SingleKanjiBean("道"))).isEmpty();
        assertThat(validator.validate(new SingleKanjiBean("𠮟"))).isEmpty();
        assertThat("𠮟".length()).isEqualTo(2);
        assertThat("𠮟".codePointCount(0, "𠮟".length())).isEqualTo(1);

        assertThat(validator.validate(new SingleKanjiBean(null))).isNotEmpty();
        assertThat(validator.validate(new SingleKanjiBean("道路"))).isNotEmpty();
        assertThat(validator.validate(new SingleKanjiBean("あ"))).isNotEmpty();
        assertThat(validator.validate(new SingleKanjiBean("A"))).isNotEmpty();
    }
}

