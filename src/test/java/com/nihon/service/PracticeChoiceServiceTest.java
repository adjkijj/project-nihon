package com.nihon.service;

import com.nihon.dto.PracticeChoiceAnswerForm;
import com.nihon.dto.PracticeChoiceOptionDto;
import com.nihon.dto.PracticeChoiceQuestionDto;
import com.nihon.dto.PracticeChoiceResultDto;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeChoiceServiceTest {

    @Mock
    private VocabularyRepository vocabularyRepository;

    @InjectMocks
    private PracticeService practiceService;

    private Topic topic1;
    private Topic topic2;

    private Vocabulary v1;
    private Vocabulary v2;
    private Vocabulary v3;
    private Vocabulary v4;
    private Vocabulary v5;

    @BeforeEach
    void setUp() {
        topic1 = new Topic();
        topic1.setId(1L);
        topic1.setName("Chủ đề 1");

        topic2 = new Topic();
        topic2.setId(2L);
        topic2.setName("Chủ đề 2");

        v1 = new Vocabulary();
        v1.setId(1L);
        v1.setJapaneseText("本");
        v1.setReading("ほん");
        v1.setMeaningVi("sách");
        v1.setTopic(topic1);

        v2 = new Vocabulary();
        v2.setId(2L);
        v2.setJapaneseText("ペン");
        v2.setReading("ぺん");
        v2.setMeaningVi("bút mực");
        v2.setTopic(topic1);

        v3 = new Vocabulary();
        v3.setId(3L);
        v3.setJapaneseText("ノート");
        v3.setReading("のーと");
        v3.setMeaningVi("vở ghi");
        v3.setTopic(topic1);

        v4 = new Vocabulary();
        v4.setId(4L);
        v4.setJapaneseText("机");
        v4.setReading("つくえ");
        v4.setMeaningVi("bàn học");
        v4.setTopic(topic1);

        v5 = new Vocabulary();
        v5.setId(5L);
        v5.setJapaneseText("椅子");
        v5.setReading("いす");
        v5.setMeaningVi("ghế");
        v5.setTopic(topic2);
    }

    @Test
    @DisplayName("Câu hỏi sinh ra có đúng 4 lựa chọn khác nhau, có một lựa chọn đúng")
    void testGetChoiceQuestion_generatesFourDistinctOptionsWithOneCorrect() {
        when(vocabularyRepository.findByTopicIdOrderBySourceLineAsc(1L)).thenReturn(List.of(v1, v2, v3, v4));

        Optional<PracticeChoiceQuestionDto> questionOpt = practiceService.getChoiceQuestion(1L, "JA_VI", null);

        assertThat(questionOpt).isPresent();
        PracticeChoiceQuestionDto question = questionOpt.get();

        assertThat(question.getOptions()).hasSize(4);

        // Đảm bảo cả 4 lựa chọn đều có ID và nội dung hiển thị phân biệt
        List<Long> optionIds = question.getOptions().stream().map(PracticeChoiceOptionDto::getVocabId).toList();
        assertThat(optionIds).doesNotHaveDuplicates();

        List<String> optionTexts = question.getOptions().stream().map(PracticeChoiceOptionDto::getDisplayText).toList();
        assertThat(optionTexts).doesNotHaveDuplicates();

        // Đảm bảo chứa đúng 1 lựa chọn tương ứng với câu hỏi
        assertThat(optionIds).contains(question.getVocabId());
    }

    @Test
    @DisplayName("Chủ đề chỉ có 2 từ: tự động bổ sung đáp án nhiễu từ chủ đề khác để đủ 4")
    void testTopicWithOnlyTwoWords_supplementsFromOtherTopics() {
        // Topic 1 chỉ có 2 từ v1, v2
        when(vocabularyRepository.findByTopicIdOrderBySourceLineAsc(1L)).thenReturn(List.of(v1, v2));
        // Toàn bộ hệ thống có thêm v3, v4, v5
        when(vocabularyRepository.findAll()).thenReturn(List.of(v1, v2, v3, v4, v5));

        Optional<PracticeChoiceQuestionDto> questionOpt = practiceService.getChoiceQuestion(1L, "JA_VI", null);

        assertThat(questionOpt).isPresent();
        PracticeChoiceQuestionDto question = questionOpt.get();
        assertThat(question.getOptions()).hasSize(4);

        List<Long> optionIds = question.getOptions().stream().map(PracticeChoiceOptionDto::getVocabId).toList();
        assertThat(optionIds).contains(question.getVocabId());
    }

    @Test
    @DisplayName("Toàn hệ thống chỉ có 3 từ: báo chưa đủ dữ liệu (Optional.empty)")
    void testSystemWithLessThanFourWords_returnsEmpty() {
        when(vocabularyRepository.findByTopicIdOrderBySourceLineAsc(1L)).thenReturn(List.of(v1, v2));
        when(vocabularyRepository.findAll()).thenReturn(List.of(v1, v2, v3)); // Chỉ có 3 từ tổng cộng

        Optional<PracticeChoiceQuestionDto> questionOpt = practiceService.getChoiceQuestion(1L, "JA_VI", null);

        assertThat(questionOpt).isEmpty();
    }

    @Test
    @DisplayName("Hai từ khác nhau cùng nghĩa: không bị xem là đáp án nhiễu của nhau")
    void testDifferentWordsWithSameMeaning_notConsideredDistractors() {
        // vSynonym có cùng nghĩa "sách" với v1 nhưng là từ khác
        Vocabulary vSynonym = new Vocabulary();
        vSynonym.setId(99L);
        vSynonym.setJapaneseText("書籍");
        vSynonym.setReading("しょせき");
        vSynonym.setMeaningVi("sách");
        vSynonym.setTopic(topic1);

        when(vocabularyRepository.findByTopicIdOrderBySourceLineAsc(1L))
                .thenReturn(List.of(v1, vSynonym, v2, v3, v4));

        // Sinh 20 lần để đảm bảo vSynonym không bao giờ là đáp án nhiễu khi v1 là target
        for (int i = 0; i < 20; i++) {
            Optional<PracticeChoiceQuestionDto> questionOpt = practiceService.getChoiceQuestion(1L, "JA_VI", null);
            assertThat(questionOpt).isPresent();
            PracticeChoiceQuestionDto q = questionOpt.get();

            if (q.getVocabId().equals(v1.getId())) {
                List<Long> optionIds = q.getOptions().stream().map(PracticeChoiceOptionDto::getVocabId).toList();
                assertThat(optionIds).doesNotContain(vSynonym.getId());
            } else if (q.getVocabId().equals(vSynonym.getId())) {
                List<Long> optionIds = q.getOptions().stream().map(PracticeChoiceOptionDto::getVocabId).toList();
                assertThat(optionIds).doesNotContain(v1.getId());
            }
        }
    }

    @Test
    @DisplayName("Chấm đáp án trắc nghiệm: đúng, sai và chặn choiceVocabId nằm ngoài bộ lựa chọn")
    void testCheckChoiceAnswer_correctIncorrectAndSecurityValidation() {
        when(vocabularyRepository.findById(1L)).thenReturn(Optional.of(v1));
        when(vocabularyRepository.findById(2L)).thenReturn(Optional.of(v2));

        List<Long> validOptions = List.of(1L, 2L, 3L, 4L);

        // 1. Chấm đúng
        PracticeChoiceAnswerForm correctForm = PracticeChoiceAnswerForm.builder()
                .vocabId(1L)
                .choiceVocabId(1L)
                .direction("JA_VI")
                .build();
        PracticeChoiceResultDto correctResult = practiceService.checkChoiceAnswer(correctForm, validOptions);
        assertThat(correctResult.isCorrect()).isTrue();

        // 2. Chấm sai
        PracticeChoiceAnswerForm incorrectForm = PracticeChoiceAnswerForm.builder()
                .vocabId(1L)
                .choiceVocabId(2L)
                .direction("JA_VI")
                .build();
        PracticeChoiceResultDto incorrectResult = practiceService.checkChoiceAnswer(incorrectForm, validOptions);
        assertThat(incorrectResult.isCorrect()).isFalse();

        // 3. choiceVocabId không nằm trong bộ lựa chọn hợp lệ -> ném ngoại lệ
        PracticeChoiceAnswerForm invalidChoiceForm = PracticeChoiceAnswerForm.builder()
                .vocabId(1L)
                .choiceVocabId(999L)
                .direction("JA_VI")
                .build();
        assertThatThrownBy(() -> practiceService.checkChoiceAnswer(invalidChoiceForm, validOptions))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Lựa chọn không hợp lệ hoặc câu hỏi đã hết hạn.");
    }
}

