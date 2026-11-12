package com.nihon.service;

import com.nihon.dto.KanjiPracticeMode;
import com.nihon.dto.PracticeKanjiAnswerForm;
import com.nihon.dto.PracticeKanjiQuestionDto;
import com.nihon.dto.PracticeKanjiResultDto;
import com.nihon.entity.KanjiEntry;
import com.nihon.entity.Topic;
import com.nihon.repository.KanjiEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Kiểm thử đơn vị cho KanjiPracticeService (F22, F23).
 */
@ExtendWith(MockitoExtension.class)
class KanjiPracticeServiceTest {

    @Mock
    private KanjiEntryRepository kanjiEntryRepository;

    @InjectMocks
    private KanjiPracticeService kanjiPracticeService;

    private KanjiEntry kanjiTsu;
    private KanjiEntry kanjiFish;
    private KanjiEntry kanjiMissingReadings;
    private Topic testTopic;

    @BeforeEach
    void setUp() {
        testTopic = new Topic();
        testTopic.setId(1L);
        testTopic.setName("Bài 1");

        // Chữ 通 (THÔNG): On = ツウ, ツ; Kun = とお-る, とお-す, かよ-う
        kanjiTsu = new KanjiEntry();
        kanjiTsu.setId(10L);
        kanjiTsu.setCharacter("通");
        kanjiTsu.setHanViet("THÔNG");
        kanjiTsu.setMeaningVi("Đi qua, thông suốt");
        kanjiTsu.setOnyomi("ツウ, ツ");
        kanjiTsu.setKunyomi("とお-る, とお-す, かよ-う");
        kanjiTsu.setTopic(testTopic);

        // Chữ 魚 (NGƯ): On = ギョ; Kun = さかな, -うお
        kanjiFish = new KanjiEntry();
        kanjiFish.setId(11L);
        kanjiFish.setCharacter("魚");
        kanjiFish.setHanViet("NGƯ");
        kanjiFish.setMeaningVi("Con cá");
        kanjiFish.setOnyomi("ギョ");
        kanjiFish.setKunyomi("さかな, -");
        kanjiFish.setTopic(testTopic);

        // Kanji thiếu âm đọc (chỉ có nghĩa/Hán Việt)
        kanjiMissingReadings = new KanjiEntry();
        kanjiMissingReadings.setId(12L);
        kanjiMissingReadings.setCharacter("々");
        kanjiMissingReadings.setHanViet("ĐỒNG");
        kanjiMissingReadings.setMeaningVi("Dấu lặp");
        kanjiMissingReadings.setOnyomi(null);
        kanjiMissingReadings.setKunyomi("-");
        kanjiMissingReadings.setTopic(testTopic);
    }

    // =========================================================================
    // 1. Chuẩn hóa và trích xuất âm đọc (F22)
    // =========================================================================

    @Test
    @DisplayName("F22: Trích xuất và chuẩn hóa âm đọc On (Katakana -> Hiragana) và Kun (bỏ dấu '-' và phần sau)")
    void extractValidReadings_normalizesCorrectly() {
        List<String> readings = kanjiPracticeService.extractValidReadings(kanjiTsu);

        // Onyomi: "ツウ" -> "つう", "ツ" -> "つ"
        // Kunyomi: "とお-る" -> "とお", "とお-す" -> "とお", "かよ-う" -> "かよ"
        assertThat(readings).containsExactly("つう", "つ", "とお", "かよ");
    }

    @Test
    @DisplayName("F22: Loại bỏ dấu '-' độc lập khi nguồn ghi không có âm Kun")
    void extractValidReadings_ignoresStandaloneDash() {
        List<String> readings = kanjiPracticeService.extractValidReadings(kanjiFish);

        // Onyomi: "ギョ" -> "ぎょ"
        // Kunyomi: "さかな", "-" -> bỏ qua "-"
        assertThat(readings).containsExactly("ぎょ", "さかな");
    }

