package com.nihon.service.exam;

import com.nihon.entity.ExamAnswerKind;
import com.nihon.entity.ExamQuestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExamValidatorTest {

    @Nested
    @DisplayName("Thẩm định câu hỏi trắc nghiệm (MULTIPLE_CHOICE)")
    class MultipleChoiceTests {

        @Test
        @DisplayName("Hợp lệ: 4 phương án, có 1 đáp án đúng hợp lệ")
        void validMultipleChoice() {
            List<String> choices = List.of("A", "B", "C", "D");
            Integer correctIndex = 2;

            assertThatCode(() -> ExamValidator.validateQuestion(
                    ExamQuestionType.MULTIPLE_CHOICE,
                    "これは何ですか。",
                    "Đây là cái gì?",
                    choices,
                    correctIndex,
                    Collections.emptyList()
            )).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Không hợp lệ: Ít hơn 2 lựa chọn")
        void invalidLessThanTwoChoices() {
            List<String> choices = List.of("Duy nhất 1 phương án");
            assertThatThrownBy(() -> ExamValidator.validateQuestion(
                    ExamQuestionType.MULTIPLE_CHOICE,
                    "テスト",
                    null,
                    choices,
                    1,
                    Collections.emptyList()
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("từ 2 đến 6");
        }

        @Test
        @DisplayName("Không hợp lệ: Nhiều hơn 6 lựa chọn")
        void invalidMoreThanSixChoices() {
            List<String> choices = List.of("1", "2", "3", "4", "5", "6", "7");
            assertThatThrownBy(() -> ExamValidator.validateQuestion(
                    ExamQuestionType.MULTIPLE_CHOICE,
                    "テスト",
                    null,
                    choices,
                    1,
                    Collections.emptyList()
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("từ 2 đến 6");
        }

        @Test
        @DisplayName("Không hợp lệ: Chưa chọn đáp án đúng")
        void invalidMissingCorrectIndex() {
            List<String> choices = List.of("A", "B", "C", "D");
            assertThatThrownBy(() -> ExamValidator.validateQuestion(
                    ExamQuestionType.MULTIPLE_CHOICE,
                    "テスト",
                    null,
                    choices,
                    null,
                    Collections.emptyList()
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("đáp án đúng");
        }

        @Test
        @DisplayName("Không hợp lệ: Chỉ số đáp án đúng ngoài khoảng")
        void invalidCorrectIndexOutOfRange() {
            List<String> choices = List.of("A", "B", "C", "D");
            assertThatThrownBy(() -> ExamValidator.validateQuestion(
                    ExamQuestionType.MULTIPLE_CHOICE,
                    "テスト",
                    null,
                    choices,
                    5,
                    Collections.emptyList()
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("từ 1 đến 4");
        }
    }

    @Nested
    @DisplayName("Thẩm định câu hỏi gõ phím (TYPING)")
    class TypingTests {

        @Test
        @DisplayName("Hợp lệ: Ít nhất 1 đáp án ACCEPTED")
        void validTyping() {
            List<ExamValidator.AnswerKeyItem> keys = List.of(
                    new ExamValidator.AnswerKeyItem(ExamAnswerKind.ACCEPTED, "nihon"),
                    new ExamValidator.AnswerKeyItem(ExamAnswerKind.ACCEPTED, "日本")
            );

            assertThatCode(() -> ExamValidator.validateQuestion(
                    ExamQuestionType.TYPING,
                    "にほん",
                    null,
                    Collections.emptyList(),
                    null,
                    keys
            )).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Không hợp lệ: Không có đáp án ACCEPTED nào")
        void invalidTypingNoAcceptedKey() {
            List<ExamValidator.AnswerKeyItem> keys = List.of(
                    new ExamValidator.AnswerKeyItem(ExamAnswerKind.SAMPLE, "câu mẫu")
            );

            assertThatThrownBy(() -> ExamValidator.validateQuestion(
                    ExamQuestionType.TYPING,
                    "にほん",
                    null,
                    Collections.emptyList(),
                    null,
                    keys
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ACCEPTED");
        }
    }

    @Nested
    @DisplayName("Thẩm định câu hỏi sắp xếp từ / câu sao JLPT (ORDERING)")
    class OrderingTests {

        @Test
        @DisplayName("Hợp lệ: 4 mảnh từ, ORDER hợp lệ và vị trí ngôi sao hợp lệ")
        void validOrderingWithStar() {
            List<String> choices = List.of("きのう", "ともだちと", "えいがを", "みにいきました");
            List<ExamValidator.AnswerKeyItem> keys = List.of(
                    new ExamValidator.AnswerKeyItem(ExamAnswerKind.ORDER, "ORDER:1,2,3,4"),
                    new ExamValidator.AnswerKeyItem(ExamAnswerKind.ORDER, "STAR:3")
            );

            assertThatCode(() -> ExamValidator.validateQuestion(
                    ExamQuestionType.ORDERING,
                    "_____ _____ __★__ _____ 。",
                    "Sắp xếp thành câu đúng nghĩa",
                    choices,
                    null,
                    keys
            )).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Không hợp lệ: Dưới 3 mảnh từ")
        void invalidLessThanThreeChoices() {
            List<String> choices = List.of("A", "B");
            List<ExamValidator.AnswerKeyItem> keys = List.of(
                    new ExamValidator.AnswerKeyItem(ExamAnswerKind.ORDER, "ORDER:1,2")
            );

            assertThatThrownBy(() -> ExamValidator.validateQuestion(
                    ExamQuestionType.ORDERING,
                    "Prompt",
                    null,
                    choices,
                    null,
                    keys
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("từ 3 đến 10");
        }

        @Test
        @DisplayName("Không hợp lệ: Thứ tự ORDER bị thiếu mảnh hoặc không phải hoán vị")
        void invalidOrderingPermutation() {
            List<String> choices = List.of("A", "B", "C", "D");
            List<ExamValidator.AnswerKeyItem> keys = List.of(
                    new ExamValidator.AnswerKeyItem(ExamAnswerKind.ORDER, "ORDER:1,2,2,4")
            );

            assertThatThrownBy(() -> ExamValidator.validateQuestion(
                    ExamQuestionType.ORDERING,
                    "Prompt",
                    null,
                    choices,
                    null,
                    keys
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("hoán vị");
        }

        @Test
        @DisplayName("Không hợp lệ: Vị trí ngôi sao ngoài số lượng mảnh từ")
        void invalidStarIndexOutOfRange() {
            List<String> choices = List.of("A", "B", "C", "D");
            List<ExamValidator.AnswerKeyItem> keys = List.of(
                    new ExamValidator.AnswerKeyItem(ExamAnswerKind.ORDER, "ORDER:1,2,3,4"),
                    new ExamValidator.AnswerKeyItem(ExamAnswerKind.ORDER, "STAR:5")
            );

            assertThatThrownBy(() -> ExamValidator.validateQuestion(
                    ExamQuestionType.ORDERING,
                    "Prompt",
                    null,
                    choices,
                    null,
                    keys
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ngôi sao");
        }
    }

    @Nested
    @DisplayName("Thẩm định giới hạn đề thi (Sections & Questions limits)")
    class ExamLimitsTests {

        @Test
        @DisplayName("Hợp lệ: 10 phần thi, 200 câu hỏi")
        void validExamLimits() {
            assertThatCode(() -> ExamValidator.validateExamLimits(10, 200))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Không hợp lệ: Vượt quá 10 phần thi")
        void invalidMoreThanTenSections() {
            assertThatThrownBy(() -> ExamValidator.validateExamLimits(11, 50))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tối đa 10 phần thi");
        }

        @Test
        @DisplayName("Không hợp lệ: Vượt quá 200 câu hỏi")
        void invalidMoreThanTwoHundredQuestions() {
            assertThatThrownBy(() -> ExamValidator.validateExamLimits(5, 201))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tối đa 200 câu hỏi");
        }
    }
}

