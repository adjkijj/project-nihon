package com.nihon.conjugation;

import com.nihon.dto.verb.PracticeVerbAnswerForm;
import com.nihon.dto.verb.PracticeVerbResultDto;
import com.nihon.entity.Verb;
import com.nihon.repository.VerbRepository;
import com.nihon.service.StudyProgressService;
import com.nihon.service.VerbPracticeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Kiểm thử khảo sát dạng câu hỏi chia thể trong VerbPracticeService:
 * 1. Danh sách các dạng câu hỏi chia thể hiện có trong TargetForm chỉ bao gồm:
 *    TE, RU, TA, NAI, MASU, RANDOM.
 * 2. Xác thực dạng câu hỏi "Te phủ định" (ví dụ: TE_NEGATIVE / TE_PHU_DINH) CHƯA TỒN TẠI trong hệ thống.
 * 3. Với câu hỏi thể Te (TargetForm.TE), KHÔNG CHẤP NHẬN dạng masu phủ định (ません).
 */
class VerbPracticeTeNegativeFormTest {

    private VerbRepository verbRepository;
    private StudyProgressService studyProgressService;
    private VerbPracticeService verbPracticeService;

    private Verb verbEat;
    private Verb verbDrink;

    @BeforeEach
    void setUp() {
        verbRepository = mock(VerbRepository.class);
        studyProgressService = mock(StudyProgressService.class);
        verbPracticeService = new VerbPracticeService(verbRepository, studyProgressService);

        verbEat = new Verb();
        verbEat.setId(1L);
        verbEat.setDictionaryForm("たべる");
        verbEat.setMasuForm("たべます");
        verbEat.setKanjiText("食べる");
        verbEat.setVerbGroup(2);
        verbEat.setMeaningVi("Ăn");

        verbDrink = new Verb();
        verbDrink.setId(2L);
        verbDrink.setDictionaryForm("のむ");
        verbDrink.setMasuForm("のみます");
        verbDrink.setKanjiText("飲む");
        verbDrink.setVerbGroup(1);
        verbDrink.setMeaningVi("Uống");
    }

    @Test
    @DisplayName("1. TargetForm chỉ hỗ trợ chính xác 5 thể cụ thể (TE, RU, TA, NAI, MASU) và RANDOM")
    void testTargetFormList() {
        List<String> formNames = Arrays.stream(TargetForm.values())
                .map(Enum::name)
                .toList();

        assertThat(formNames).containsExactlyInAnyOrder("TE", "RU", "TA", "NAI", "MASU", "RANDOM");
    }

    @Test
    @DisplayName("2. Xác thực dạng câu hỏi 'Te phủ định' (TE_NEGATIVE) chưa tồn tại trong TargetForm")
    void testTeNegativeDoesNotExistInTargetForm() {
        boolean hasTeNegative = Arrays.stream(TargetForm.values())
                .anyMatch(f -> f.name().contains("NEGATIVE") || f.name().contains("PHU_DINH") || f.name().equals("TE_NAI"));

        assertThat(hasTeNegative).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"たべません", "食べません", "のみません", "飲みません", "しません", "きません"})
    @DisplayName("3. isFormMatch: Câu hỏi thể Te (TargetForm.TE) KHÔNG chấp nhận dạng masu phủ định (ません)")
    void testTeFormRejectsMasuNegative(String masuNegativeAnswer) {
        // Động từ たべる (Nhóm 2)
        boolean matchEat = VerbConjugator.isFormMatch("たべる", 2, "食べる", TargetForm.TE, masuNegativeAnswer);
        assertThat(matchEat).isFalse();

        // Động từ のむ (Nhóm 1)
        boolean matchDrink = VerbConjugator.isFormMatch("のむ", 1, "飲む", TargetForm.TE, masuNegativeAnswer);
        assertThat(matchDrink).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "たべる, 2, 食べる, たべて, true",
            "たべる, 2, 食べる, 食べて, true",
            "たべる, 2, 食べる, たべません, false",
            "たべる, 2, 食べる, 食べません, false",
            "のむ, 1, 飲む, のんで, true",
            "のむ, 1, 飲む, 飲んで, true",
            "のむ, 1, 飲む, のみません, false",
            "のむ, 1, 飲む, 飲みません, false"
    })
    @DisplayName("4. isFormMatch: Thể Te chỉ chấp nhận dạng thể Te khẳng định, từ chối dạng ません")
    void testTeFormAcceptsOnlyTeAndRejectsMasuNegative(String dict, int group, String kanji, String answer, boolean expected) {
        boolean result = VerbConjugator.isFormMatch(dict, group, kanji, TargetForm.TE, answer);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("5. VerbPracticeService.checkAnswer: Chấm điểm câu hỏi thể Te từ chối câu trả lời masu phủ định (ません)")
    void testCheckAnswerRejectsMasuNegativeForTeForm() {
        when(verbRepository.findById(1L)).thenReturn(Optional.of(verbEat));

        PracticeVerbAnswerForm form = new PracticeVerbAnswerForm();
        form.setVerbId(1L);
        form.setTargetForm("TE");
        form.setAnswer("たべません");
        form.setMode("INPUT");

        PracticeVerbResultDto result = verbPracticeService.checkAnswer(form, null);

        assertThat(result.isCorrect()).isFalse();
        assertThat(result.getExpectedKana()).isEqualTo("たべて");
        assertThat(result.getExpectedKanji()).isEqualTo("食べて");
    }
}