    @Test
    @DisplayName("F22: Chấm điểm KANJI_TO_READING chấp nhận bất kỳ âm On hoặc Kun hợp lệ (Hiragana hoặc Katakana)")
    void isReadingCorrect_acceptsAnyValidOnOrKun() {
        // Đúng với âm On Katakana
        assertThat(kanjiPracticeService.isReadingCorrect("ツウ", kanjiTsu)).isTrue();
        // Đúng với âm On Hiragana
        assertThat(kanjiPracticeService.isReadingCorrect("つう", kanjiTsu)).isTrue();
        // Đúng với âm On ngắn
        assertThat(kanjiPracticeService.isReadingCorrect("つ", kanjiTsu)).isTrue();
        // Đúng với âm Kun đã bỏ phần sau dấu "-"
        assertThat(kanjiPracticeService.isReadingCorrect("とお", kanjiTsu)).isTrue();
        assertThat(kanjiPracticeService.isReadingCorrect("かよ", kanjiTsu)).isTrue();

        // Đúng dù có khoảng trắng thừa
        assertThat(kanjiPracticeService.isReadingCorrect("  つう  ", kanjiTsu)).isTrue();

        // Sai với âm không khớp
        assertThat(kanjiPracticeService.isReadingCorrect("ぎょ", kanjiTsu)).isFalse();
        assertThat(kanjiPracticeService.isReadingCorrect("", kanjiTsu)).isFalse();
        assertThat(kanjiPracticeService.isReadingCorrect(null, kanjiTsu)).isFalse();
    }

    // =========================================================================
    // 2. Chấm điểm dạng MEANING_TO_KANJI và KANJI_CHOICE (F22, F23)
    // =========================================================================

    @Test
    @DisplayName("F22: Chấm điểm MEANING_TO_KANJI đúng nếu nhập bằng đúng ký tự Kanji")
    void checkAnswer_meaningToKanji_evaluatesCorrectly() {
        when(kanjiEntryRepository.findById(10L)).thenReturn(Optional.of(kanjiTsu));

        PracticeKanjiAnswerForm correctForm = PracticeKanjiAnswerForm.builder()
                .kanjiId(10L)
                .mode("MEANING_TO_KANJI")
                .answer("通")
                .build();

        PracticeKanjiResultDto correctResult = kanjiPracticeService.checkAnswer(correctForm, new MockHttpSession());
        assertThat(correctResult.isCorrect()).isTrue();
        assertThat(correctResult.getExpectedAnswer()).contains("通");

        PracticeKanjiAnswerForm wrongForm = PracticeKanjiAnswerForm.builder()
                .kanjiId(10L)
                .mode("MEANING_TO_KANJI")
                .answer("魚")
                .build();

        PracticeKanjiResultDto wrongResult = kanjiPracticeService.checkAnswer(wrongForm, new MockHttpSession());
        assertThat(wrongResult.isCorrect()).isFalse();
    }

