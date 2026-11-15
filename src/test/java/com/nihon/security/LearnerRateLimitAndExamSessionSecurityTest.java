package com.nihon.security;

import com.nihon.config.CsrfInterceptor;
import com.nihon.config.RateLimitInterceptor;
import com.nihon.dto.exam.taker.ExamSessionAttempt;
import com.nihon.entity.ExamCategory;
import com.nihon.entity.ExamChoice;
import com.nihon.entity.ExamPaper;
import com.nihon.entity.ExamQuestion;
import com.nihon.entity.ExamQuestionType;
import com.nihon.entity.ExamSection;
import com.nihon.entity.GrammarCategory;
import com.nihon.entity.GrammarTopic;
import com.nihon.history.LearnerIdentityFilter;
import com.nihon.repository.ExamPaperRepository;
import com.nihon.repository.GrammarTopicRepository;
import com.nihon.service.excel.RateLimiterService;
import com.nihon.service.exam.ExamTakingService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LearnerRateLimitAndExamSessionSecurityTest {

    private static final String CSRF_TOKEN = "test-csrf-token-learner";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExamTakingService examTakingService;

    @Autowired
    private ExamPaperRepository paperRepository;

    @Autowired
    private GrammarTopicRepository grammarTopicRepository;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private RateLimitInterceptor rateLimitInterceptor;

    private ExamPaper testPaper;
    private GrammarTopic testGrammar;
    private Long questionId;

    @BeforeEach
    void setUp() {
        rateLimiterService.reset();

        // Đặt lại các ngưỡng mặc định trong interceptor
        rateLimitInterceptor.setExamStartLimit(10);
        rateLimitInterceptor.setExamAnswerLimit(60);
        rateLimitInterceptor.setExamFinishLimit(10);
        rateLimitInterceptor.setGrammarQuizLimit(30);
        rateLimitInterceptor.setHistoryDeleteLimit(5);

        // Chuẩn bị đề thi mẫu
        paperRepository.deleteAll();

        testPaper = ExamPaper.builder()
                .code("JLPT-N5-SEC-TEST")
                .title("Đề thi kiểm thử bảo mật Rate Limit")
                .description("Đề thi phục vụ kiểm thử RateLimiterService")
                .durationMinutes(30)
                .category(ExamCategory.MOCK)
                .published(true)
                .shuffleChoices(false)
                .sections(new ArrayList<>())
                .build();

        ExamSection section = ExamSection.builder()
                .title("Phần trắc nghiệm")
                .sortOrder(1)
                .instructions("Chọn câu trả lời đúng")
                .questions(new ArrayList<>())
                .build();

        ExamQuestion question = ExamQuestion.builder()
                .sortOrder(1)
                .type(ExamQuestionType.MULTIPLE_CHOICE)
                .promptJa("テスト問題（　）。")
                .promptVi("Câu hỏi kiểm thử (  ).")
                .points(BigDecimal.valueOf(10.0))
                .choices(new ArrayList<>())
                .build();

        question.addChoice(ExamChoice.builder().sortOrder(1).text("です").correct(true).build());
        question.addChoice(ExamChoice.builder().sortOrder(2).text("ます").correct(false).build());

        section.addQuestion(question);
        testPaper.addSection(section);
        testPaper = paperRepository.save(testPaper);

        questionId = testPaper.getSections().get(0).getQuestions().get(0).getId();

        // Chuẩn bị chủ đề ngữ pháp mẫu
        testGrammar = grammarTopicRepository.findByCode("TEST-GRAMMAR-RATE-LIMIT")
                .orElseGet(() -> grammarTopicRepository.save(GrammarTopic.builder()
                        .code("TEST-GRAMMAR-RATE-LIMIT")
                        .title("Cấu trúc ngữ pháp kiểm thử Rate Limit")
                        .category(GrammarCategory.GRAMMAR)
                        .formula("V-ru + koto")
                        .explanation("Giải thích ngữ pháp")
                        .build()));
    }

    // =========================================================================
    // 1. Kiểm thử Rate Limit: Bắt đầu bài thi (POST /exams/{code}/start)
    // =========================================================================
    @Test
    @DisplayName("Endpoint 1: POST /exams/{code}/start - Vượt quá giới hạn trả về HTTP 429 với thông báo chung")
    void testExamStartRateLimiter() throws Exception {
        rateLimitInterceptor.setExamStartLimit(2);
        MockHttpSession session = new MockHttpSession();

        // Gửi 2 request hợp lệ đầu tiên -> Chuyển hướng 302 vào phòng thi
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/exams/" + testPaper.getCode() + "/start")
                            .session(session)
                            .cookie(new Cookie(CsrfInterceptor.CSRF_COOKIE_NAME, CSRF_TOKEN))
                            .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                    .andExpect(status().is3xxRedirection());
        }

        // Request thứ 3 vượt ngưỡng 2 lượt/phút -> HTTP 429 Too Many Requests
        mockMvc.perform(post("/exams/" + testPaper.getCode() + "/start")
                        .session(session)
                        .cookie(new Cookie(CsrfInterceptor.CSRF_COOKIE_NAME, CSRF_TOKEN))
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().isTooManyRequests())
                .andExpect(view().name("error"))
                .andExpect(content().string(containsString("429 - Quá giới hạn tần suất")))
                .andExpect(content().string(containsString("Bạn đã thực hiện thao tác quá nhiều lần")))
                .andExpect(content().string(not(containsString("127.0.0.1"))))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("org.springframework"))));
    }

    // =========================================================================
    // 2. Kiểm thử Rate Limit: Nộp đáp án (POST /exams/attempt/{id}/answer)
    // =========================================================================
    @Test
    @DisplayName("Endpoint 2: POST /exams/attempt/{id}/answer - Vượt quá giới hạn trả về HTTP 429 dạng JSON không lộ vết nội bộ")
    void testExamAnswerRateLimiter() throws Exception {
        rateLimitInterceptor.setExamAnswerLimit(2);
        MockHttpSession session = new MockHttpSession();

        // Khởi tạo bài thi trong session
        ExamSessionAttempt attempt = examTakingService.startExam(testPaper.getCode(), session);

        // Gửi 2 request nộp đáp án đầu tiên -> 200 OK
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/exams/attempt/" + attempt.getAttemptId() + "/answer")
                            .session(session)
                            .cookie(new Cookie(CsrfInterceptor.CSRF_COOKIE_NAME, CSRF_TOKEN))
                            .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                            .param("questionId", String.valueOf(questionId))
                            .param("answer", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("OK"));
        }

        // Request thứ 3 vượt ngưỡng -> HTTP 429 dạng JSON
        mockMvc.perform(post("/exams/attempt/" + attempt.getAttemptId() + "/answer")
                        .session(session)
                        .header("Accept", "application/json")
                        .cookie(new Cookie(CsrfInterceptor.CSRF_COOKIE_NAME, CSRF_TOKEN))
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("questionId", String.valueOf(questionId))
                        .param("answer", "1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value("Bạn đã thực hiện thao tác quá nhiều lần. Vui lòng thử lại sau ít phút."));
    }

    // =========================================================================
    // 3. Kiểm thử Rate Limit: Kết thúc bài thi (POST /exams/attempt/{id}/finish)
    // =========================================================================
    @Test
    @DisplayName("Endpoint 3: POST /exams/attempt/{id}/finish - Vượt quá giới hạn trả về HTTP 429")
    void testExamFinishRateLimiter() throws Exception {
        rateLimitInterceptor.setExamFinishLimit(2);
        MockHttpSession session = new MockHttpSession();
        ExamSessionAttempt attempt = examTakingService.startExam(testPaper.getCode(), session);

        // 2 lần đầu nộp bài hợp lệ
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/exams/attempt/" + attempt.getAttemptId() + "/finish")
                            .session(session)
                            .cookie(new Cookie(CsrfInterceptor.CSRF_COOKIE_NAME, CSRF_TOKEN))
                            .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                    .andExpect(status().is3xxRedirection());
        }

        // Lần thứ 3 vượt ngưỡng -> HTTP 429
        mockMvc.perform(post("/exams/attempt/" + attempt.getAttemptId() + "/finish")
                        .session(session)
                        .cookie(new Cookie(CsrfInterceptor.CSRF_COOKIE_NAME, CSRF_TOKEN))
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().isTooManyRequests())
                .andExpect(view().name("error"))
                .andExpect(content().string(containsString("429 - Quá giới hạn tần suất")));
    }

    // =========================================================================
    // 4. Kiểm thử Rate Limit: Kiểm tra quiz ngữ pháp (POST /grammar/{id}/quiz/check)
    // =========================================================================
    @Test
    @DisplayName("Endpoint 4: POST /grammar/{id}/quiz/check - Vượt quá giới hạn trả về HTTP 429 (cả HTML và JSON)")
    void testGrammarQuizCheckRateLimiter() throws Exception {
        rateLimitInterceptor.setGrammarQuizLimit(2);
        MockHttpSession session = new MockHttpSession();

        // 2 lần đầu kiểm tra quiz ngữ pháp
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/grammar/" + testGrammar.getId() + "/quiz/check")
                            .session(session)
                            .cookie(new Cookie(CsrfInterceptor.CSRF_COOKIE_NAME, CSRF_TOKEN))
                            .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                            .param("selectedChoiceId", "1"))
                    .andExpect(status().is3xxRedirection());
        }

        // Lần thứ 3 vượt ngưỡng -> HTTP 429
        mockMvc.perform(post("/grammar/" + testGrammar.getId() + "/quiz/check")
                        .session(session)
                        .cookie(new Cookie(CsrfInterceptor.CSRF_COOKIE_NAME, CSRF_TOKEN))
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("selectedChoiceId", "1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(view().name("error"))
                .andExpect(content().string(containsString("429 - Quá giới hạn tần suất")));

        // Kiểm tra nhánh trả về JSON nếu Accept: application/json
        mockMvc.perform(post("/grammar/" + testGrammar.getId() + "/quiz/check")
                        .session(session)
                        .header("Accept", "application/json")
                        .cookie(new Cookie(CsrfInterceptor.CSRF_COOKIE_NAME, CSRF_TOKEN))
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("selectedChoiceId", "1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("Bạn đã thực hiện thao tác quá nhiều lần. Vui lòng thử lại sau ít phút."));
    }

    // =========================================================================
    // 5. Kiểm thử Rate Limit: Xóa lịch sử học tập (POST /history/delete-all)
    // =========================================================================
    @Test
    @DisplayName("Endpoint 5: POST /history/delete-all - Vượt quá giới hạn trả về HTTP 429")
    void testHistoryDeleteAllRateLimiter() throws Exception {
        rateLimitInterceptor.setHistoryDeleteLimit(2);
        String learnerId = UUID.randomUUID().toString();

        // 2 lần đầu xóa lịch sử
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/history/delete-all")
                            .cookie(new Cookie(LearnerIdentityFilter.COOKIE_NAME, learnerId))
                            .cookie(new Cookie(CsrfInterceptor.CSRF_COOKIE_NAME, CSRF_TOKEN))
                            .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                    .andExpect(status().is3xxRedirection());
        }

        // Lần thứ 3 vượt ngưỡng -> HTTP 429
        mockMvc.perform(post("/history/delete-all")
                        .cookie(new Cookie(LearnerIdentityFilter.COOKIE_NAME, learnerId))
                        .cookie(new Cookie(CsrfInterceptor.CSRF_COOKIE_NAME, CSRF_TOKEN))
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().isTooManyRequests())
                .andExpect(view().name("error"))
                .andExpect(content().string(containsString("429 - Quá giới hạn tần suất")));
    }

    // =========================================================================
    // 6. Kiểm thử Giới hạn số bài thi trong HttpSession là 5 (SEC-006)
    // =========================================================================
    @Test
    @DisplayName("SEC-006: Giới hạn số bài thi đang mở trong HttpSession là 5. Khi vượt, bài cũ nhất tự động bị xóa")
    void testExamSessionMaxAttemptsLimit5() {
        MockHttpSession session = new MockHttpSession();
        Instant baseTime = Instant.parse("2026-11-15T10:00:00Z");

        // Tạo 6 bài thi với thời gian startedAt tăng dần
        List<String> attemptIds = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            String attemptId = "attempt-" + i;
            attemptIds.add(attemptId);

            ExamSessionAttempt attempt = ExamSessionAttempt.builder()
                    .attemptId(attemptId)
                    .learnerId("learner-test")
                    .paperCode(testPaper.getCode())
                    .paperTitle("Bài thi số " + i)
                    .startedAt(baseTime.plusSeconds(i * 60)) // Cách nhau 1 phút
                    .durationMinutes(30)
                    .build();

            examTakingService.putSessionAttempt(session, attempt);
        }

        // Kiểm tra trong session
        @SuppressWarnings("unchecked")
        Map<String, ExamSessionAttempt> attemptsMap =
                (Map<String, ExamSessionAttempt>) session.getAttribute(ExamSessionAttempt.SESSION_ATTEMPTS_MAP);

        assertThat(attemptsMap).isNotNull();
        // Giới hạn cứng tối đa 5 bài thi
        assertThat(attemptsMap).hasSize(5);

        // Bài thi thứ 1 (cũ nhất, startedAt sớm nhất lúc 10:01) phải bị xóa khỏi session
        assertThat(attemptsMap.containsKey(attemptIds.get(0))).isFalse();
        assertThat(examTakingService.getAttempt(attemptIds.get(0), session)).isNull();

        // Các bài thi từ 2 đến 6 (5 bài gần nhất) vẫn còn nguyên trong session
        for (int i = 1; i <= 5; i++) {
            assertThat(attemptsMap.containsKey(attemptIds.get(i))).isTrue();
            assertThat(examTakingService.getAttempt(attemptIds.get(i), session)).isNotNull();
        }
    }

    // =========================================================================
    // 7. Kiểm thử Đọc cấu hình từ application.properties
    // =========================================================================
    @Test
    @DisplayName("Cấu hình: Các ngưỡng rate limit và max-attempts được đọc chính xác từ application.properties")
    void testConfiguredLimitsFromProperties() {
        assertThat(examTakingService.getMaxSessionAttempts()).isEqualTo(5);
        assertThat(examTakingService.getMaxSessionAttempts()).isEqualTo(ExamTakingService.MAX_SESSION_ATTEMPTS);
    }
}
