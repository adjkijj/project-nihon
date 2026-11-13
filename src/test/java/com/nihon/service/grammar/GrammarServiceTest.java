package com.nihon.service.grammar;

import com.nihon.dto.grammar.GrammarExampleForm;
import com.nihon.dto.grammar.GrammarQuizChoiceForm;
import com.nihon.dto.grammar.GrammarQuizQuestionForm;
import com.nihon.dto.grammar.GrammarTopicForm;
import com.nihon.entity.GrammarCategory;
import com.nihon.entity.GrammarExample;
import com.nihon.entity.GrammarQuizQuestion;
import com.nihon.entity.GrammarTopic;
import com.nihon.repository.GrammarExampleRepository;
import com.nihon.repository.GrammarQuizChoiceRepository;
import com.nihon.repository.GrammarQuizQuestionRepository;
import com.nihon.repository.GrammarTopicRepository;
import com.nihon.repository.TopicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class GrammarServiceTest {

    @Autowired
    private GrammarService grammarService;

    @Autowired
    private GrammarTopicRepository topicRepository;

    @Autowired
    private GrammarExampleRepository exampleRepository;

    @Autowired
    private GrammarQuizQuestionRepository questionRepository;

    @Autowired
    private GrammarQuizChoiceRepository choiceRepository;

    @Autowired
    private TopicRepository generalTopicRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private GrammarTopic testTopic;

    @BeforeEach
    void setUp() {
        GrammarTopicForm form = GrammarTopicForm.builder()
                .code("TEST-GR-01")
                .title("Cấu trúc Test 〜から")
                .category("GRAMMAR")
                .formula("V-te + から")
                .explanation("Giải thích test")
                .notes("Ghi chú test")
                .sortOrder(1)
                .build();
        testTopic = grammarService.createTopic(form);
    }

    @Test
    @DisplayName("CRUD Chủ đề ngữ pháp: Tạo, Sửa, Lấy thông tin thành công")
    void testTopicCrud() {
        GrammarTopic found = grammarService.getTopicById(testTopic.getId());
        assertThat(found.getCode()).isEqualTo("TEST-GR-01");
        assertThat(found.getTitle()).isEqualTo("Cấu trúc Test 〜から");
        assertThat(found.getCategory()).isEqualTo(GrammarCategory.GRAMMAR);

        // Chỉnh sửa
        GrammarTopicForm updateForm = GrammarTopicForm.builder()
                .code("TEST-GR-01-MOD")
                .title("Cấu trúc Test Cập nhật")
                .category("SUPPLEMENT")
                .formula("N + から")
                .explanation("Giải thích cập nhật")
                .notes("Ghi chú cập nhật")
                .sortOrder(5)
                .build();
        GrammarTopic updated = grammarService.updateTopic(testTopic.getId(), updateForm);
        assertThat(updated.getCode()).isEqualTo("TEST-GR-01-MOD");
        assertThat(updated.getTitle()).isEqualTo("Cấu trúc Test Cập nhật");
        assertThat(updated.getCategory()).isEqualTo(GrammarCategory.SUPPLEMENT);
        assertThat(updated.getSortOrder()).isEqualTo(5);
    }

    @Test
    @DisplayName("Chặn trùng Code: Tạo hoặc cập nhật trùng mã bị ném IllegalArgumentException")
    void testDuplicateCodeRejected() {
        GrammarTopicForm duplicateForm = GrammarTopicForm.builder()
                .code("TEST-GR-01") // Trùng với testTopic
                .title("Chủ đề trùng")
                .category("GRAMMAR")
                .build();

        assertThatThrownBy(() -> grammarService.createTopic(duplicateForm))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("đã tồn tại");

        // Tạo topic thứ hai với code khác
        GrammarTopicForm form2 = GrammarTopicForm.builder()
                .code("TEST-GR-02")
                .title("Chủ đề 2")
                .category("GRAMMAR")
                .build();
        GrammarTopic topic2 = grammarService.createTopic(form2);

        // Cập nhật topic2 thành code của testTopic bị chặn
        GrammarTopicForm updateForm = GrammarTopicForm.builder()
                .code("TEST-GR-01")
                .title("Chủ đề 2 sửa")
                .category("GRAMMAR")
                .build();
        assertThatThrownBy(() -> grammarService.updateTopic(topic2.getId(), updateForm))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("đã tồn tại");
    }

    @Test
    @DisplayName("Xóa chủ đề ngữ pháp: Cascade xóa toàn bộ câu ví dụ và câu hỏi trắc nghiệm")
    void testDeleteTopicCascade() {
        // Thêm 1 ví dụ
        GrammarExampleForm exForm = GrammarExampleForm.builder()
                .japanese("雨が降っているから")
                .meaningVi("Vì trời đang mưa")
                .build();
        GrammarExample ex = grammarService.addExample(testTopic.getId(), exForm);

        // Thêm 1 câu hỏi
        GrammarQuizQuestionForm qForm = createSampleQuestionForm(2, 0);
        GrammarQuizQuestion q = grammarService.addQuestion(testTopic.getId(), qForm);

        Long topicId = testTopic.getId();
        Long exId = ex.getId();
        Long qId = q.getId();

        grammarService.deleteTopic(topicId);
        entityManager.flush();
        entityManager.clear();

        assertThat(topicRepository.findById(topicId)).isEmpty();
        assertThat(exampleRepository.findById(exId)).isEmpty();
        assertThat(questionRepository.findById(qId)).isEmpty();
    }

    @Test
    @DisplayName("Giới hạn tối đa 20 ví dụ: Thêm câu ví dụ thứ 21 bị ném IllegalStateException")
    void testMax20ExamplesLimit() {
        for (int i = 1; i <= 20; i++) {
            GrammarExampleForm form = GrammarExampleForm.builder()
                    .japanese("例文 " + i + " から")
                    .meaningVi("Ví dụ " + i)
                    .sortOrder(i)
                    .build();
            grammarService.addExample(testTopic.getId(), form);
        }

        assertThat(exampleRepository.countByGrammarTopicId(testTopic.getId())).isEqualTo(20);

        GrammarExampleForm overflowForm = GrammarExampleForm.builder()
                .japanese("例文 21 から")
                .meaningVi("Ví dụ 21")
                .build();

        assertThatThrownBy(() -> grammarService.addExample(testTopic.getId(), overflowForm))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tối đa (20 câu)");
    }

    @Test
    @DisplayName("Giới hạn tối đa 20 câu hỏi: Thêm câu hỏi thứ 21 bị ném IllegalStateException")
    void testMax20QuestionsLimit() {
        for (int i = 1; i <= 20; i++) {
            GrammarQuizQuestionForm form = createSampleQuestionForm(2, 0);
            form.setPrompt("Đề bài câu " + i);
            form.setSortOrder(i);
            grammarService.addQuestion(testTopic.getId(), form);
        }

        assertThat(questionRepository.countByGrammarTopicId(testTopic.getId())).isEqualTo(20);

        GrammarQuizQuestionForm overflowForm = createSampleQuestionForm(2, 0);
        overflowForm.setPrompt("Đề bài câu 21");

        assertThatThrownBy(() -> grammarService.addQuestion(testTopic.getId(), overflowForm))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tối đa (20 câu)");
    }

    @Test
    @DisplayName("Quy tắc câu trắc nghiệm: Ít hơn 2 lựa chọn bị ném IllegalArgumentException")
    void testQuizChoiceLessThanTwo() {
        GrammarQuizQuestionForm form = createSampleQuestionForm(1, 0);

        assertThatThrownBy(() -> grammarService.addQuestion(testTopic.getId(), form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("từ 2 đến 6 lựa chọn");
    }

    @Test
    @DisplayName("Quy tắc câu trắc nghiệm: Nhiều hơn 6 lựa chọn bị ném IllegalArgumentException")
    void testQuizChoiceMoreThanSix() {
        GrammarQuizQuestionForm form = createSampleQuestionForm(7, 0);

        assertThatThrownBy(() -> grammarService.addQuestion(testTopic.getId(), form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("từ 2 đến 6 lựa chọn");
    }

    @Test
    @DisplayName("Quy tắc câu trắc nghiệm: Không có đáp án đúng bị ném IllegalArgumentException")
    void testQuizChoiceNoCorrectAnswer() {
        GrammarQuizQuestionForm form = createSampleQuestionForm(3, -1); // không có đáp án đúng

        assertThatThrownBy(() -> grammarService.addQuestion(testTopic.getId(), form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("đúng 1 đáp án đúng");
    }

    @Test
    @DisplayName("Quy tắc câu trắc nghiệm: Nhiều hơn 1 đáp án đúng bị ném IllegalArgumentException")
    void testQuizChoiceMultipleCorrectAnswers() {
        GrammarQuizQuestionForm form = createSampleQuestionForm(4, 0);
        form.getChoices().get(1).setCorrect(true); // 2 đáp án đúng

        assertThatThrownBy(() -> grammarService.addQuestion(testTopic.getId(), form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("đúng 1 đáp án đúng");
    }

    @Test
    @DisplayName("Sắp xếp ví dụ: moveExample lên và xuống thay đổi sortOrder chính xác")
    void testMoveExample() {
        GrammarExample ex1 = grammarService.addExample(testTopic.getId(), GrammarExampleForm.builder()
                .japanese("例文 1").meaningVi("Nghĩa 1").sortOrder(1).build());
        GrammarExample ex2 = grammarService.addExample(testTopic.getId(), GrammarExampleForm.builder()
                .japanese("例文 2").meaningVi("Nghĩa 2").sortOrder(2).build());

        // ex2 move up -> sortOrder của ex2 thành 1, ex1 thành 2
        grammarService.moveExample(ex2.getId(), true);

        GrammarExample reloaded1 = grammarService.getExampleById(ex1.getId());
        GrammarExample reloaded2 = grammarService.getExampleById(ex2.getId());

        assertThat(reloaded2.getSortOrder()).isLessThan(reloaded1.getSortOrder());

        // move down lại
        grammarService.moveExample(ex2.getId(), false);
        reloaded1 = grammarService.getExampleById(ex1.getId());
        reloaded2 = grammarService.getExampleById(ex2.getId());

        assertThat(reloaded1.getSortOrder()).isLessThan(reloaded2.getSortOrder());
    }

    private GrammarQuizQuestionForm createSampleQuestionForm(int choiceCount, int correctIndex) {
        List<GrammarQuizChoiceForm> choices = new ArrayList<>();
        for (int i = 0; i < choiceCount; i++) {
            choices.add(GrammarQuizChoiceForm.builder()
                    .sortOrder(i + 1)
                    .text("Đáp án " + (i + 1))
                    .correct(i == correctIndex)
                    .build());
        }
        return GrammarQuizQuestionForm.builder()
                .prompt("Đề bài trắc nghiệm mẫu")
                .explanation("Giải thích mẫu")
                .choices(choices)
                .build();
    }
}

