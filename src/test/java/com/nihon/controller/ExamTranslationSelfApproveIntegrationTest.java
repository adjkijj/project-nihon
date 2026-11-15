package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.dto.exam.taker.ExamAttemptStatus;
import com.nihon.entity.ExamAnswerKind;
import com.nihon.entity.ExamAnswerKey;
import com.nihon.entity.ExamAttempt;
import com.nihon.entity.ExamAttemptAnswer;
import com.nihon.entity.ExamCategory;
import com.nihon.entity.ExamChoice;
import com.nihon.entity.ExamPaper;
import com.nihon.entity.ExamQuestion;
import com.nihon.entity.ExamQuestionType;
import com.nihon.entity.ExamSection;
import com.nihon.history.LearnerIdentityFilter;
import com.nihon.repository.ExamAttemptAnswerRepository;
import com.nihon.repository.ExamAttemptRepository;
import com.nihon.repository.ExamPaperRepository;
import com.nihon.repository.ExamQuestionRepository;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử tính năng Tự xác nhận điểm cho câu hỏi dịch câu (TRANSLATION).
 * Yêu cầu:
 * - Chỉ áp dụng cho câu hỏi loại TRANSLATION sau khi nộp bài và xem kết quả.
 * - Endpoint POST /exams/attempt/{attemptId}/answer/{answerId}/self-approve.
 * - Có CSRF.
 * - Kiểm tra answer thuộc attempt.
 * - Kiểm tra learner_id trong cookie khớp với attempt.
 * - Điểm cập nhật bằng điểm tối đa của câu.
 * - Kết quả hiển thị dòng "Tự xác nhận" cạnh điểm.
 * - Tổng điểm và xếp loại tính lại.
 * - Có nút hủy xác nhận.
 * - Test: đúng attempt, sai attempt, sai learner, câu hỏi không phải TRANSLATION.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExamTranslationSelfApproveIntegrationTest {

    private static final String CSRF_TOKEN = "csrf-token-self-approve-test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExamPaperRepository paperRepository;

    @Autowired
    private ExamQuestionRepository questionRepository;

    @Autowired
    private ExamAttemptRepository attemptRepository;

    @Autowired
    private ExamAttemptAnswerRepository attemptAnswerRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private String learnerId;
    private Cookie learnerCookie;
    private MockHttpSession session;

    private ExamPaper testPaper;
    private ExamQuestion qTranslation;
    private ExamQuestion qMcq;

    private ExamAttempt attempt;
    private ExamAttemptAnswer ansTranslation;
    private ExamAttemptAnswer ansMcq;

    private ExamAttempt otherAttempt;
    private ExamAttemptAnswer otherAnsTranslation;

    @BeforeEach
    void setUp() {
        learnerId = UUID.randomUUID().toString();
        learnerCookie = new Cookie(LearnerIdentityFilter.COOKIE_NAME, learnerId);
        session = new MockHttpSession();
        session.setAttribute(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN);

        testPaper = ExamPaper.builder()
                .code("TRANS-TEST-" + System.currentTimeMillis())
                .title("Đề kiểm tra dịch thuật")
                .durationMinutes(20)
                .category(ExamCategory.MOCK)
                .published(true)
                .sections(new ArrayList<>())
                .build();

        ExamSection sec = ExamSection.builder()
                .title("Phần Dịch và Trắc nghiệm")
                .sortOrder(1)
                .questions(new ArrayList<>())
                .build();

        qTranslation = ExamQuestion.builder()
                .sortOrder(1)
                .type(ExamQuestionType.TRANSLATION)
                .promptJa("これはペンです。")
                .promptVi("Dịch sang tiếng Việt:")
                .points(BigDecimal.valueOf(4.0))
                .answerKeys(new ArrayList<>())
                .build();
        qTranslation.addAnswerKey(ExamAnswerKey.builder()
                .kind(ExamAnswerKind.SAMPLE)
                .answerText("Đây là cái bút.")
                .build());

        qMcq = ExamQuestion.builder()
                .sortOrder(2)
                .type(ExamQuestionType.MULTIPLE_CHOICE)
                .promptJa("「本」の読み方は？")
                .points(BigDecimal.valueOf(2.0))
                .choices(new ArrayList<>())
                .build();
        qMcq.addChoice(ExamChoice.builder().sortOrder(1).text("ほん").correct(true).build());
        qMcq.addChoice(ExamChoice.builder().sortOrder(2).text("ぼん").correct(false).build());

        sec.addQuestion(qTranslation);
        sec.addQuestion(qMcq);
        testPaper.addSection(sec);
        paperRepository.save(testPaper);

        // Tạo bài thi đã hoàn thành (FINISHED) cho learnerId
        attempt = ExamAttempt.builder()
                .id(UUID.randomUUID().toString())
                .paper(testPaper)
                .learnerId(learnerId)
                .startedAt(Instant.now().minusSeconds(600))
                .submittedAt(Instant.now())
                .durationSeconds(600)
                .totalPoints(BigDecimal.ZERO)
                .maxPoints(BigDecimal.valueOf(6.0))
                .status(ExamAttemptStatus.FINISHED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .answers(new ArrayList<>())
                .build();

        ansTranslation = ExamAttemptAnswer.builder()
                .attempt(attempt)
                .questionId(qTranslation.getId())
                .answerValue("Cái bút này.") // Bản dịch có độ tương đồng thấp (< 75%) -> 0 điểm
                .pointsAwarded(BigDecimal.ZERO)
                .correct(false)
                .selfApproved(false)
                .build();

        ansMcq = ExamAttemptAnswer.builder()
                .attempt(attempt)
                .questionId(qMcq.getId())
                .answerValue("2") // Sai -> 0 điểm
                .pointsAwarded(BigDecimal.ZERO)
                .correct(false)
                .selfApproved(false)
                .build();

        attempt.addAnswer(ansTranslation);
        attempt.addAnswer(ansMcq);
        attempt = attemptRepository.save(attempt);
        ansTranslation = attempt.getAnswers().get(0);
        ansMcq = attempt.getAnswers().get(1);

        // Tạo một attempt khác của learner khác để test sai attempt / sai learner
        String otherLearnerId = UUID.randomUUID().toString();
        otherAttempt = ExamAttempt.builder()
                .id(UUID.randomUUID().toString())
                .paper(testPaper)
                .learnerId(otherLearnerId)
                .startedAt(Instant.now().minusSeconds(300))
                .submittedAt(Instant.now())
                .durationSeconds(300)
                .totalPoints(BigDecimal.ZERO)
                .maxPoints(BigDecimal.valueOf(6.0))
                .status(ExamAttemptStatus.FINISHED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .answers(new ArrayList<>())
                .build();

        otherAnsTranslation = ExamAttemptAnswer.builder()
                .attempt(otherAttempt)
                .questionId(qTranslation.getId())
                .answerValue("Bút.")
                .pointsAwarded(BigDecimal.ZERO)
                .correct(false)
                .selfApproved(false)
                .build();

        otherAttempt.addAnswer(otherAnsTranslation);
        otherAttempt = attemptRepository.save(otherAttempt);
        otherAnsTranslation = otherAttempt.getAnswers().get(0);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("1. Đúng attempt: Tự xác nhận thành công, cập nhật điểm tối đa, tính lại tổng điểm và xếp loại, sau đó hủy xác nhận khôi phục điểm cũ")
    void testSelfApprove_SuccessAndCancel() throws Exception {
        // Trước khi tự xác nhận: điểm câu dịch = 0.00, tổng điểm = 0.00
        assertThat(ansTranslation.isSelfApproved()).isFalse();
        assertThat(attempt.getTotalPoints()).isEqualByComparingTo(BigDecimal.ZERO);

        // Gọi POST self-approve
        mockMvc.perform(post("/exams/attempt/" + attempt.getId() + "/answer/" + ansTranslation.getId() + "/self-approve")
                        .cookie(learnerCookie)
                        .session(session)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/exams/attempt/" + attempt.getId() + "/result"))
                .andExpect(flash().attributeExists("successMessage"));

        entityManager.flush();
        entityManager.clear();

        // Kiểm tra CSDL sau khi tự xác nhận
        ExamAttempt updated = attemptRepository.findByIdWithDetails(attempt.getId()).orElseThrow();
        ExamAttemptAnswer updatedAns = updated.getAnswers().stream()
                .filter(a -> a.getId().equals(ansTranslation.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(updatedAns.isSelfApproved()).isTrue();
        assertThat(updatedAns.getPointsAwarded()).isEqualByComparingTo(BigDecimal.valueOf(4.00)); // Điểm tối đa của câu
        assertThat(updatedAns.getCorrect()).isTrue();
        assertThat(updated.getTotalPoints()).isEqualByComparingTo(BigDecimal.valueOf(4.00)); // 4.00 / 6.00 = 66.7% (Hạng B)

        // Kiểm tra render trang kết quả: có dòng "Tự xác nhận" cạnh điểm và có nút "Hủy xác nhận"
        mockMvc.perform(get("/exams/attempt/" + attempt.getId() + "/result")
                        .cookie(learnerCookie)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tự xác nhận")))
                .andExpect(content().string(containsString("Hủy xác nhận")))
                .andExpect(content().string(containsString("Hạng B")));

        // Gọi POST hủy xác nhận (action=cancel)
        mockMvc.perform(post("/exams/attempt/" + attempt.getId() + "/answer/" + ansTranslation.getId() + "/self-approve")
                        .cookie(learnerCookie)
                        .session(session)
                        .param("action", "cancel")
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/exams/attempt/" + attempt.getId() + "/result"))
                .andExpect(flash().attributeExists("successMessage"));

        entityManager.flush();
        entityManager.clear();

        // Kiểm tra CSDL sau khi hủy xác nhận: khôi phục điểm chấm tự động ban đầu (0.00)
        ExamAttempt reverted = attemptRepository.findByIdWithDetails(attempt.getId()).orElseThrow();
        ExamAttemptAnswer revertedAns = reverted.getAnswers().stream()
                .filter(a -> a.getId().equals(ansTranslation.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(revertedAns.isSelfApproved()).isFalse();
        assertThat(revertedAns.getPointsAwarded()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(reverted.getTotalPoints()).isEqualByComparingTo(BigDecimal.ZERO); // 0.00 / 6.00 = 0% (Hạng C)

        // Kiểm tra render trang kết quả sau khi hủy: hiển thị nút "Tự xác nhận"
        mockMvc.perform(get("/exams/attempt/" + attempt.getId() + "/result")
                        .cookie(learnerCookie)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tự xác nhận")))
                .andExpect(content().string(containsString("Hạng C")));
    }

    @Test
    @DisplayName("2. Sai attempt: answerId thuộc bài thi khác -> 400 Bad Request")
    void testSelfApprove_WrongAttempt_BadRequest() throws Exception {
        // otherAnsTranslation thuộc otherAttempt chứ không thuộc attempt
        mockMvc.perform(post("/exams/attempt/" + attempt.getId() + "/answer/" + otherAnsTranslation.getId() + "/self-approve")
                        .cookie(learnerCookie)
                        .session(session)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("3. Sai learner: learner_id trong cookie không khớp với attempt -> 403 Forbidden")
    void testSelfApprove_WrongLearner_Forbidden() throws Exception {
        Cookie wrongCookie = new Cookie(LearnerIdentityFilter.COOKIE_NAME, UUID.randomUUID().toString());

        mockMvc.perform(post("/exams/attempt/" + attempt.getId() + "/answer/" + ansTranslation.getId() + "/self-approve")
                        .cookie(wrongCookie)
                        .session(session)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("4. Câu hỏi không phải TRANSLATION (ví dụ MCQ) -> 400 Bad Request")
    void testSelfApprove_NonTranslationQuestion_BadRequest() throws Exception {
        // ansMcq thuộc về câu hỏi loại MULTIPLE_CHOICE
        mockMvc.perform(post("/exams/attempt/" + attempt.getId() + "/answer/" + ansMcq.getId() + "/self-approve")
                        .cookie(learnerCookie)
                        .session(session)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("5. Có CSRF bảo vệ: thiếu CSRF token -> 403 Forbidden")
    void testSelfApprove_MissingCsrf_Forbidden() throws Exception {
        mockMvc.perform(post("/exams/attempt/" + attempt.getId() + "/answer/" + ansTranslation.getId() + "/self-approve")
                        .cookie(learnerCookie))
                .andExpect(status().isForbidden());
    }
}