    @Test
    @DisplayName("F23: Chấm điểm KANJI_CHOICE xác thực qua session và so khớp ID")
    void checkAnswer_kanjiChoice_validatesSessionAndMatchesId() {
        when(kanjiEntryRepository.findById(10L)).thenReturn(Optional.of(kanjiTsu));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(KanjiPracticeService.SESSION_KANJI_CHOICE_ID, 10L);
        session.setAttribute(KanjiPracticeService.SESSION_KANJI_CHOICE_OPTION_IDS, List.of(10L, 11L, 12L, 13L));

        // 1. Chọn đúng đáp án 10L
        PracticeKanjiAnswerForm correctForm = PracticeKanjiAnswerForm.builder()
                .kanjiId(10L)
                .mode("KANJI_CHOICE")
                .choiceKanjiId(10L)
                .build();

        PracticeKanjiResultDto result = kanjiPracticeService.checkAnswer(correctForm, session);
        assertThat(result.isCorrect()).isTrue();

        // 2. Session đã bị xóa sau khi chấm xong
        assertThat(session.getAttribute(KanjiPracticeService.SESSION_KANJI_CHOICE_ID)).isNull();

        // 3. Nộp lại khi session đã hết hạn -> ném ngoại lệ
        assertThatThrownBy(() -> kanjiPracticeService.checkAnswer(correctForm, session))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("F23: Từ chối lựa chọn trắc nghiệm ngoài session (chống gian lận ID)")
    void checkAnswer_kanjiChoice_rejectsOptionOutsideSession() {
        when(kanjiEntryRepository.findById(10L)).thenReturn(Optional.of(kanjiTsu));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(KanjiPracticeService.SESSION_KANJI_CHOICE_ID, 10L);
        session.setAttribute(KanjiPracticeService.SESSION_KANJI_CHOICE_OPTION_IDS, List.of(10L, 11L, 12L, 13L));

        PracticeKanjiAnswerForm tamperedForm = PracticeKanjiAnswerForm.builder()
                .kanjiId(10L)
                .mode("KANJI_CHOICE")
                .choiceKanjiId(9999L) // ID không nằm trong sessionOptionIds
                .build();

        assertThatThrownBy(() -> kanjiPracticeService.checkAnswer(tamperedForm, session))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Lựa chọn không hợp lệ hoặc nằm ngoài danh sách.");
    }

    // =========================================================================
    // 3. Lọc bỏ Kanji thiếu dữ liệu cho từng dạng bài (F22, F23)
    // =========================================================================

    @Test
    @DisplayName("Loại Kanji không có âm On/Kun khỏi dạng KANJI_TO_READING")
    void isEligibleForMode_filtersOutMissingReadings() {
        assertThat(kanjiPracticeService.isEligibleForMode(kanjiTsu, KanjiPracticeMode.KANJI_TO_READING, 10)).isTrue();
        assertThat(kanjiPracticeService.isEligibleForMode(kanjiMissingReadings, KanjiPracticeMode.KANJI_TO_READING, 10)).isFalse();
    }

    @Test
    @DisplayName("Loại Kanji khỏi KANJI_CHOICE khi tổng số lượng khả dụng nhỏ hơn 4")
    void isEligibleForMode_requiresAtLeast4KanjiForChoice() {
        assertThat(kanjiPracticeService.isEligibleForMode(kanjiTsu, KanjiPracticeMode.KANJI_CHOICE, 3)).isFalse();
        assertThat(kanjiPracticeService.isEligibleForMode(kanjiTsu, KanjiPracticeMode.KANJI_CHOICE, 4)).isTrue();
    }

    @Test
    @DisplayName("getNextQuestion trả về Optional.empty() khi không có Kanji nào đủ điều kiện")
    void getNextQuestion_returnsEmptyWhenNoEligibleKanji() {
        when(kanjiEntryRepository.findEligibleKanji(eq(1L)))
                .thenReturn(List.of(kanjiMissingReadings));

        Optional<PracticeKanjiQuestionDto> question = kanjiPracticeService.getNextQuestion(
                1L, KanjiPracticeMode.KANJI_TO_READING, null, null, new MockHttpSession());

        assertThat(question).isEmpty();
    }

    @Test
    @DisplayName("getNextQuestion lọc đúng theo allowedKanjiIds khi ở chế độ ôn từ sai")
    void getNextQuestion_filtersByAllowedKanjiIds() {
        when(kanjiEntryRepository.findEligibleKanjiWithIds(eq(1L), eq(Set.of(10L))))
                .thenReturn(List.of(kanjiTsu));

        Optional<PracticeKanjiQuestionDto> question = kanjiPracticeService.getNextQuestion(
                1L, KanjiPracticeMode.KANJI_TO_READING, null, Set.of(10L), new MockHttpSession());

        assertThat(question).isPresent();
        assertThat(question.get().getKanjiId()).isEqualTo(10L);
    }
}

