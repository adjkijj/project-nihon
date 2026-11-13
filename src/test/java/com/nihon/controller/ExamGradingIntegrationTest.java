package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.dto.exam.result.ExamResultViewDto;
import com.nihon.dto.exam.taker.ExamSessionAttempt;
import com.nihon.entity.ExamAnswerKind;
import com.nihon.entity.ExamAnswerKey;
import com.nihon.entity.ExamAttempt;
import com.nihon.entity.ExamCategory;
import com.nihon.entity.ExamChoice;
import com.nihon.entity.ExamPaper;
import com.nihon.entity.ExamQuestion;
import com.nihon.entity.ExamQuestionType;
import com.nihon.entity.ExamSection;
import com.nihon.history.LearnerContext;
import com.nihon.history.LearnerIdentityFilter;
import com.nihon.repository.ExamAttemptRepository;
import com.nihon.repository.ExamPaperRepository;
import com.nihon.service.exam.ExamService;
import com.nihon.service.exam.ExamTakingService;
import com.nihon.service.exam.grading.ExamGradingService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExamGradingIntegrationTest {

    private static final String CSRF_TOKEN = "csrf-token-grading-test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExamPaperRepository paperRepository;

    @Autowired
    private ExamAttemptRepository attemptRepository;

    @Autowired
    private ExamTakingService takingService;

    @Autowired
    private ExamGradingService gradingService;

    @Autowired
    private ExamService examService;

    private ExamPaper gradingPaper;
    private Long qMcqId;
    private Long qTypingId;
    private Long qTranslationId;
    private Long qEssayId;

    @BeforeEach
    void setUp() {
        gradingPaper = ExamPaper.builder()
                .code("GRADE-TEST-" + System.currentTimeMillis())
                .title("Đề thi kiểm thử chấm điểm toàn diện")
                .durationMinutes(30)
                .category(ExamCategory.MOCK)
                .published(true)
                .sections(new ArrayList<>())
                .build();

        // Phần 1: Trắc nghiệm và Gõ đáp án
        ExamSection sec1 = ExamSection.builder()
                .title("Phần 1: Kiến thức ngôn ngữ")
                .sortOrder(1)
                .questions(new ArrayList<>())
                .build();

        ExamQuestion qMcq = ExamQuestion.builder()
                .sortOrder(1)
                .type(ExamQuestionType.MULTIPLE_CHOICE)
                .promptJa("「日本」の読み方は？")
                .points(BigDecimal.valueOf(2.0))
                .choices(new ArrayList<>())
                .build();
        qMcq.addChoice(ExamChoice.builder().sortOrder(1).text("にほん").correct(true).build());
        qMcq.addChoice(ExamChoice.builder().sortOrder(2).text("にっぽ").correct(false).build());

        ExamQuestion qTyping = ExamQuestion.builder()
                .sortOrder(2)
                .type(ExamQuestionType.TYPING)
                .promptJa("Gõ từ 'học sinh':")
                .points(BigDecimal.valueOf(2.0))
                .answerKeys(new ArrayList<>())
                .build();
        qTyping.addAnswerKey(ExamAnswerKey.builder().kind(ExamAnswerKind.ACCEPTED).answerText("がくせい").build());

        sec1.addQuestion(qMcq);
        sec1.addQuestion(qTyping);

        // Phần 2: Dịch câu và Tự luận
        ExamSection sec2 = ExamSection.builder()
                .title("Phần 2: Viết và Dịch")
                .sortOrder(2)
                .questions(new ArrayList<>())
                .build();

        ExamQuestion qTrans = ExamQuestion.builder()
                .sortOrder(1)
                .type(ExamQuestionType.TRANSLATION)
                .promptJa("わたしはにほんごをべんきょうします。")
                .points(BigDecimal.valueOf(3.0))
                .answerKeys(new ArrayList<>())
                .build();
        qTrans.addAnswerKey(ExamAnswerKey.builder().kind(ExamAnswerKind.SAMPLE).answerText("Tôi học tiếng Nhật.").build());

        ExamQuestion qEssay = ExamQuestion.builder()
                .sortOrder(2)
                .type(ExamQuestionType.ESSAY)
                .promptJa("Hãy viết một đoạn văn ngắn về sở thích của bạn:")
                .points(BigDecimal.valueOf(5.0))
                .answerKeys(new ArrayList<>())
                .build();
        qEssay.addAnswerKey(ExamAnswerKey.builder().kind(ExamAnswerKind.SAMPLE).answerText("私の趣味は読書です。").build());
        qEssay.addAnswerKey(ExamAnswerKey.builder().kind(ExamAnswerKind.KEYWORD).answerText("趣味").build());
        qEssay.addAnswerKey(ExamAnswerKey.builder().kind(ExamAnswerKind.KEYWORD).answerText("読書").build());

        sec2.addQuestion(qTrans);
        sec2.addQuestion(qEssay);

        gradingPaper.addSection(sec1);
        gradingPaper.addSection(sec2);

        paperRepository.save(gradingPaper);

        qMcqId = qMcq.getId();
        qTypingId = qTyping.getId();
        qTranslationId = qTrans.getId();
        qEssayId = qEssay.getId();
    }

    @Test
    @DisplayName("Nộp bài thi: Lưu bền vững vào exam_attempt và exam_attempt_answer trong CSDL")
    void testGradeAndPersistWorkflow() throws Exception {
        String learnerId = UUID.randomUUID().toString();
        Cookie learnerCookie = new Cookie(LearnerIdentityFilter.COOKIE_NAME, learnerId);
        MockHttpSession session = new MockHttpSession();

        // 1. Bắt đầu làm bài
        mockMvc.perform(post("/exams/" + gradingPaper.getCode() + "/start")
                        .cookie(learnerCookie)
                        .session(session)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection());

        java.util.Map<?, ?> map = (java.util.Map<?, ?>) session.getAttribute(ExamSessionAttempt.SESSION_ATTEMPTS_MAP);
        ExamSessionAttempt sessionAttempt = (ExamSessionAttempt) map.values().iterator().next();

        // Lưu câu trả lời đúng cho MCQ
        Long correctChoiceId = gradingPaper.getAllQuestions().get(0).getChoices().stream()
                .filter(ExamChoice::isCorrect).findFirst().get().getId();

        mockMvc.perform(post("/exams/attempt/" + sessionAttempt.getAttemptId() + "/answer")
                        .cookie(learnerCookie)
                        .session(session)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("questionId", String.valueOf(qMcqId))
                        .param("answer", String.valueOf(correctChoiceId)))
                .andExpect(status().isOk());

        // Lưu câu trả lời cho Typing (sai)
        mockMvc.perform(post("/exams/attempt/" + sessionAttempt.getAttemptId() + "/answer")
                        .cookie(learnerCookie)
                        .session(session)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("questionId", String.valueOf(qTypingId))
                        .param("answer", "sai_roi"))
                .andExpect(status().isOk());

        // 2. Thí sinh nộp bài
        mockMvc.perform(post("/exams/attempt/" + sessionAttempt.getAttemptId() + "/finish")
                        .cookie(learnerCookie)
                        .session(session)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/exams/attempt/" + sessionAttempt.getAttemptId() + "/result"));

        // 3. Kiểm tra bản ghi CSDL exam_attempt
        ExamAttempt dbAttempt = attemptRepository.findByIdWithDetails(sessionAttempt.getAttemptId()).orElse(null);
        assertThat(dbAttempt).isNotNull();
        assertThat(dbAttempt.getLearnerId()).isEqualTo(learnerId);
        assertThat(dbAttempt.getAnswers()).hasSize(4);

        // MCQ đúng được 2.0 đ
        var ansMcq = dbAttempt.getAnswers().stream().filter(a -> qMcqId.equals(a.getQuestionId())).findFirst().get();
        assertThat(ansMcq.getCorrect()).isTrue();
        assertThat(ansMcq.getPointsAwarded()).isEqualByComparingTo(BigDecimal.valueOf(2.0));

        // Typing sai được 0 đ
        var ansTyping = dbAttempt.getAnswers().stream().filter(a -> qTypingId.equals(a.getQuestionId())).findFirst().get();
        assertThat(ansTyping.getCorrect()).isFalse();
        assertThat(ansTyping.getPointsAwarded()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Bảo mật kết quả thi: Người học khác cố xem kết quả nhận 404 Not Found")
    void testSecurityOtherLearnerReceives404() throws Exception {
        String learnerOwnerId = UUID.randomUUID().toString();
        Cookie ownerCookie = new Cookie(LearnerIdentityFilter.COOKIE_NAME, learnerOwnerId);

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/exams/" + gradingPaper.getCode() + "/start")
                        .cookie(ownerCookie)
                        .session(session)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection());

        ExamSessionAttempt sessionAttempt = (ExamSessionAttempt) ((java.util.Map<?, ?>) session.getAttribute(ExamSessionAttempt.SESSION_ATTEMPTS_MAP)).values().iterator().next();

        // Nộp bài
        mockMvc.perform(post("/exams/attempt/" + sessionAttempt.getAttemptId() + "/finish")
                        .cookie(ownerCookie)
                        .session(session)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection());

        // 1. Đúng chủ bài thi: xem bình thường 200 OK
        mockMvc.perform(get("/exams/attempt/" + sessionAttempt.getAttemptId() + "/result")
                        .cookie(ownerCookie)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("exams/taker/result"))
                .andExpect(model().attributeExists("result", "attempt", "paper"));

        // 2. Kẻ khác (learnerId khác): nhận 404 Not Found
        String otherLearnerId = UUID.randomUUID().toString();
        Cookie otherCookie = new Cookie(LearnerIdentityFilter.COOKIE_NAME, otherLearnerId);

        mockMvc.perform(get("/exams/attempt/" + sessionAttempt.getAttemptId() + "/result")
                        .cookie(otherCookie))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Tự chấm điểm bài luận (POST /exams/attempt/{id}/grade-essay): cập nhật điểm và tổng điểm")
    void testSelfGradeEssay() throws Exception {
        String learnerId = UUID.randomUUID().toString();
        Cookie learnerCookie = new Cookie(LearnerIdentityFilter.COOKIE_NAME, learnerId);
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/exams/" + gradingPaper.getCode() + "/start")
                        .cookie(learnerCookie)
                        .session(session)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection());

        ExamSessionAttempt sessionAttempt = (ExamSessionAttempt) ((java.util.Map<?, ?>) session.getAttribute(ExamSessionAttempt.SESSION_ATTEMPTS_MAP)).values().iterator().next();
        mockMvc.perform(post("/exams/attempt/" + sessionAttempt.getAttemptId() + "/finish")
                        .cookie(learnerCookie)
                        .session(session)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection());

        // Học viên tự cho câu tự luận 4.5 điểm (tối đa là 5.0)
        mockMvc.perform(post("/exams/attempt/" + sessionAttempt.getAttemptId() + "/grade-essay")
                        .cookie(learnerCookie)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("questionId", String.valueOf(qEssayId))
                        .param("points", "4.50"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/exams/attempt/" + sessionAttempt.getAttemptId() + "/result"))
                .andExpect(flash().attributeExists("successMessage"));

        ExamAttempt updated = attemptRepository.findByIdWithDetails(sessionAttempt.getAttemptId()).get();
        var essayAns = updated.getAnswers().stream().filter(a -> qEssayId.equals(a.getQuestionId())).findFirst().get();
        assertThat(essayAns.getPointsAwarded()).isEqualByComparingTo(BigDecimal.valueOf(4.50));
        assertThat(updated.getTotalPoints()).isEqualByComparingTo(BigDecimal.valueOf(4.50));

        // Tự chấm điểm vượt quá maxPoints (ví dụ 10.0 > 5.0) bị báo lỗi
        mockMvc.perform(post("/exams/attempt/" + sessionAttempt.getAttemptId() + "/grade-essay")
                        .cookie(learnerCookie)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("questionId", String.valueOf(qEssayId))
                        .param("points", "10.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @DisplayName("Luyện lại các câu sai (POST /exams/attempt/{id}/retry-incorrect): tạo bài mini gồm các câu sai")
    void testRetryIncorrectQuestions() throws Exception {
        String learnerId = UUID.randomUUID().toString();
        Cookie learnerCookie = new Cookie(LearnerIdentityFilter.COOKIE_NAME, learnerId);
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/exams/" + gradingPaper.getCode() + "/start")
                        .cookie(learnerCookie)
                        .session(session)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection());

        ExamSessionAttempt sessionAttempt = (ExamSessionAttempt) ((java.util.Map<?, ?>) session.getAttribute(ExamSessionAttempt.SESSION_ATTEMPTS_MAP)).values().iterator().next();

        // Không trả lời gì cả -> cả 3 câu tự động đều sai
        mockMvc.perform(post("/exams/attempt/" + sessionAttempt.getAttemptId() + "/finish")
                        .cookie(learnerCookie)
                        .session(session)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection());

        // Bấm Luyện lại các câu sai
        mockMvc.perform(post("/exams/attempt/" + sessionAttempt.getAttemptId() + "/retry-incorrect")
                        .cookie(learnerCookie)
                        .session(session)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/exams/attempt/")))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    @DisplayName("Chặn xóa đề thi khi đã có bài làm trong CSDL (Prompt 19 mục 3)")
    void testPreventDeleteExamWithExistingAttempts() {
        // Tạo một bài thi nộp thành công cho đề
        ExamAttempt attempt = ExamAttempt.builder()
                .id(UUID.randomUUID().toString())
                .paper(gradingPaper)
                .learnerId(UUID.randomUUID().toString())
                .startedAt(Instant.now().minus(10, ChronoUnit.MINUTES))
                .submittedAt(Instant.now())
                .status(com.nihon.dto.exam.taker.ExamAttemptStatus.FINISHED)
                .build();
        attemptRepository.save(attempt);

        // Cố xóa đề thi qua ExamService
        assertThatThrownBy(() -> examService.deleteExam(gradingPaper.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Đề thi đã có bài làm của học viên, không thể xóa");
    }

    @Test
    @DisplayName("Dọn dẹp định kỳ các bài thi quá hạn lưu trữ (retention cleanup)")
    void testRetentionCleanup() {
        // Tạo bài thi cũ hơn 190 ngày
        ExamAttempt oldAttempt = ExamAttempt.builder()
                .id(UUID.randomUUID().toString())
                .paper(gradingPaper)
                .learnerId(UUID.randomUUID().toString())
                .startedAt(Instant.now().minus(195, ChronoUnit.DAYS))
                .submittedAt(Instant.now().minus(195, ChronoUnit.DAYS))
                .status(com.nihon.dto.exam.taker.ExamAttemptStatus.FINISHED)
                .build();
        attemptRepository.save(oldAttempt);

        Instant cutoff = Instant.now().minus(180, ChronoUnit.DAYS);
        int deleted = gradingService.cleanupExpiredAttempts(cutoff);
        assertThat(deleted).isGreaterThanOrEqualTo(1);

        assertThat(attemptRepository.findById(oldAttempt.getId())).isEmpty();
    }
}

