package com.nihon.conjugation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerbConjugatorTest {

    private void assertForms(ConjugatedForms forms,
                             String expectedDict,
                             String expectedMasu,
                             String expectedTe,
                             String expectedTa,
                             String expectedNai) {
        assertThat(forms.getDictionaryForm()).as("dictionaryForm").isEqualTo(expectedDict);
        assertThat(forms.getMasuForm()).as("masuForm").isEqualTo(expectedMasu);
        assertThat(forms.getTeForm()).as("teForm").isEqualTo(expectedTe);
        assertThat(forms.getTaForm()).as("taForm").isEqualTo(expectedTa);
        assertThat(forms.getNaiForm()).as("naiForm").isEqualTo(expectedNai);
    }

    private void assertKanjiForms(ConjugatedForms forms,
                                  String expectedDict,
                                  String expectedMasu,
                                  String expectedTe,
                                  String expectedTa,
                                  String expectedNai) {
        assertThat(forms.hasKanji()).isTrue();
        assertThat(forms.getKanjiDictionaryForm()).as("kanjiDictionaryForm").isEqualTo(expectedDict);
        assertThat(forms.getKanjiMasuForm()).as("kanjiMasuForm").isEqualTo(expectedMasu);
        assertThat(forms.getKanjiTeForm()).as("kanjiTeForm").isEqualTo(expectedTe);
        assertThat(forms.getKanjiTaForm()).as("kanjiTaForm").isEqualTo(expectedTa);
        assertThat(forms.getKanjiNaiForm()).as("kanjiNaiForm").isEqualTo(expectedNai);
    }

    @Nested
    @DisplayName("Group 1: Godan verbs across all endings")
    class Group1Tests {

        @Test
        @DisplayName("Ending: う -> かう (買う)")
        void testGodanU() {
            ConjugatedForms forms = VerbConjugator.conjugate("かう", 1, "買う");
            assertForms(forms, "かう", "かいます", "かって", "かった", "かわない");
            assertKanjiForms(forms, "買う", "買います", "買って", "買った", "買わない");
        }

        @Test
        @DisplayName("Ending: う -> いう (言う)")
        void testGodanU_Iu() {
            ConjugatedForms forms = VerbConjugator.conjugate("いう", 1, "言う");
            assertForms(forms, "いう", "いいます", "いって", "いった", "いわない");
            assertKanjiForms(forms, "言う", "言います", "言って", "言った", "言わない");
        }

        @Test
        @DisplayName("Ending: う -> おもう (思う)")
        void testGodanU_Omou() {
            ConjugatedForms forms = VerbConjugator.conjugate("おもう", 1, "思う");
            assertForms(forms, "おもう", "おもいます", "おもって", "おもった", "おもわない");
            assertKanjiForms(forms, "思う", "思います", "思って", "思った", "思わない");
        }

        @Test
        @DisplayName("Ending: つ -> まつ (待つ)")
        void testGodanTsu() {
            ConjugatedForms forms = VerbConjugator.conjugate("まつ", 1, "待つ");
            assertForms(forms, "まつ", "まちます", "まって", "まった", "またない");
            assertKanjiForms(forms, "待つ", "待ちます", "待って", "待った", "待たない");
        }

        @Test
        @DisplayName("Ending: つ -> たつ (立つ)")
        void testGodanTsu_Tatsu() {
            ConjugatedForms forms = VerbConjugator.conjugate("たつ", 1, "立つ");
            assertForms(forms, "たつ", "たちます", "たって", "たった", "たたない");
            assertKanjiForms(forms, "立つ", "立ちます", "立って", "立った", "立たない");
        }

        @Test
        @DisplayName("Ending: つ -> もつ (持つ)")
        void testGodanTsu_Motsu() {
            ConjugatedForms forms = VerbConjugator.conjugate("もつ", 1, "持つ");
            assertForms(forms, "もつ", "もちます", "もって", "もった", "もたない");
            assertKanjiForms(forms, "持つ", "持ちます", "持って", "持った", "持たない");
        }

        @Test
        @DisplayName("Ending: る -> とる (取る)")
        void testGodanRu() {
            ConjugatedForms forms = VerbConjugator.conjugate("とる", 1, "取る");
            assertForms(forms, "とる", "とります", "とって", "とった", "とらない");
            assertKanjiForms(forms, "取る", "取ります", "取って", "取った", "取らない");
        }

        @Test
        @DisplayName("Ending: る -> つくる (作る)")
        void testGodanRu_Tsukuru() {
            ConjugatedForms forms = VerbConjugator.conjugate("つくる", 1, "作る");
            assertForms(forms, "つくる", "つくります", "つくって", "つくった", "つくらない");
            assertKanjiForms(forms, "作る", "作ります", "作って", "作った", "作らない");
        }

        @Test
        @DisplayName("Ending: る -> わかる (分かる)")
        void testGodanRu_Wakaru() {
            ConjugatedForms forms = VerbConjugator.conjugate("わかる", 1, "分かる");
            assertForms(forms, "わかる", "わかります", "わかって", "わかった", "わからない");
            assertKanjiForms(forms, "分かる", "分かります", "分かって", "分かった", "分からない");
        }

        @Test
        @DisplayName("Ending: む -> よむ (読む)")
        void testGodanMu() {
            ConjugatedForms forms = VerbConjugator.conjugate("よむ", 1, "読む");
            assertForms(forms, "よむ", "よみます", "よんで", "よんだ", "よまない");
            assertKanjiForms(forms, "読む", "読みます", "読んで", "読んだ", "読まない");
        }

        @Test
        @DisplayName("Ending: む -> のむ (飲む)")
        void testGodanMu_Nomu() {
            ConjugatedForms forms = VerbConjugator.conjugate("のむ", 1, "飲む");
            assertForms(forms, "のむ", "のみます", "のんで", "のんだ", "のまない");
            assertKanjiForms(forms, "飲む", "飲みます", "飲んで", "飲んだ", "飲まない");
        }

        @Test
        @DisplayName("Ending: む -> やすむ (休む)")
        void testGodanMu_Yasumu() {
            ConjugatedForms forms = VerbConjugator.conjugate("やすむ", 1, "休む");
            assertForms(forms, "やすむ", "やすみます", "やすんで", "やすんだ", "やすまない");
            assertKanjiForms(forms, "休む", "休みます", "休んで", "休んだ", "休まない");
        }

        @Test
        @DisplayName("Ending: む -> すむ (住む)")
        void testGodanMu_Sumu() {
            ConjugatedForms forms = VerbConjugator.conjugate("すむ", 1, "住む");
            assertForms(forms, "すむ", "すみます", "すんで", "すんだ", "すまない");
            assertKanjiForms(forms, "住む", "住みます", "住んで", "住んだ", "住まない");
        }

        @Test
        @DisplayName("Ending: ぶ -> あそぶ (遊ぶ)")
        void testGodanBu() {
            ConjugatedForms forms = VerbConjugator.conjugate("あそぶ", 1, "遊ぶ");
            assertForms(forms, "あそぶ", "あそびます", "あそんで", "あそんだ", "あそばない");
            assertKanjiForms(forms, "遊ぶ", "遊びます", "遊んで", "遊んだ", "遊ばない");
        }

        @Test
        @DisplayName("Ending: ぶ -> よぶ (呼ぶ)")
        void testGodanBu_Yobu() {
            ConjugatedForms forms = VerbConjugator.conjugate("よぶ", 1, "呼ぶ");
            assertForms(forms, "よぶ", "よびます", "よんで", "よんだ", "よばない");
            assertKanjiForms(forms, "呼ぶ", "呼びます", "呼んで", "呼んだ", "呼ばない");
        }

        @Test
        @DisplayName("Ending: ぶ -> えらぶ (選ぶ)")
        void testGodanBu_Erabu() {
            ConjugatedForms forms = VerbConjugator.conjugate("えらぶ", 1, "選ぶ");
            assertForms(forms, "えらぶ", "えらびます", "えらんで", "えらんだ", "えらばない");
            assertKanjiForms(forms, "選ぶ", "選びます", "選んで", "選んだ", "選ばない");
        }

        @Test
        @DisplayName("Ending: ぬ -> しぬ (死ぬ)")
        void testGodanNu() {
            ConjugatedForms forms = VerbConjugator.conjugate("しぬ", 1, "死ぬ");
            assertForms(forms, "しぬ", "しにます", "しんで", "しんだ", "しなない");
            assertKanjiForms(forms, "死ぬ", "死にます", "死んで", "死んだ", "死なない");
        }

        @Test
        @DisplayName("Ending: く -> かく (書く)")
        void testGodanKu() {
            ConjugatedForms forms = VerbConjugator.conjugate("かく", 1, "書く");
            assertForms(forms, "かく", "かきます", "かいて", "かいた", "かかない");
            assertKanjiForms(forms, "書く", "書きます", "書いて", "書いた", "書かない");
        }

        @Test
        @DisplayName("Ending: く -> きく (聞く)")
        void testGodanKu_Kiku() {
            ConjugatedForms forms = VerbConjugator.conjugate("きく", 1, "聞く");
            assertForms(forms, "きく", "ききます", "きいて", "きいた", "きかない");
            assertKanjiForms(forms, "聞く", "聞きます", "聞いて", "聞いた", "聞かない");
        }

        @Test
        @DisplayName("Ending: く -> あるく (歩く)")
        void testGodanKu_Aruku() {
            ConjugatedForms forms = VerbConjugator.conjugate("あるく", 1, "歩く");
            assertForms(forms, "あるく", "あるきます", "あるいて", "あるいた", "あるかない");
            assertKanjiForms(forms, "歩く", "歩きます", "歩いて", "歩いた", "歩かない");
        }

        @Test
        @DisplayName("Ending: ぐ -> およぐ (泳ぐ)")
        void testGodanGu() {
            ConjugatedForms forms = VerbConjugator.conjugate("およぐ", 1, "泳ぐ");
            assertForms(forms, "およぐ", "およぎます", "およいで", "およいだ", "およがない");
            assertKanjiForms(forms, "泳ぐ", "泳ぎます", "泳いで", "泳いだ", "泳がない");
        }

        @Test
        @DisplayName("Ending: ぐ -> いそぐ (急ぐ)")
        void testGodanGu_Isogu() {
            ConjugatedForms forms = VerbConjugator.conjugate("いそぐ", 1, "急ぐ");
            assertForms(forms, "いそぐ", "いそぎます", "いそいで", "いそいだ", "いそがない");
            assertKanjiForms(forms, "急ぐ", "急ぎます", "急いで", "急いだ", "急がない");
        }

        @Test
        @DisplayName("Ending: す -> はなす (話す)")
        void testGodanSu() {
            ConjugatedForms forms = VerbConjugator.conjugate("はなす", 1, "話す");
            assertForms(forms, "はなす", "はなします", "はなして", "はなした", "はなさない");
            assertKanjiForms(forms, "話す", "話します", "話して", "話した", "話さない");
        }

        @Test
        @DisplayName("Ending: す -> かす (貸す)")
        void testGodanSu_Kasu() {
            ConjugatedForms forms = VerbConjugator.conjugate("かす", 1, "貸す");
            assertForms(forms, "かす", "かします", "かして", "かした", "かさない");
            assertKanjiForms(forms, "貸す", "貸します", "貸して", "貸した", "貸さない");
        }

        @Test
        @DisplayName("Ending: す -> だす (出す)")
        void testGodanSu_Dasu() {
            ConjugatedForms forms = VerbConjugator.conjugate("だす", 1, "出す");
            assertForms(forms, "だす", "だします", "だして", "だした", "ださない");
            assertKanjiForms(forms, "出す", "出します", "出して", "出した", "出さない");
        }
    }

    @Nested
    @DisplayName("Special Exceptions: いく/行く, ある/有る")
    class SpecialExceptionTests {

        @Test
        @DisplayName("Exception: いく / 行く (te -> いって/行って, ta -> いった/行った)")
        void testIkuException() {
            ConjugatedForms forms = VerbConjugator.conjugate("いく", 1, "行く");
            assertForms(forms, "いく", "いきます", "いって", "いった", "いかない");
            assertKanjiForms(forms, "行く", "行きます", "行って", "行った", "行かない");
        }

        @Test
        @DisplayName("Exception: ある / 有る (nai -> ない)")
        void testAruException() {
            ConjugatedForms forms = VerbConjugator.conjugate("ある", 1, "有る");
            assertForms(forms, "ある", "あります", "あって", "あった", "ない");
            assertKanjiForms(forms, "有る", "有ります", "有って", "有った", "ない");
        }
    }

    @Nested
    @DisplayName("Group 1 lookalikes (end in -iru/-eru but are Godan)")
    class Group1LookalikesTests {

        @Test
        @DisplayName("かえる (帰る)")
        void testKaeru() {
            ConjugatedForms forms = VerbConjugator.conjugate("かえる", 1, "帰る");
            assertForms(forms, "かえる", "かえります", "かえって", "かえった", "かえらない");
            assertKanjiForms(forms, "帰る", "帰ります", "帰って", "帰った", "帰らない");
        }

        @Test
        @DisplayName("はいる (入る)")
        void testHairu() {
            ConjugatedForms forms = VerbConjugator.conjugate("はいる", 1, "入る");
            assertForms(forms, "はいる", "はいります", "はいって", "はいった", "はいらない");
            assertKanjiForms(forms, "入る", "入ります", "入って", "入った", "入らない");
        }

        @Test
        @DisplayName("しる (知る)")
        void testShiru() {
            ConjugatedForms forms = VerbConjugator.conjugate("しる", 1, "知る");
            assertForms(forms, "しる", "しります", "しって", "しった", "しらない");
            assertKanjiForms(forms, "知る", "知ります", "知って", "知った", "知らない");
        }

        @Test
        @DisplayName("はしる (走る)")
        void testHashiru() {
            ConjugatedForms forms = VerbConjugator.conjugate("はしる", 1, "走る");
            assertForms(forms, "はしる", "はしります", "はしって", "はしった", "はしらない");
            assertKanjiForms(forms, "走る", "走ります", "走って", "走った", "走らない");
        }

        @Test
        @DisplayName("きる (切る)")
        void testKiru() {
            ConjugatedForms forms = VerbConjugator.conjugate("きる", 1, "切る");
            assertForms(forms, "きる", "きります", "きって", "きった", "きらない");
            assertKanjiForms(forms, "切る", "切ります", "切って", "切った", "切らない");
        }

        @Test
        @DisplayName("しゃべる (喋る)")
        void testShaberu() {
            ConjugatedForms forms = VerbConjugator.conjugate("しゃべる", 1, "喋る");
            assertForms(forms, "しゃべる", "しゃべります", "しゃべって", "しゃべった", "しゃべらない");
            assertKanjiForms(forms, "喋る", "喋ります", "喋って", "喋った", "喋らない");
        }
    }

    @Nested
    @DisplayName("Group 2: Ichidan verbs")
    class Group2Tests {

        @Test
        @DisplayName("たべる (食べる)")
        void testTaberu() {
            ConjugatedForms forms = VerbConjugator.conjugate("たべる", 2, "食べる");
            assertForms(forms, "たべる", "たべます", "たべて", "たべた", "たべない");
            assertKanjiForms(forms, "食べる", "食べます", "食べて", "食べた", "食べない");
        }

        @Test
        @DisplayName("みる (見る)")
        void testMiru() {
            ConjugatedForms forms = VerbConjugator.conjugate("みる", 2, "見る");
            assertForms(forms, "みる", "みます", "みて", "みた", "みない");
            assertKanjiForms(forms, "見る", "見ます", "見て", "見た", "見ない");
        }

        @Test
        @DisplayName("おきる (起きる)")
        void testOkiru() {
            ConjugatedForms forms = VerbConjugator.conjugate("おきる", 2, "起きる");
            assertForms(forms, "おきる", "おきます", "おきて", "おきた", "おきない");
            assertKanjiForms(forms, "起きる", "起きます", "起きて", "起きた", "起きない");
        }

        @Test
        @DisplayName("ねる (寝る)")
        void testNeru() {
            ConjugatedForms forms = VerbConjugator.conjugate("ねる", 2, "寝る");
            assertForms(forms, "ねる", "ねます", "ねて", "ねた", "ねない");
            assertKanjiForms(forms, "寝る", "寝ます", "寝て", "寝た", "寝ない");
        }

        @Test
        @DisplayName("おしえる (教える)")
        void testOshieru() {
            ConjugatedForms forms = VerbConjugator.conjugate("おしえる", 2, "教える");
            assertForms(forms, "おしえる", "おしえます", "おしえて", "おしえた", "おしえない");
            assertKanjiForms(forms, "教える", "教えます", "教えて", "教えた", "教えない");
        }

        @Test
        @DisplayName("あける (開ける)")
        void testAkeru() {
            ConjugatedForms forms = VerbConjugator.conjugate("あける", 2, "開ける");
            assertForms(forms, "あける", "あけます", "あけて", "あけた", "あけない");
            assertKanjiForms(forms, "開ける", "開けます", "開けて", "開けた", "開けない");
        }

        @Test
        @DisplayName("しめる (閉める)")
        void testShimeru() {
            ConjugatedForms forms = VerbConjugator.conjugate("しめる", 2, "閉める");
            assertForms(forms, "しめる", "しめます", "しめて", "しめた", "しめない");
            assertKanjiForms(forms, "閉める", "閉めます", "閉めて", "閉めた", "閉めない");
        }

        @Test
        @DisplayName("わすれる (忘れる)")
        void testWasureru() {
            ConjugatedForms forms = VerbConjugator.conjugate("わすれる", 2, "忘れる");
            assertForms(forms, "わすれる", "わすれます", "わすれて", "わすれた", "わすれない");
            assertKanjiForms(forms, "忘れる", "忘れます", "忘れて", "忘れた", "忘れない");
        }

        @Test
        @DisplayName("かりる (借りる)")
        void testKariru() {
            ConjugatedForms forms = VerbConjugator.conjugate("かりる", 2, "借りる");
            assertForms(forms, "かりる", "かります", "かりて", "かりた", "かりない");
            assertKanjiForms(forms, "借りる", "借ります", "借りて", "借りた", "借りない");
        }
    }

    @Nested
    @DisplayName("Group 3: Irregular verbs (する, くる, and compounds)")
    class Group3Tests {

        @Test
        @DisplayName("する")
        void testSuru() {
            ConjugatedForms forms = VerbConjugator.conjugate("する", 3);
            assertForms(forms, "する", "します", "して", "した", "しない");
            assertThat(forms.hasKanji()).isFalse();
        }

        @Test
        @DisplayName("くる (来る)")
        void testKuru() {
            ConjugatedForms forms = VerbConjugator.conjugate("くる", 3, "来る");
            assertForms(forms, "くる", "きます", "きて", "きた", "こない");
            assertKanjiForms(forms, "来る", "来ます", "来て", "来た", "来ない");
        }

        @Test
        @DisplayName("べんきょうする (勉強する)")
        void testBenkyouSuru() {
            ConjugatedForms forms = VerbConjugator.conjugate("べんきょうする", 3, "勉強する");
            assertForms(forms, "べんきょうする", "べんきょうします", "べんきょうして", "べんきょうした", "べんきょうしない");
            assertKanjiForms(forms, "勉強する", "勉強します", "勉強して", "勉強した", "勉強しない");
        }

        @Test
        @DisplayName("れんしゅうする (練習する)")
        void testRenshuuSuru() {
            ConjugatedForms forms = VerbConjugator.conjugate("れんしゅうする", 3, "練習する");
            assertForms(forms, "れんしゅうする", "れんしゅうします", "れんしゅうして", "れんしゅうした", "れんしゅうしない");
            assertKanjiForms(forms, "練習する", "練習します", "練習して", "練習した", "練習しない");
        }

        @Test
        @DisplayName("さんぽする (散歩する)")
        void testSanpoSuru() {
            ConjugatedForms forms = VerbConjugator.conjugate("さんぽする", 3, "散歩する");
            assertForms(forms, "さんぽする", "さんぽします", "さんぽして", "さんぽした", "さんぽしない");
            assertKanjiForms(forms, "散歩する", "散歩します", "散歩して", "散歩した", "散歩しない");
        }

        @Test
        @DisplayName("もってくる (持って来る)")
        void testMotteKuru() {
            ConjugatedForms forms = VerbConjugator.conjugate("もってくる", 3, "持って来る");
            assertForms(forms, "もってくる", "もってきます", "もってきて", "もってきた", "もってこない");
            assertKanjiForms(forms, "持って来る", "持って来ます", "持って来て", "持って来た", "持って来ない");
        }

        @Test
        @DisplayName("つれてくる (連れて来る)")
        void testTsureteKuru() {
            ConjugatedForms forms = VerbConjugator.conjugate("つれてくる", 3, "連れて来る");
            assertForms(forms, "つれてくる", "つれてきます", "つれてきて", "つれてきた", "つれてこない");
            assertKanjiForms(forms, "連れて来る", "連れて来ます", "連れて来て", "連れて来た", "連れて来ない");
        }
    }

    @Nested
    @DisplayName("Direct Kanji input in dictionaryForm")
    class DirectKanjiTests {

        @Test
        @DisplayName("飲む directly in dictionaryForm")
        void testNomuDirectKanji() {
            ConjugatedForms forms = VerbConjugator.conjugate("飲む", 1);
            assertForms(forms, "のむ", "のみます", "のんで", "のんだ", "のまない");
            assertKanjiForms(forms, "飲む", "飲みます", "飲んで", "飲んだ", "飲まない");
        }

        @Test
        @DisplayName("食べる directly in dictionaryForm")
        void testTaberuDirectKanji() {
            ConjugatedForms forms = VerbConjugator.conjugate("食べる", 2);
            assertForms(forms, "たべる", "たべます", "たべて", "たべた", "たべない");
            assertKanjiForms(forms, "食べる", "食べます", "食べて", "食べた", "食べない");
        }

        @Test
        @DisplayName("行く directly in dictionaryForm")
        void testIkuDirectKanji() {
            ConjugatedForms forms = VerbConjugator.conjugate("行く", 1);
            assertForms(forms, "いく", "いきます", "いって", "いった", "いかない");
            assertKanjiForms(forms, "行く", "行きます", "行って", "行った", "行かない");
        }

        @Test
        @DisplayName("来る directly in dictionaryForm")
        void testKuruDirectKanji() {
            ConjugatedForms forms = VerbConjugator.conjugate("来る", 3);
            assertForms(forms, "くる", "きます", "きて", "きた", "こない");
            assertKanjiForms(forms, "来る", "来ます", "来て", "来た", "来ない");
        }
    }

    @Nested
    @DisplayName("Consistency validation: isConsistent()")
    class ConsistencyTests {

        @ParameterizedTest(name = "{0} - {1} - group {2} should be consistent")
        @CsvSource({
                "のむ, のみます, 1",
                "飲む, 飲みます, 1",
                "たべる, たべます, 2",
                "食べる, 食べます, 2",
                "いく, いきます, 1",
                "行く, 行きます, 1",
                "くる, きます, 3",
                "来る, 来ます, 3",
                "する, します, 3",
                "べんきょうする, べんきょうします, 3",
                "かえる, かえります, 1",
                "まつ, まちます, 1",
                "かく, かきます, 1",
                "およぐ, およぎます, 1",
                "しぬ, しにます, 1",
                "あそぶ, あそびます, 1",
                "はなす, はなします, 1",
                "かう, かいます, 1"
        })
        void testConsistentValidCases(String dict, String masu, int group) {
            assertThat(VerbConjugator.isConsistent(dict, masu, group)).isTrue();
        }

        @ParameterizedTest(name = "{0} - {1} - group {2} should be inconsistent")
        @CsvSource({
                "のむ, たべます, 1",
                "たべる, たべます, 1",      // たべる is group 2; if group 1, masu would be たべります
                "たべる, たべります, 2",
                "のむ, のみます, 2",        // のむ cannot be group 2 (does not end with る)
                "くる, きます, 1",          // くる as group 1 would be くります
                "する, します, 1",          // する as group 1 would be すります
                "べんきょうする, べんきょうします, 1",
                "かえる, かえます, 1",      // かえる as group 1 is かえります
                "いく, いきます, 2",        // いく cannot be group 2
                "まつ, まちます, 2"
        })
        void testInconsistentCases(String dict, String masu, int group) {
            assertThat(VerbConjugator.isConsistent(dict, masu, group)).isFalse();
        }

        @Test
        @DisplayName("isConsistent with nulls or blanks returns false")
        void testNullAndBlankCases() {
            assertThat(VerbConjugator.isConsistent(null, "のみます", 1)).isFalse();
            assertThat(VerbConjugator.isConsistent("のむ", null, 1)).isFalse();
            assertThat(VerbConjugator.isConsistent("のむ", "のみます", null)).isFalse();
            assertThat(VerbConjugator.isConsistent("", "のみます", 1)).isFalse();
            assertThat(VerbConjugator.isConsistent("のむ", "", 1)).isFalse();
            assertThat(VerbConjugator.isConsistent("のむ", "のみます", 0)).isFalse();
            assertThat(VerbConjugator.isConsistent("のむ", "のみます", 4)).isFalse();
            assertThat(VerbConjugator.isConsistent("のむ", "のむ", 1)).isFalse(); // does not end in ます
        }
    }

    @Nested
    @DisplayName("Masu inference: inferFromMasu()")
    class MasuInferenceTests {

        @Test
        @DisplayName("E-dan before ます: clearly Group 2")
        void testEdanGroup2Inference() {
            var r1 = VerbConjugator.inferFromMasu("たべます");
            assertThat(r1.isClear()).isTrue();
            assertThat(r1.getVerbGroup()).isEqualTo(2);
            assertThat(r1.getDictionaryForm()).isEqualTo("たべる");

            var r2 = VerbConjugator.inferFromMasu("ねます");
            assertThat(r2.isClear()).isTrue();
            assertThat(r2.getVerbGroup()).isEqualTo(2);
            assertThat(r2.getDictionaryForm()).isEqualTo("ねる");

            var r3 = VerbConjugator.inferFromMasu("おしえます");
            assertThat(r3.isClear()).isTrue();
            assertThat(r3.getVerbGroup()).isEqualTo(2);
            assertThat(r3.getDictionaryForm()).isEqualTo("おしえる");

            var r4 = VerbConjugator.inferFromMasu("あけます");
            assertThat(r4.isClear()).isTrue();
            assertThat(r4.getVerbGroup()).isEqualTo(2);
            assertThat(r4.getDictionaryForm()).isEqualTo("あける");
        }

        @Test
        @DisplayName("Exact します / きます: clearly Group 3")
        void testSuruAndKuruInference() {
            var suru = VerbConjugator.inferFromMasu("します");
            assertThat(suru.isClear()).isTrue();
            assertThat(suru.getVerbGroup()).isEqualTo(3);
            assertThat(suru.getDictionaryForm()).isEqualTo("する");

            var kuru = VerbConjugator.inferFromMasu("きます");
            assertThat(kuru.isClear()).isTrue();
            assertThat(kuru.getVerbGroup()).isEqualTo(3);
            assertThat(kuru.getDictionaryForm()).isEqualTo("くる");
        }

        @Test
        @DisplayName("Recognized compounds: clearly Group 3")
        void testRecognizedCompoundInference() {
            var benkyou = VerbConjugator.inferFromMasu("べんきょうします");
            assertThat(benkyou.isClear()).isTrue();
            assertThat(benkyou.getVerbGroup()).isEqualTo(3);
            assertThat(benkyou.getDictionaryForm()).isEqualTo("べんきょうする");

            var motte = VerbConjugator.inferFromMasu("もってきます");
            assertThat(motte.isClear()).isTrue();
            assertThat(motte.getVerbGroup()).isEqualTo(3);
            assertThat(motte.getDictionaryForm()).isEqualTo("もってくる");
        }

        @Test
        @DisplayName("I-dan before ます: ambiguous (Group 1 vs Group 2)")
        void testIdanAmbiguousInference() {
            // のみます -> could be のむ (G1) or のみる (G2)
            var nomimasu = VerbConjugator.inferFromMasu("のみます");
            assertThat(nomimasu.isValid()).isTrue();
            assertThat(nomimasu.isAmbiguous()).isTrue();
            assertThat(nomimasu.isClear()).isFalse();
            assertThat(nomimasu.getCandidates())
                    .extracting(MasuInferenceResult.Candidate::getDictionaryForm)
                    .contains("のむ", "のみる");

            // かきます -> could be かく (G1) or かきる (G2)
            var kakimasu = VerbConjugator.inferFromMasu("かきます");
            assertThat(kakimasu.isAmbiguous()).isTrue();
            assertThat(kakimasu.getCandidates())
                    .extracting(MasuInferenceResult.Candidate::getDictionaryForm)
                    .contains("かく", "かきる");

            // はなします -> could be はなす (G1), はなしる (G2), はなする (G3)
            var hanashimasu = VerbConjugator.inferFromMasu("はなします");
            assertThat(hanashimasu.isAmbiguous()).isTrue();
            assertThat(hanashimasu.getCandidates())
                    .extracting(MasuInferenceResult.Candidate::getDictionaryForm)
                    .contains("はなす", "はなしる");

            // まちます -> could be まつ (G1) or まちる (G2)
            var machimasu = VerbConjugator.inferFromMasu("まちます");
            assertThat(machimasu.isAmbiguous()).isTrue();
            assertThat(machimasu.getCandidates())
                    .extracting(MasuInferenceResult.Candidate::getDictionaryForm)
                    .contains("まつ", "まちる");

            // かいます -> could be かう (G1) or かいる (G2)
            var kaimasu = VerbConjugator.inferFromMasu("かいます");
            assertThat(kaimasu.isAmbiguous()).isTrue();
            assertThat(kaimasu.getCandidates())
                    .extracting(MasuInferenceResult.Candidate::getDictionaryForm)
                    .contains("かう", "かいる");
        }

        @Test
        @DisplayName("Invalid masu forms")
        void testInvalidMasuForms() {
            assertThat(VerbConjugator.inferFromMasu(null).isValid()).isFalse();
            assertThat(VerbConjugator.isConsistent("   ", "ます", 1)).isFalse();
            assertThat(VerbConjugator.inferFromMasu("たべる").isValid()).isFalse();
            assertThat(VerbConjugator.inferFromMasu("ます").isValid()).isFalse(); // too short
            assertThat(VerbConjugator.inferFromMasu("あます").isValid()).isFalse(); // 'あ' is neither i-dan nor e-dan
        }
    }

    @Nested
    @DisplayName("Group inference from dictionary form")
    class DictionaryGroupInferenceTests {

        @Test
        void testGroupInference() {
            assertThat(VerbConjugator.inferGroup("する")).isEqualTo(3);
            assertThat(VerbConjugator.inferGroup("べんきょうする")).isEqualTo(3);
            assertThat(VerbConjugator.inferGroup("くる")).isEqualTo(3);
            assertThat(VerbConjugator.inferGroup("もってくる")).isEqualTo(3);

            // Group 1 non-ru endings
            assertThat(VerbConjugator.inferGroup("かう")).isEqualTo(1);
            assertThat(VerbConjugator.inferGroup("まつ")).isEqualTo(1);
            assertThat(VerbConjugator.inferGroup("かく")).isEqualTo(1);
            assertThat(VerbConjugator.inferGroup("およぐ")).isEqualTo(1);
            assertThat(VerbConjugator.inferGroup("よむ")).isEqualTo(1);
            assertThat(VerbConjugator.inferGroup("あそぶ")).isEqualTo(1);
            assertThat(VerbConjugator.inferGroup("しぬ")).isEqualTo(1);
            assertThat(VerbConjugator.inferGroup("はなす")).isEqualTo(1);

            // Group 1 -aru, -uru, -oru
            assertThat(VerbConjugator.inferGroup("ある")).isEqualTo(1);
            assertThat(VerbConjugator.inferGroup("とる")).isEqualTo(1);
            assertThat(VerbConjugator.inferGroup("つくる")).isEqualTo(1);

            // Group 1 lookalike exceptions
            assertThat(VerbConjugator.inferGroup("かえる")).isEqualTo(1);
            assertThat(VerbConjugator.inferGroup("はいる")).isEqualTo(1);
            assertThat(VerbConjugator.inferGroup("しる")).isEqualTo(1);
            assertThat(VerbConjugator.inferGroup("はしる")).isEqualTo(1);
            assertThat(VerbConjugator.inferGroup("きる")).isEqualTo(1);
            assertThat(VerbConjugator.inferGroup("しゃべる")).isEqualTo(1);

            // Standard Group 2
            assertThat(VerbConjugator.inferGroup("たべる")).isEqualTo(2);
            assertThat(VerbConjugator.inferGroup("みる")).isEqualTo(2);
            assertThat(VerbConjugator.inferGroup("ねる")).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Extensibility: VerbExceptions dynamic registration")
    class ExtensibilityTests {

        @Test
        @DisplayName("Register custom Group 1 lookalike")
        void testDynamicGroup1ExceptionRegistration() {
            VerbExceptions.registerGroup1Lookalike("あせる");
            assertThat(VerbExceptions.isGroup1Exception("あせる")).isTrue();
            assertThat(VerbConjugator.inferGroup("あせる")).isEqualTo(1);
        }

        @Test
        @DisplayName("Register custom compound suru")
        void testDynamicCompoundSuruRegistration() {
            VerbExceptions.registerCompoundSuru("ちょうせんする");
            assertThat(VerbExceptions.isCompoundSuru("ちょうせんする")).isTrue();
            var res = VerbConjugator.inferFromMasu("ちょうせんします");
            assertThat(res.isClear()).isTrue();
            assertThat(res.getVerbGroup()).isEqualTo(3);
            assertThat(res.getDictionaryForm()).isEqualTo("ちょうせんする");
        }

        @Test
        @DisplayName("Register custom override")
        void testCustomOverrideRegistration() {
            ConjugatedForms customForms = new ConjugatedForms(
                    "テストする", "テストします", "テストして", "テストした", "テストしない"
            );
            VerbExceptions.registerCustomOverride("テストする", customForms);
            ConjugatedForms result = VerbConjugator.conjugate("テストする", 3);
            assertThat(result).isEqualTo(customForms);
        }
    }

    @Nested
    @DisplayName("Error Handling and Validation")
    class ErrorHandlingTests {

        @Test
        void testBlankDictionaryThrows() {
            assertThatThrownBy(() -> VerbConjugator.conjugate("", 1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> VerbConjugator.conjugate(null, 1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void testInvalidGroupThrows() {
            assertThatThrownBy(() -> VerbConjugator.conjugate("のむ", 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> VerbConjugator.conjugate("のむ", 4))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void testGroup2MustEndWithRu() {
            assertThatThrownBy(() -> VerbConjugator.conjugate("のむ", 2))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("る");
        }
    }
}
