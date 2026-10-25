package com.nihon.service;

import com.nihon.dto.PracticeAnswerForm;
import com.nihon.dto.PracticeQuestionDto;
import com.nihon.dto.PracticeResultDto;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.repository.VocabularyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.text.Normalizer;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeServiceTest {

    @Mock
    private VocabularyRepository vocabularyRepository;

    @InjectMocks
    private PracticeService practiceService;

    private Topic sampleTopic;
    private Vocabulary vocabWithKanji;
    private Vocabulary vocabKanaOnly;
    private Vocabulary vocabKanjiMissingReading;
    private Vocabulary vocabMultipleAlternatives;

    @BeforeEach
    void setUp() {
        sampleTopic = new Topic();
        sampleTopic.setId(1L);
        sampleTopic.setName("1. Chào hỏi");

        // 1. Từ có Kanji và có cách đọc chuẩn
        vocabWithKanji = new Vocabulary();
        vocabWithKanji.setId(10L);
        vocabWithKanji.setJapaneseText("私（わたし）");
        vocabWithKanji.setReading("わたし");
        vocabWithKanji.setRomaji("watashi");
        vocabWithKanji.setMeaningVi("Tôi");
        vocabWithKanji.setHasKanji(true);
        vocabWithKanji.setTopic(sampleTopic);

        // 2. Từ chỉ có Kana (không có Kanji)
        vocabKanaOnly = new Vocabulary();
        vocabKanaOnly.setId(20L);
        vocabKanaOnly.setJapaneseText("すみません");
        vocabKanaOnly.setReading(null);
        vocabKanaOnly.setRomaji("sumimasen");
        vocabKanaOnly.setMeaningVi("Xin lỗi");
        vocabKanaOnly.setHasKanji(false);
        vocabKanaOnly.setTopic(sampleTopic);

        // 3. Từ có Kanji nhưng nguồn dữ liệu thiếu cách đọc
        vocabKanjiMissingReading = new Vocabulary();
        vocabKanjiMissingReading.setId(30L);
        vocabKanjiMissingReading.setJapaneseText("1歳");
        vocabKanjiMissingReading.setReading(null);
        vocabKanjiMissingReading.setRomaji("issai");
        vocabKanjiMissingReading.setMeaningVi("1 tuổi");
        vocabKanjiMissingReading.setHasKanji(true);
        vocabKanjiMissingReading.setTopic(sampleTopic);

        // 4. Từ có nhiều cách đọc và nhiều nghĩa thay thế
        vocabMultipleAlternatives = new Vocabulary();
        vocabMultipleAlternatives.setId(40L);
        vocabMultipleAlternatives.setJapaneseText("父（ちち、とう）");
        vocabMultipleAlternatives.setReading("ちち、とう");
        vocabMultipleAlternatives.setRomaji("chichi, tou");
        vocabMultipleAlternatives.setMeaningVi("bố, cha, ba");
        vocabMultipleAlternatives.setHasKanji(true);
        vocabMultipleAlternatives.setTopic(sampleTopic);
    }

    @Test
    @DisplayName("Câu trả lời đúng hoàn toàn cho từ có Kanji (cả cách đọc Hiragana và nghĩa tiếng Việt)")
    void testCheckAnswer_allCorrect_withKanjiAndReading() {
        when(vocabularyRepository.findById(10L)).thenReturn(Optional.of(vocabWithKanji));

        PracticeAnswerForm form = new PracticeAnswerForm();
        form.setVocabId(10L);
        form.setUserReading("わたし");
        form.setUserMeaning("Tôi");

        PracticeResultDto result = practiceService.checkAnswer(form);

        assertThat(result.isOverallCorrect()).isTrue();
        assertThat(result.getReadingCorrect()).isTrue();
        assertThat(result.isMeaningCorrect()).isTrue();
        assertThat(result.isRequireReading()).isTrue();
        assertThat(result.isReadingMissingInSource()).isFalse();
        assertThat(result.getExpectedReading()).isEqualTo("わたし");
        assertThat(result.getExpectedMeaning()).isEqualTo("Tôi");
    }

    @Test
    @DisplayName("Câu trả lời sai nghĩa tiếng Việt")
    void testCheckAnswer_wrongMeaning() {
        when(vocabularyRepository.findById(10L)).thenReturn(Optional.of(vocabWithKanji));

        PracticeAnswerForm form = new PracticeAnswerForm();
        form.setVocabId(10L);
        form.setUserReading("わたし");
        form.setUserMeaning("Bạn"); // Sai

        PracticeResultDto result = practiceService.checkAnswer(form);

        assertThat(result.isOverallCorrect()).isFalse();
        assertThat(result.getReadingCorrect()).isTrue();
        assertThat(result.isMeaningCorrect()).isFalse();
    }

    @Test
    @DisplayName("Câu trả lời sai cách đọc Hiragana")
    void testCheckAnswer_wrongReading() {
        when(vocabularyRepository.findById(10L)).thenReturn(Optional.of(vocabWithKanji));

        PracticeAnswerForm form = new PracticeAnswerForm();
        form.setVocabId(10L);
        form.setUserReading("あなた"); // Sai
        form.setUserMeaning("Tôi"); // Đúng

        PracticeResultDto result = practiceService.checkAnswer(form);

        assertThat(result.isOverallCorrect()).isFalse();
        assertThat(result.getReadingCorrect()).isFalse();
        assertThat(result.isMeaningCorrect()).isTrue();
    }

    @Test
    @DisplayName("Câu trả lời rỗng hoặc null được coi là sai")
    void testCheckAnswer_emptyInputs() {
        when(vocabularyRepository.findById(10L)).thenReturn(Optional.of(vocabWithKanji));

        PracticeAnswerForm form = new PracticeAnswerForm();
        form.setVocabId(10L);
        form.setUserReading("");
        form.setUserMeaning(null);

        PracticeResultDto result = practiceService.checkAnswer(form);

        assertThat(result.isOverallCorrect()).isFalse();
        assertThat(result.getReadingCorrect()).isFalse();
        assertThat(result.isMeaningCorrect()).isFalse();
    }

    @Test
    @DisplayName("Loại bỏ khoảng trắng đầu/cuối và thu gọn khoảng trắng trước khi so sánh")
    void testCheckAnswer_withExtraWhitespace_isNormalizedAndAccepted() {
        when(vocabularyRepository.findById(10L)).thenReturn(Optional.of(vocabWithKanji));

        PracticeAnswerForm form = new PracticeAnswerForm();
        form.setVocabId(10L);
        form.setUserReading("   わたし   ");
        form.setUserMeaning("   Tôi   ");

        PracticeResultDto result = practiceService.checkAnswer(form);

        assertThat(result.isOverallCorrect()).isTrue();
        assertThat(result.getReadingCorrect()).isTrue();
        assertThat(result.isMeaningCorrect()).isTrue();
    }

    @Test
    @DisplayName("Chuẩn hóa Unicode NFD sang NFC cho tiếng Việt và tiếng Nhật")
    void testCheckAnswer_unicodeNormalization_nfdVsNfc() {
        when(vocabularyRepository.findById(10L)).thenReturn(Optional.of(vocabWithKanji));

        // Chuỗi "Tôi" ở dạng tổ hợp NFD (Decomposed)
        String nfdMeaning = Normalizer.normalize("Tôi", Normalizer.Form.NFD);
        String nfdReading = Normalizer.normalize("わたし", Normalizer.Form.NFD);

        PracticeAnswerForm form = new PracticeAnswerForm();
        form.setVocabId(10L);
        form.setUserReading(nfdReading);
        form.setUserMeaning(nfdMeaning);

        PracticeResultDto result = practiceService.checkAnswer(form);

        assertThat(result.isOverallCorrect()).isTrue();
        assertThat(result.getReadingCorrect()).isTrue();
        assertThat(result.isMeaningCorrect()).isTrue();
    }

    @Test
    @DisplayName("Từ có Kanji nhưng thiếu cách đọc trong dữ liệu nguồn -> đánh dấu chưa đủ dữ liệu và bỏ qua kiểm tra Hiragana")
    void testCheckAnswer_missingReadingInSource_doesNotGuessAndSkipsReadingCheck() {
        when(vocabularyRepository.findById(30L)).thenReturn(Optional.of(vocabKanjiMissingReading));

        PracticeAnswerForm form = new PracticeAnswerForm();
        form.setVocabId(30L);
        form.setUserReading(null); // Không cần nhập
        form.setUserMeaning("1 tuổi"); // Đúng nghĩa

        PracticeResultDto result = practiceService.checkAnswer(form);

        assertThat(result.isOverallCorrect()).isTrue();
        assertThat(result.getReadingCorrect()).isNull(); // Không áp dụng
        assertThat(result.isRequireReading()).isFalse();
        assertThat(result.isReadingMissingInSource()).isTrue();
        assertThat(result.isMeaningCorrect()).isTrue();
    }

    @Test
    @DisplayName("Từ không có Kanji (chỉ có Kana) -> không yêu cầu nhập Hiragana, chỉ cần nghĩa đúng")
    void testCheckAnswer_wordWithoutKanji_doesNotRequireReading() {
        when(vocabularyRepository.findById(20L)).thenReturn(Optional.of(vocabKanaOnly));

        PracticeAnswerForm form = new PracticeAnswerForm();
        form.setVocabId(20L);
        form.setUserReading(""); // Không yêu cầu
        form.setUserMeaning("Xin lỗi");

        PracticeResultDto result = practiceService.checkAnswer(form);

        assertThat(result.isOverallCorrect()).isTrue();
        assertThat(result.getReadingCorrect()).isNull();
        assertThat(result.isRequireReading()).isFalse();
        assertThat(result.isReadingMissingInSource()).isFalse();
        assertThat(result.isMeaningCorrect()).isTrue();
    }

    @Test
    @DisplayName("Chấp nhận bất kỳ cách đọc thay thế nào nếu được khai báo rõ ràng trong dữ liệu")
    void testCheckAnswer_multipleAlternativeReadingsInSource() {
        when(vocabularyRepository.findById(40L)).thenReturn(Optional.of(vocabMultipleAlternatives));

        // Cách đọc 1: ちち
        PracticeAnswerForm form1 = new PracticeAnswerForm();
        form1.setVocabId(40L);
        form1.setUserReading("ちち");
        form1.setUserMeaning("bố");
        assertThat(practiceService.checkAnswer(form1).isOverallCorrect()).isTrue();

        // Cách đọc 2: とう
        PracticeAnswerForm form2 = new PracticeAnswerForm();
        form2.setVocabId(40L);
        form2.setUserReading("とう");
        form2.setUserMeaning("cha");
        assertThat(practiceService.checkAnswer(form2).isOverallCorrect()).isTrue();
    }

    @Test
    @DisplayName("Chấp nhận các nghĩa tiếng Việt thay thế được phân tách trong dữ liệu nguồn")
    void testCheckAnswer_multipleAlternativeMeaningsInSource() {
        when(vocabularyRepository.findById(40L)).thenReturn(Optional.of(vocabMultipleAlternatives));

        PracticeAnswerForm form = new PracticeAnswerForm();
        form.setVocabId(40L);
        form.setUserReading("ちち");
        form.setUserMeaning("ba"); // "ba" là 1 trong các nghĩa của "bố, cha, ba"

        PracticeResultDto result = practiceService.checkAnswer(form);
        assertThat(result.isMeaningCorrect()).isTrue();
        assertThat(result.isOverallCorrect()).isTrue();
    }

    @Test
    @DisplayName("ID từ vựng không tồn tại sẽ ném NoSuchElementException an toàn")
    void testCheckAnswer_nonExistentId_throwsNoSuchElementException() {
        when(vocabularyRepository.findById(999L)).thenReturn(Optional.empty());

        PracticeAnswerForm form = new PracticeAnswerForm();
        form.setVocabId(999L);
        form.setUserMeaning("Test");

        assertThatThrownBy(() -> practiceService.checkAnswer(form))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("Lấy câu hỏi ngẫu nhiên và loại trừ ID câu hỏi vừa làm")
    void testGetNextQuestion_excludesCurrentId() {
        when(vocabularyRepository.findIdsByTopicId(1L)).thenReturn(List.of(10L, 20L));
        when(vocabularyRepository.findById(20L)).thenReturn(Optional.of(vocabKanaOnly));

        // excludeId là 10L -> ứng viên còn lại phải là 20L
        Optional<PracticeQuestionDto> q = practiceService.getNextQuestion(1L, 10L);

        assertThat(q).isPresent();
        assertThat(q.get().getVocabId()).isEqualTo(20L);
    }
}
