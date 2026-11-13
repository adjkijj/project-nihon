package com.nihon.service.exam;

import com.nihon.dto.exam.ExamPaperForm;
import com.nihon.dto.exam.ExamQuestionForm;
import com.nihon.dto.exam.ExamSectionForm;
import com.nihon.entity.ExamAnswerKind;
import com.nihon.entity.ExamCategory;
import com.nihon.entity.ExamPaper;
import com.nihon.entity.ExamQuestion;
import com.nihon.entity.ExamQuestionType;
import com.nihon.entity.ExamSection;
import com.nihon.repository.ExamAnswerKeyRepository;
import com.nihon.repository.ExamChoiceRepository;
import com.nihon.repository.ExamPaperRepository;
import com.nihon.repository.ExamQuestionRepository;
import com.nihon.repository.ExamSectionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ExamServiceTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ExamService examService;

    @Autowired
    private ExamPaperRepository paperRepository;

    @Autowired
    private ExamSectionRepository sectionRepository;

    @Autowired
    private ExamQuestionRepository questionRepository;

    @Autowired
    private ExamChoiceRepository choiceRepository;

    @Autowired
    private ExamAnswerKeyRepository answerKeyRepository;

    @Test
    @DisplayName("Tạo đề thi mới thành công và kiểm tra giá trị mặc định")
    void testCreateExamSuccess() {
        ExamPaperForm form = ExamPaperForm.builder()
                .code("TEST-EXAM-01")
                .title("Đề thi thử nghiệm N5")
                .category(ExamCategory.MIDTERM.name())
                .durationMinutes(45)
                .published(false)
                .build();

        ExamPaper paper = examService.createExam(form);

        assertThat(paper).isNotNull();
        assertThat(paper.getId()).isNotNull();
        assertThat(paper.getCode()).isEqualTo("TEST-EXAM-01");
        assertThat(paper.getTitle()).isEqualTo("Đề thi thử nghiệm N5");
        assertThat(paper.getCategory()).isEqualTo(ExamCategory.MIDTERM);
        assertThat(paper.getDurationMinutes()).isEqualTo(45);
        assertThat(paper.isPublished()).isFalse();
    }

    @Test
    @DisplayName("Tạo đề thi với mã code bị trùng sẽ ném IllegalArgumentException")
    void testCreateExamDuplicateCodeThrows() {
        ExamPaperForm form1 = ExamPaperForm.builder()
                .code("DUPLICATE-CODE")
                .title("Đề thi 1")
                .category(ExamCategory.MOCK.name())
                .durationMinutes(60)
                .build();
        examService.createExam(form1);

        ExamPaperForm form2 = ExamPaperForm.builder()
                .code("DUPLICATE-CODE")
                .title("Đề thi 2")
                .category(ExamCategory.MOCK.name())
                .durationMinutes(60)
                .build();

        assertThatThrownBy(() -> examService.createExam(form2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("đã tồn tại");
    }

    @Test
    @DisplayName("Cập nhật thông tin đề thi và bật/tắt xuất bản")
    void testUpdateAndTogglePublish() {
        ExamPaperForm form = ExamPaperForm.builder()
                .code("TEST-EXAM-UPDATE")
                .title("Tiêu đề ban đầu")
                .category(ExamCategory.REVIEW.name())
                .durationMinutes(30)
                .published(false)
                .build();
        ExamPaper paper = examService.createExam(form);

        ExamPaperForm updateForm = ExamPaperForm.builder()
                .code("TEST-EXAM-UPDATE-MOD")
                .title("Tiêu đề đã sửa")
                .category(ExamCategory.QUICK_TEST.name())
                .durationMinutes(15)
                .published(true)
                .build();
        ExamPaper updated = examService.updateExam(paper.getId(), updateForm);

        assertThat(updated.getTitle()).isEqualTo("Tiêu đề đã sửa");
        assertThat(updated.getCode()).isEqualTo("TEST-EXAM-UPDATE-MOD");
        assertThat(updated.getCategory()).isEqualTo(ExamCategory.QUICK_TEST);
        assertThat(updated.getDurationMinutes()).isEqualTo(15);
        assertThat(updated.isPublished()).isTrue();

        // Toggle publish
        ExamPaper toggled = examService.togglePublish(paper.getId());
        assertThat(toggled.isPublished()).isFalse();
    }

    @Test
    @DisplayName("Thêm phần thi và đổi thứ tự phần thi (reorder)")
    void testAddAndReorderSections() {
        ExamPaper paper = examService.createExam(ExamPaperForm.builder()
                .code("TEST-SECTIONS")
                .title("Đề thi kiểm tra phần")
                .category(ExamCategory.MIDTERM.name())
                .durationMinutes(60)
                .build());

        ExamSection s1 = examService.addSection(paper.getId(), ExamSectionForm.builder()
                .paperId(paper.getId())
                .title("Phần 1 - Từ vựng")
                .build());

        ExamSection s2 = examService.addSection(paper.getId(), ExamSectionForm.builder()
                .paperId(paper.getId())
                .title("Phần 2 - Ngữ pháp")
                .build());

        assertThat(s1.getSortOrder()).isEqualTo(1);
        assertThat(s2.getSortOrder()).isEqualTo(2);

        // Move s2 up
        examService.moveSection(s2.getId(), true);

        List<ExamSection> sections = sectionRepository.findByPaperIdOrderBySortOrderAsc(paper.getId());
        assertThat(sections.get(0).getTitle()).isEqualTo("Phần 2 - Ngữ pháp");
        assertThat(sections.get(1).getTitle()).isEqualTo("Phần 1 - Từ vựng");
    }

    @Test
    @DisplayName("Thêm tối đa 10 phần thi; phần thi thứ 11 sẽ ném lỗi")
    void testSectionLimitMax10() {
        ExamPaper paper = examService.createExam(ExamPaperForm.builder()
                .code("TEST-MAX-SECTIONS")
                .title("Đề kiểm tra giới hạn phần")
                .category(ExamCategory.MIDTERM.name())
                .durationMinutes(60)
                .build());

        for (int i = 1; i <= 10; i++) {
            examService.addSection(paper.getId(), ExamSectionForm.builder()
                    .paperId(paper.getId())
                    .title("Phần " + i)
                    .build());
        }

        assertThatThrownBy(() -> examService.addSection(paper.getId(), ExamSectionForm.builder()
                .paperId(paper.getId())
                .title("Phần 11")
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tối đa 10 phần thi");
    }

    @Test
    @DisplayName("Thêm và cập nhật câu hỏi trắc nghiệm (MULTIPLE_CHOICE)")
    void testAddAndCheckMultipleChoiceQuestion() {
        ExamPaper paper = examService.createExam(ExamPaperForm.builder()
                .code("TEST-Q-MC")
                .title("Đề trắc nghiệm")
                .category(ExamCategory.MIDTERM.name())
                .durationMinutes(60)
                .build());

        ExamSection section = examService.addSection(paper.getId(), ExamSectionForm.builder()
                .paperId(paper.getId())
                .title("Phần trắc nghiệm")
                .build());

        ExamQuestionForm qForm = ExamQuestionForm.builder()
                .sectionId(section.getId())
                .type(ExamQuestionType.MULTIPLE_CHOICE.name())
                .points(BigDecimal.valueOf(1.50))
                .promptJa("きのう 何を (____) か。")
                .promptVi("Hôm qua bạn đã ăn gì?")
                .choice1("たべます")
                .choice2("たべました")
                .choice3("たべません")
                .choice4("たべませんでした")
                .correctChoiceIndex(2)
                .explanation("Quá khứ dùng たべました")
                .build();

        ExamQuestion q = examService.addQuestion(section.getId(), qForm);
        entityManager.flush();
        entityManager.clear();
        ExamQuestion reloaded = examService.getQuestionById(q.getId());
        assertThat(reloaded.getChoices()).hasSize(4);
        assertThat(reloaded.getChoices().get(1).isCorrect()).isTrue();
        assertThat(reloaded.getChoices().get(0).isCorrect()).isFalse();
    }

    @Test
    @DisplayName("Thêm câu hỏi ghép câu ngôi sao JLPT (ORDERING)")
    void testAddOrderingQuestion() {
        ExamPaper paper = examService.createExam(ExamPaperForm.builder()
                .code("TEST-Q-ORD")
                .title("Đề câu sao JLPT")
                .category(ExamCategory.MOCK.name())
                .durationMinutes(60)
                .build());

        ExamSection section = examService.addSection(paper.getId(), ExamSectionForm.builder()
                .paperId(paper.getId())
                .title("Phần sắp xếp câu")
                .build());

        ExamQuestionForm qForm = ExamQuestionForm.builder()
                .sectionId(section.getId())
                .type(ExamQuestionType.ORDERING.name())
                .points(BigDecimal.valueOf(2.00))
                .promptJa("わたしは ____ ____ __★__ ____ です。")
                .choice1("ベトナム")
                .choice2("から")
                .choice3("きました")
                .choice4("がくせい")
                .orderingOrder("1,2,3,4")
                .orderingStar(3)
                .build();

        ExamQuestion q = examService.addQuestion(section.getId(), qForm);
        entityManager.flush();
        entityManager.clear();
        ExamQuestion reloaded = examService.getQuestionById(q.getId());
        assertThat(reloaded.getAnswerKeys()).hasSize(2);
        assertThat(reloaded.getAnswerKeys()).anyMatch(k -> k.getKind() == ExamAnswerKind.ORDER && k.getAnswerText().equals("ORDER:1,2,3,4"));
        assertThat(reloaded.getAnswerKeys()).anyMatch(k -> k.getKind() == ExamAnswerKind.ORDER && k.getAnswerText().equals("STAR:3"));
    }

    @Test
    @DisplayName("Xóa đề thi cascade: xóa sạch các phần thi, câu hỏi, lựa chọn và đáp án")
    void testDeleteExamCascade() {
        ExamPaper paper = examService.createExam(ExamPaperForm.builder()
                .code("TEST-CASCADE-DEL")
                .title("Đề cần xóa")
                .category(ExamCategory.REVIEW.name())
                .durationMinutes(60)
                .build());

        ExamSection section = examService.addSection(paper.getId(), ExamSectionForm.builder()
                .paperId(paper.getId())
                .title("Phần 1")
                .build());

        ExamQuestion q = examService.addQuestion(section.getId(), ExamQuestionForm.builder()
                .sectionId(section.getId())
                .type(ExamQuestionType.MULTIPLE_CHOICE.name())
                .points(BigDecimal.ONE)
                .promptJa("これ")
                .choice1("A")
                .choice2("B")
                .correctChoiceIndex(1)
                .build());

        Long paperId = paper.getId();
        Long sectionId = section.getId();
        Long questionId = q.getId();

        entityManager.flush();
        entityManager.clear();

        examService.deleteExam(paperId);
        entityManager.flush();
        entityManager.clear();

        assertThat(paperRepository.findById(paperId)).isEmpty();
        assertThat(sectionRepository.findById(sectionId)).isEmpty();
        assertThat(questionRepository.findById(questionId)).isEmpty();
        assertThat(choiceRepository.findByQuestionIdOrderBySortOrderAsc(questionId)).isEmpty();
        assertThat(answerKeyRepository.findByQuestionId(questionId)).isEmpty();
    }
}

