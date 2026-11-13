package com.nihon.service.exam.grading;

import com.nihon.entity.ExamAnswerKind;
import com.nihon.entity.ExamAnswerKey;
import com.nihon.entity.ExamChoice;
import com.nihon.entity.ExamQuestion;
import com.nihon.entity.ExamQuestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionGradersTest {

    @Nested
    @DisplayName("MultipleChoiceGrader: Chấm trắc nghiệm")
    class MultipleChoiceTests {
        private final MultipleChoiceGrader grader = new MultipleChoiceGrader();

        @Test
        @DisplayName("Đúng theo ID phương án hoặc theo sortOrder -> Đủ điểm, correct = true")
        void testCorrectMultipleChoice() {
            ExamQuestion q = ExamQuestion.builder()
                    .type(ExamQuestionType.MULTIPLE_CHOICE)
                    .points(BigDecimal.valueOf(2.0))
                    .choices(new ArrayList<>())
                    .build();

            ExamChoice c1 = ExamChoice.builder().id(101L).sortOrder(1).text("Sai A").correct(false).build();
            ExamChoice c2 = ExamChoice.builder().id(102L).sortOrder(2).text("Đúng B").correct(true).build();
            q.addChoice(c1);
            q.addChoice(c2);

            // Học viên chọn ID "102"
            GradingResult resId = grader.grade(q, "102");
            assertThat(resId.getCorrect()).isTrue();
            assertThat(resId.getPointsAwarded()).isEqualByComparingTo(BigDecimal.valueOf(2.0));

            // Học viên chọn số thứ tự "2"
            GradingResult resOrder = grader.grade(q, "2");
            assertThat(resOrder.getCorrect()).isTrue();
            assertThat(resOrder.getPointsAwarded()).isEqualByComparingTo(BigDecimal.valueOf(2.0));

            // Học viên chọn sai "101"
            GradingResult resWrong = grader.grade(q, "101");
            assertThat(resWrong.getCorrect()).isFalse();
            assertThat(resWrong.getPointsAwarded()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("TypingGrader: Chấm gõ đáp án (NFKC, bỏ khoảng trắng, dấu câu cuối, cờ kana)")
    class TypingTests {
        private final TypingGrader grader = new TypingGrader();

        @Test
        @DisplayName("Khớp đáp án ACCEPTED sau chuẩn hóa NFKC và bỏ khoảng trắng, dấu câu")
        void testTypingAcceptedNormalization() {
            ExamQuestion q = ExamQuestion.builder()
                    .type(ExamQuestionType.TYPING)
                    .points(BigDecimal.valueOf(1.5))
                    .answerKeys(new ArrayList<>())
                    .build();

            q.addAnswerKey(ExamAnswerKey.builder()
                    .kind(ExamAnswerKind.ACCEPTED)
                    .answerText("がくせい")
                    .build());

            // Nhập có dấu câu cuối và khoảng trắng
            GradingResult res = grader.grade(q, " がくせい 。 ");
            assertThat(res.getCorrect()).isTrue();
            assertThat(res.getPointsAwarded()).isEqualByComparingTo(BigDecimal.valueOf(1.5));

            // Nhập sai
            GradingResult resWrong = grader.grade(q, "せんせい");
            assertThat(resWrong.getCorrect()).isFalse();
            assertThat(resWrong.getPointsAwarded()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Cờ [CONVERT_KANA]: chuyển đổi Katakana sang Hiragana khi so khớp")
        void testTypingConvertKanaFlag() {
            ExamQuestion q = ExamQuestion.builder()
                    .type(ExamQuestionType.TYPING)
                    .points(BigDecimal.valueOf(1.0))
                    .explanation("[CONVERT_KANA] Cho phép gõ bằng Katakana hoặc Hiragana")
                    .answerKeys(new ArrayList<>())
                    .build();

            q.addAnswerKey(ExamAnswerKey.builder()
                    .kind(ExamAnswerKind.ACCEPTED)
                    .answerText("コーヒー")
                    .build());

            // Thí sinh nhập bằng Hiragana: こーひー
            GradingResult res = grader.grade(q, "こーひー");
            assertThat(res.getCorrect()).isTrue();
            assertThat(res.getPointsAwarded()).isEqualByComparingTo(BigDecimal.valueOf(1.0));
        }
    }

    @Nested
    @DisplayName("OrderingGrader (F43): Ghép câu ngôi sao JLPT")
    class OrderingTests {
        private final OrderingGrader grader = new OrderingGrader();

        @Test
        @DisplayName("Câu không có ngôi sao: đúng khi toàn bộ thứ tự mảnh từ khớp")
        void testOrderingFullSequence() {
            ExamQuestion q = ExamQuestion.builder()
                    .type(ExamQuestionType.ORDERING)
                    .points(BigDecimal.valueOf(2.0))
                    .choices(new ArrayList<>())
                    .answerKeys(new ArrayList<>())
                    .build();

            ExamChoice c1 = ExamChoice.builder().id(201L).sortOrder(1).text("わたしは").build();
            ExamChoice c2 = ExamChoice.builder().id(202L).sortOrder(2).text("にほんごを").build();
            ExamChoice c3 = ExamChoice.builder().id(203L).sortOrder(3).text("べんきょう").build();
            ExamChoice c4 = ExamChoice.builder().id(204L).sortOrder(4).text("します").build();
            q.addChoice(c1);
            q.addChoice(c2);
            q.addChoice(c3);
            q.addChoice(c4);

            q.addAnswerKey(ExamAnswerKey.builder().kind(ExamAnswerKind.ORDER).answerText("ORDER:2,1,4,3").build());

            // Khớp đúng theo ID phương án
            GradingResult res = grader.grade(q, "202,201,204,203");
            assertThat(res.getCorrect()).isTrue();
            assertThat(res.getPointsAwarded()).isEqualByComparingTo(BigDecimal.valueOf(2.0));

            // Khớp đúng theo thứ tự số "2,1,4,3"
            GradingResult resNumbers = grader.grade(q, "2, 1, 4, 3");
            assertThat(resNumbers.getCorrect()).isTrue();

            // Sai thứ tự
            GradingResult resWrong = grader.grade(q, "1, 2, 3, 4");
            assertThat(resWrong.getCorrect()).isFalse();
            assertThat(resWrong.getPointsAwarded()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Câu có ngôi sao (STAR:3): đúng khi mảnh ở vị trí thứ 3 đúng, dù các vị trí khác sai")
        void testOrderingStarPosition() {
            ExamQuestion q = ExamQuestion.builder()
                    .type(ExamQuestionType.ORDERING)
                    .points(BigDecimal.valueOf(3.0))
                    .choices(new ArrayList<>())
                    .answerKeys(new ArrayList<>())
                    .build();

            for (int i = 1; i <= 4; i++) {
                q.addChoice(ExamChoice.builder().id((long) (300 + i)).sortOrder(i).text("Mảnh " + i).build());
            }

            // Thứ tự đúng: 4, 2, 1, 3. Ngôi sao ở vị trí thứ 3 (mảnh đúng ở vị trí 3 là mảnh [1])
            q.addAnswerKey(ExamAnswerKey.builder().kind(ExamAnswerKind.ORDER).answerText("ORDER:4,2,1,3").build());
            q.addAnswerKey(ExamAnswerKey.builder().kind(ExamAnswerKind.ORDER).answerText("STAR:3").build());

            // Thí sinh ghép: 2, 4, 1, 3 -> Vị trí thứ 3 là mảnh 1 -> ĐÚNG vì ngôi sao đúng!
            GradingResult res = grader.grade(q, "2, 4, 1, 3");
            assertThat(res.getCorrect()).isTrue();
            assertThat(res.getPointsAwarded()).isEqualByComparingTo(BigDecimal.valueOf(3.0));
            assertThat(res.getFeedback()).contains("vị trí ngôi sao ★");

            // Thí sinh ghép: 4, 2, 3, 1 -> Vị trí thứ 3 là mảnh 3 (sai) -> 0 điểm
            GradingResult resWrong = grader.grade(q, "4, 2, 3, 1");
            assertThat(resWrong.getCorrect()).isFalse();
            assertThat(resWrong.getPointsAwarded()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("TranslationGrader (F44): So khớp tương đối Levenshtein (90%, 75%, <75%)")
    class TranslationTests {
        private final TranslationGrader grader = new TranslationGrader();

        @Test
        @DisplayName("Các mốc chấm điểm tương đối: >= 90% (100%), 75% đến < 90% (50%), < 75% (0%)")
        void testTranslationThresholds() {
            ExamQuestion q = ExamQuestion.builder()
                    .type(ExamQuestionType.TRANSLATION)
                    .points(BigDecimal.valueOf(4.0))
                    .answerKeys(new ArrayList<>())
                    .build();

            // Khóa mẫu: 20 ký tự
            // "わたしはにほんごのせんせいです"
            q.addAnswerKey(ExamAnswerKey.builder()
                    .kind(ExamAnswerKind.SAMPLE)
                    .answerText("わたしはにほんごのせんせいです。")
                    .build());

            // 1. Đúng hoàn toàn: 100% điểm
            GradingResult full = grader.grade(q, "わたしはにほんごのせんせいです");
            assertThat(full.getCorrect()).isTrue();
            assertThat(full.getPointsAwarded()).isEqualByComparingTo(BigDecimal.valueOf(4.0));
            assertThat(full.getSimilarity()).isEqualTo(1.0);
            assertThat(full.getFeedback()).contains("tham khảo");

            // 2. Sai 1 ký tự (13/14 ký tự ~ 92.8% >= 90%): 100% điểm
            GradingResult high = grader.grade(q, "わたしはにほんごのせんせいだす");
            assertThat(high.getCorrect()).isTrue();
            assertThat(high.getPointsAwarded()).isEqualByComparingTo(BigDecimal.valueOf(4.0));
            assertThat(high.getSimilarity()).isGreaterThanOrEqualTo(0.90);

            // 3. Sai 3 ký tự (11/14 ~ 78.5% nằm trong [75%, 90%)): 50% điểm (2.0 đ)
            GradingResult partial = grader.grade(q, "わたしはにほんごのせんせX Y Z");
            assertThat(partial.getPointsAwarded()).isEqualByComparingTo(BigDecimal.valueOf(2.0));
            assertThat(partial.getFeedback()).contains("50% số điểm");

            // 4. Sai nhiều (< 75%): 0 điểm
            GradingResult fail = grader.grade(q, "わたしはがくせいです");
            assertThat(fail.getCorrect()).isFalse();
            assertThat(fail.getPointsAwarded()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(fail.getFeedback()).contains("< 75%");
        }
    }

    @Nested
    @DisplayName("EssayGrader (F45): Tự luận không chấm tự động, kiểm tra từ khóa")
    class EssayTests {
        private final EssayGrader grader = new EssayGrader();

        @Test
        @DisplayName("Câu luận: correct = null, points = 0, trích xuất từ khóa có/chưa có")
        void testEssayKeywordExtraction() {
            ExamQuestion q = ExamQuestion.builder()
                    .type(ExamQuestionType.ESSAY)
                    .points(BigDecimal.valueOf(5.0))
                    .answerKeys(new ArrayList<>())
                    .build();

            q.addAnswerKey(ExamAnswerKey.builder().kind(ExamAnswerKind.SAMPLE).answerText("Bài mẫu đoạn văn...").build());
            q.addAnswerKey(ExamAnswerKey.builder().kind(ExamAnswerKind.KEYWORD).answerText("週末").build());
            q.addAnswerKey(ExamAnswerKey.builder().kind(ExamAnswerKind.KEYWORD).answerText("友達").build());
            q.addAnswerKey(ExamAnswerKey.builder().kind(ExamAnswerKind.KEYWORD).answerText("旅行").build());

            String studentEssay = "週末に家族と一緒に海へ行きました。友達も来ました。";
            GradingResult res = grader.grade(q, studentEssay);

            assertThat(res.getCorrect()).isNull();
            assertThat(res.getPointsAwarded()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(res.getMatchedKeywords()).containsExactlyInAnyOrder("週末", "友達");
            assertThat(res.getMissingKeywords()).containsExactly("旅行");
        }
    }
}

