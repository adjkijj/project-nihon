package com.nihon.conjugation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VerbPracticeConjugationTest {

    @Test
    @DisplayName("1. isFormMatch: Chấp nhận cả Hiragana và Kanji, chuẩn hóa khoảng trắng và NFC")
    void testIsFormMatchKanaAndKanji() {
        // Động từ のむ (飲む), Nhóm 1, thể TE -> のんで / 飲んで
        assertThat(VerbConjugator.isFormMatch("のむ", 1, "飲む", TargetForm.TE, "のんで")).isTrue();
        assertThat(VerbConjugator.isFormMatch("のむ", 1, "飲む", TargetForm.TE, "飲んで")).isTrue();
        assertThat(VerbConjugator.isFormMatch("のむ", 1, "飲む", TargetForm.TE, "　のんで  ")).isTrue();
        assertThat(VerbConjugator.isFormMatch("のむ", 1, "飲む", TargetForm.TE, " 飲んで\t")).isTrue();

        // Sai thể hoặc sai âm
        assertThat(VerbConjugator.isFormMatch("のむ", 1, "飲む", TargetForm.TE, "のんだ")).isFalse();
        assertThat(VerbConjugator.isFormMatch("のむ", 1, "飲む", TargetForm.TE, "のめて")).isFalse();
    }

    @Test
    @DisplayName("2. isFormMatch: Kiểm tra các thể khác nhau (RU, TA, NAI, MASU)")
    void testIsFormMatchAllForms() {
        // たべる (食べる), Nhóm 2
        assertThat(VerbConjugator.isFormMatch("たべる", 2, "食べる", TargetForm.RU, "たべる")).isTrue();
        assertThat(VerbConjugator.isFormMatch("たべる", 2, "食べる", TargetForm.RU, "食べる")).isTrue();

        assertThat(VerbConjugator.isFormMatch("たべる", 2, "食べる", TargetForm.TA, "たべた")).isTrue();
        assertThat(VerbConjugator.isFormMatch("たべる", 2, "食べる", TargetForm.TA, "食べた")).isTrue();

        assertThat(VerbConjugator.isFormMatch("たべる", 2, "食べる", TargetForm.NAI, "たべない")).isTrue();
        assertThat(VerbConjugator.isFormMatch("たべる", 2, "食べる", TargetForm.NAI, "食べない")).isTrue();

        assertThat(VerbConjugator.isFormMatch("たべる", 2, "食べる", TargetForm.MASU, "たべます")).isTrue();
        assertThat(VerbConjugator.isFormMatch("たべる", 2, "食べる", TargetForm.MASU, "食べます")).isTrue();
    }

    @Test
    @DisplayName("3. generateChoices: Luôn sinh đúng 4 phương án, không trùng lặp, chứa đáp án đúng")
    void testGenerateChoicesConsistency() {
        TargetForm[] forms = {TargetForm.TE, TargetForm.TA, TargetForm.NAI, TargetForm.RU, TargetForm.MASU};
        for (TargetForm tf : forms) {
            List<String> choices = VerbConjugator.generateChoices("のむ", 1, "飲む", tf);
            assertThat(choices).hasSize(4);
            assertThat(choices).doesNotHaveDuplicates();

            // Phải chứa ít nhất dạng Kana hoặc Kanji của đáp án đúng
            ConjugatedForms cf = VerbConjugator.conjugate("のむ", 1, "飲む");
            String expectedKana = VerbConjugator.getFormKana(cf, tf);
            String expectedKanji = VerbConjugator.getFormKanji(cf, tf);
            assertThat(choices).satisfies(c -> {
                boolean containsKana = c.contains(expectedKana);
                boolean containsKanji = expectedKanji != null && c.contains(expectedKanji);
                assertThat(containsKana || containsKanji).isTrue();
            });
        }
    }

    @Test
    @DisplayName("4. getShortRule: Trả về quy tắc ngắn gọn theo từng nhóm và thể")
    void testGetShortRule() {
        String ruleTeG1 = VerbConjugator.getShortRule(1, TargetForm.TE, "のむ");
        assertThat(ruleTeG1).containsIgnoringCase("nhóm 1").contains("む");

        String ruleTeG2 = VerbConjugator.getShortRule(2, TargetForm.TE, "たべる");
        assertThat(ruleTeG2).containsIgnoringCase("nhóm 2").contains("て");

        String ruleTeG3 = VerbConjugator.getShortRule(3, TargetForm.TE, "する");
        assertThat(ruleTeG3).containsIgnoringCase("nhóm 3");
    }

    @ParameterizedTest
    @CsvSource({
            "のみます, 1, のむ",
            "かいます, 1, かう",
            "はなします, 1, はなす",
            "たちます, 1, たつ",
            "しにます, 1, しぬ",
            "よびます, 1, よぶ",
            "いきます, 1, いく",
            "およぎます, 1, およぐ",
            "とります, 1, とる",
            "たべます, 2, たべる",
            "みます, 2, みる",
            "します, 3, する",
            "きます, 3, くる",
            "べんきょうします, 3, べんきょうする"
    })
    @DisplayName("5. masuToDictionary: Khôi phục chính xác thể từ điển từ thể masu và nhóm")
    void testMasuToDictionary(String masu, int group, String expectedDict) {
        String dict = VerbConjugator.masuToDictionary(masu, group);
        assertThat(dict).isEqualTo(expectedDict);
    }
}
