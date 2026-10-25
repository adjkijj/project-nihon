package com.nihon.service;

import com.nihon.dto.PracticeViJaAnswerForm;
import com.nihon.dto.PracticeViJaQuestionDto;
import com.nihon.dto.PracticeViJaResultDto;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeViJaServiceTest {

    @Mock
    private VocabularyRepository vocabularyRepository;

    @InjectMocks
    private PracticeService practiceService;

    private Topic sampleTopic;
    private Vocabulary vocabWithKanji;
    private Vocabulary vocabKanaOnly;
    private Vocabulary vocabWithSlashVariants;
    private Vocabulary vocabWithOrVariants;

    @BeforeEach
    void setUp() {
        sampleTopic = new Topic();
        sampleTopic.setId(1L);
        sampleTopic.setName("1. Chào hỏi");

        // 1. Từ có Kanji và furigana trong ngoặc
        vocabWithKanji = new Vocabulary();
        vocabWithKanji.setId(10L);
        vocabWithKanji.setJapaneseText("私（わたし）");
        vocabWithKanji.setReading("わたし");
        vocabWithKanji.setRomaji("watashi");
        vocabWithKanji.setMeaningVi("Tôi");
        vocabWithKanji.setHasKanji(true);
        vocabWithKanji.setTopic(sampleTopic);

        // 2. Từ chỉ có Kana
        vocabKanaOnly = new Vocabulary();
        vocabKanaOnly.setId(20L);
        vocabKanaOnly.setJapaneseText("すみません");
        vocabKanaOnly.setReading(null);
        vocabKanaOnly.setRomaji("sumimasen");
        vocabKanaOnly.setMeaningVi("Xin lỗi");
        vocabKanaOnly.setHasKanji(false);
        vocabKanaOnly.setTopic(sampleTopic);

        // 3. Từ có các biến thể phân tách bằng dấu slash '/'
        vocabWithSlashVariants = new Vocabulary();
        vocabWithSlashVariants.setId(30L);
        vocabWithSlashVariants.setJapaneseText("はい / ええ");
        vocabWithSlashVariants.setReading("はい");
        vocabWithSlashVariants.setRomaji("hai / ee");
        vocabWithSlashVariants.setMeaningVi("Vâng, ừ");
        vocabWithSlashVariants.setHasKanji(false);
        vocabWithSlashVariants.setTopic(sampleTopic);

        // 4. Từ có biến thể phân tách bằng "or<br />" được khai báo rõ ràng
        vocabWithOrVariants = new Vocabulary();
        vocabWithOrVariants.setId(40L);
        vocabWithOrVariants.setJapaneseText("よろしくお願(ねが)いします or<br />よろしくおねがいします");
        vocabWithOrVariants.setReading("よろしくおねがいします");
        vocabWithOrVariants.setRomaji("yoroshiku onegaishimasu");
        vocabWithOrVariants.setMeaningVi("Rất mong được giúp đỡ");
        vocabWithOrVariants.setHasKanji(true);
        vocabWithOrVariants.setTopic(sampleTopic);
    }

    @Test
    @DisplayName("Đáp án đúng: Người học gõ dạng Kanji chuẩn của từ (私)")
    void testCheckReverseAnswer_kanjiFormCorrect() {
        when(vocabularyRepository.findById(10L)).thenReturn(Optional.of(vocabWithKanji));

        PracticeViJaAnswerForm form = new PracticeViJaAnswerForm();
        form.setVocabId(10L);
        form.setUserJapanese("私");

        PracticeViJaResultDto result = practiceService.checkReverseAnswer(form);

        assertThat(result.isCorrect()).isTrue();
        assertThat(result.getUserJapanese()).isEqualTo("私");
        assertThat(result.getExpectedJapanese()).isEqualTo("私（わたし）");
    }

    @Test
    @DisplayName("Đáp án đúng: Người học gõ nguyên chuỗi được khai báo có ngoặc (私（わたし）)")
    void testCheckReverseAnswer_fullDeclaredStringCorrect() {
        when(vocabularyRepository.findById(10L)).thenReturn(Optional.of(vocabWithKanji));

        PracticeViJaAnswerForm form = new PracticeViJaAnswerForm();
        form.setVocabId(10L);
        form.setUserJapanese("私（わたし）");

        PracticeViJaResultDto result = practiceService.checkReverseAnswer(form);

        assertThat(result.isCorrect()).isTrue();
    }

    @Test
    @DisplayName("Đáp án sai: Tự ý thay Kanji bằng Hiragana (gõ わたし thay vì 私) -> Chấm sai")
    void testCheckReverseAnswer_replacingKanjiWithHiraganaIsRejected() {
        when(vocabularyRepository.findById(10L)).thenReturn(Optional.of(vocabWithKanji));

        PracticeViJaAnswerForm form = new PracticeViJaAnswerForm();
        form.setVocabId(10L);
        form.setUserJapanese("わたし"); // Từ này dạng chuẩn có Kanji 私

        PracticeViJaResultDto result = practiceService.checkReverseAnswer(form);

        assertThat(result.isCorrect()).isFalse();
        assertThat(result.getFeedbackMessage()).contains("yêu cầu viết bằng chữ Hán (Kanji)");
    }

    @Test
    @DisplayName("Đáp án sai: Tự ý thay tiếng Nhật bằng Romaji (gõ watashi) -> Chấm sai")
    void testCheckReverseAnswer_romajiRejected() {
        when(vocabularyRepository.findById(10L)).thenReturn(Optional.of(vocabWithKanji));

        PracticeViJaAnswerForm form = new PracticeViJaAnswerForm();
        form.setVocabId(10L);
        form.setUserJapanese("watashi");

        PracticeViJaResultDto result = practiceService.checkReverseAnswer(form);

        assertThat(result.isCorrect()).isFalse();
    }

    @Test
    @DisplayName("Đáp án đúng cho từ chỉ có Kana (すみません)")
    void testCheckReverseAnswer_kanaOnlyWordCorrect() {
        when(vocabularyRepository.findById(20L)).thenReturn(Optional.of(vocabKanaOnly));

        PracticeViJaAnswerForm form = new PracticeViJaAnswerForm();
        form.setVocabId(20L);
        form.setUserJapanese("すみません");

        PracticeViJaResultDto result = practiceService.checkReverseAnswer(form);

        assertThat(result.isCorrect()).isTrue();
    }

    @Test
    @DisplayName("Đáp án sai do khác biệt chính tả (ví dụ âm đục/âm trong: すみまぜん thay vì すみません)")
    void testCheckReverseAnswer_spellingDifferenceIsRejected() {
        when(vocabularyRepository.findById(20L)).thenReturn(Optional.of(vocabKanaOnly));

        PracticeViJaAnswerForm form = new PracticeViJaAnswerForm();
        form.setVocabId(20L);
        form.setUserJapanese("すみまぜん"); // Sai chính tả (ぜ thay vì せ)

        PracticeViJaResultDto result = practiceService.checkReverseAnswer(form);

        assertThat(result.isCorrect()).isFalse();
    }

    @Test
    @DisplayName("Đáp án rỗng hoặc null được coi là sai")
    void testCheckReverseAnswer_emptyOrNullIsFalse() {
        when(vocabularyRepository.findById(10L)).thenReturn(Optional.of(vocabWithKanji));

        PracticeViJaAnswerForm formEmpty = new PracticeViJaAnswerForm();
        formEmpty.setVocabId(10L);
        formEmpty.setUserJapanese("");
        assertThat(practiceService.checkReverseAnswer(formEmpty).isCorrect()).isFalse();

        PracticeViJaAnswerForm formNull = new PracticeViJaAnswerForm();
        formNull.setVocabId(10L);
        formNull.setUserJapanese(null);
        assertThat(practiceService.checkReverseAnswer(formNull).isCorrect()).isFalse();
    }

    @Test
    @DisplayName("Chuẩn hóa Unicode NFD sang NFC và loại bỏ khoảng trắng thừa đầu/cuối")
    void testCheckReverseAnswer_whitespaceAndUnicodeNormalization() {
        when(vocabularyRepository.findById(10L)).thenReturn(Optional.of(vocabWithKanji));

        String nfdKanji = Normalizer.normalize("私", Normalizer.Form.NFD);

        PracticeViJaAnswerForm vijaForm = new PracticeViJaAnswerForm();
        vijaForm.setVocabId(10L);
        vijaForm.setUserJapanese("   " + nfdKanji + "   ");

        PracticeViJaResultDto result = practiceService.checkReverseAnswer(vijaForm);

        assertThat(result.isCorrect()).isTrue();
    }

    @Test
    @DisplayName("Chấp nhận các biến thể phân tách bằng slash '/' được khai báo rõ ràng (はい / ええ)")
    void testCheckReverseAnswer_slashDeclaredVariants() {
        when(vocabularyRepository.findById(30L)).thenReturn(Optional.of(vocabWithSlashVariants));

        // Biến thể 1: はい
        PracticeViJaAnswerForm form1 = new PracticeViJaAnswerForm();
        form1.setVocabId(30L);
        form1.setUserJapanese("はい");
        assertThat(practiceService.checkReverseAnswer(form1).isCorrect()).isTrue();

        // Biến thể 2: ええ
        PracticeViJaAnswerForm form2 = new PracticeViJaAnswerForm();
        form2.setVocabId(30L);
        form2.setUserJapanese("ええ");
        assertThat(practiceService.checkReverseAnswer(form2).isCorrect()).isTrue();
    }

    @Test
    @DisplayName("Chấp nhận biến thể khai báo qua 'or<br />' (cả dạng Kanji chuẩn và dạng Kana được khai báo)")
    void testCheckReverseAnswer_orDeclaredVariants() {
        when(vocabularyRepository.findById(40L)).thenReturn(Optional.of(vocabWithOrVariants));

        // Dạng Kanji chuẩn: よろしくお願いします
        PracticeViJaAnswerForm formKanji = new PracticeViJaAnswerForm();
        formKanji.setVocabId(40L);
        formKanji.setUserJapanese("よろしくお願いします");
        assertThat(practiceService.checkReverseAnswer(formKanji).isCorrect()).isTrue();

        // Dạng Kana được khai báo rõ ràng trong dữ liệu: よろしくおねがいします
        PracticeViJaAnswerForm formKana = new PracticeViJaAnswerForm();
        formKana.setVocabId(40L);
        formKana.setUserJapanese("よろしくおねがいします");
        assertThat(practiceService.checkReverseAnswer(formKana).isCorrect()).isTrue();
    }

    @Test
    @DisplayName("Mã ID từ vựng không tồn tại sẽ ném NoSuchElementException an toàn")
    void testCheckReverseAnswer_nonExistentIdThrowsException() {
        when(vocabularyRepository.findById(999L)).thenReturn(Optional.empty());

        PracticeViJaAnswerForm form = new PracticeViJaAnswerForm();
        form.setVocabId(999L);
        form.setUserJapanese("私");

        assertThatThrownBy(() -> practiceService.checkReverseAnswer(form))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("Lấy câu hỏi ngẫu nhiên Việt -> Nhật không để lộ từ tiếng Nhật")
    void testGetReverseQuestion_doesNotRevealJapaneseText() {
        when(vocabularyRepository.findIdsByTopicId(1L)).thenReturn(List.of(10L));
        when(vocabularyRepository.findById(10L)).thenReturn(Optional.of(vocabWithKanji));

        Optional<PracticeViJaQuestionDto> q = practiceService.getReverseQuestion(1L, null);

        assertThat(q).isPresent();
        assertThat(q.get().getMeaningVi()).isEqualTo("Tôi");
        assertThat(q.get().getVocabId()).isEqualTo(10L);
    }
}
