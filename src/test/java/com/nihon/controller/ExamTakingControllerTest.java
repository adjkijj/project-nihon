package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.dto.exam.taker.ExamAttempt;
import com.nihon.entity.ExamCategory;
import com.nihon.entity.ExamChoice;
import com.nihon.entity.ExamPaper;
import com.nihon.entity.ExamQuestion;
import com.nihon.entity.ExamQuestionType;
import com.nihon.entity.ExamSection;
import com.nihon.repository.ExamPaperRepository;
import com.nihon.service.exam.ExamTakingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExamTakingControllerTest {

    private static final String CSRF_TOKEN = "test-csrf-token-taking";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExamPaperRepository paperRepository;

    @Autowired
    private ExamTakingService takingService;

    private ExamPaper publishedPaper;
    private ExamPaper unpublishedPaper;

    @BeforeEach
    void setUp() {
        paperRepository.deleteAll();

        publishedPaper = ExamPaper.builder()
                .code("JLPT-N5-PUB")
                .title("Đề thi thử JLPT N5 Đã Xuất Bản")
                .description("Mô tả đề thi thử công khai")
                .durationMinutes(45)
                .category(ExamCategory.MOCK)
                .published(true)
                .shuffleChoices(true)
                .sections(new ArrayList<>())
                .build();

        ExamSection sec1 = ExamSection.builder()
                .title("Phần 1: Kiến thức ngôn ngữ")
                .sortOrder(1)
                .instructions("Chọn 1 phương án đúng nhất")
                .questions(new ArrayList<>())
                .build();

        ExamQuestion q1 = ExamQuestion.builder()
                .sortOrder(1)
                .type(ExamQuestionType.MULTIPLE_CHOICE)
                .promptJa("きのう、わたしは　ほんを　（　　）。")
                .promptVi("Hôm qua tôi đã (   ) sách.")
                .points(BigDecimal.valueOf(2.00))
                .explanation("Đã có 'きのう' nên dùng thì quá khứ.")
                .choices(new ArrayList<>())
                .build();

        q1.addChoice(ExamChoice.builder().sortOrder(1).text("よみました").correct(true).build());
        q1.addChoice(ExamChoice.builder().sortOrder(2).text("よみます").correct(false).build());
        q1.addChoice(ExamChoice.builder().sortOrder(3).text("よまない").correct(false).build());
        q1.addChoice(ExamChoice.builder().sortOrder(4).text("よんだ").correct(false).build());

        sec1.addQuestion(q1);
        publishedPaper.addSection(sec1);
        paperRepository.save(publishedPaper);

        unpublishedPaper = ExamPaper.builder()
                .code("JLPT-N5-DRAFT")
                .title("Đề thi bản nháp chưa công bố")
                .durationMinutes(30)
                .category(ExamCategory.REVIEW)
                .published(false)
                .build();
        paperRepository.save(unpublishedPaper);
    }

    @Test
    @DisplayName("GET /exams: Danh sách chỉ hiện đề đã xuất bản")
    void testListExamsPublishedOnly() throws Exception {
        mockMvc.perform(get("/exams"))
                .andExpect(status().isOk())
                .andExpect(view().name("exams/taker/list"))
                .andExpect(model().attributeExists("exams", "categories"))
                .andExpect(content().string(containsString("JLPT-N5-PUB")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("JLPT-N5-DRAFT"))));
    }

    @Test
    @DisplayName("GET /exams?category=MOCK: Lọc danh mục chính xác")
    void testListExamsFilterCategory() throws Exception {
        mockMvc.perform(get("/exams").param("category", "MOCK"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("JLPT-N5-PUB")));

        mockMvc.perform(get("/exams").param("category", "MIDTERM"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hiện chưa có đề thi nào")));
    }

    @Test
    @DisplayName("GET /exams/{code}: Giới thiệu đề thi đã xuất bản")
    void testExamIntroPublished() throws Exception {
        mockMvc.perform(get("/exams/" + publishedPaper.getCode()))
                .andExpect(status().isOk())
                .andExpect(view().name("exams/taker/intro"))
                .andExpect(model().attributeExists("paper"))
                .andExpect(content().string(containsString("JLPT-N5-PUB")))
                .andExpect(content().string(containsString("45 phút")))
                .andExpect(content().string(containsString("Phần 1: Kiến thức ngôn ngữ")));
    }

    @Test
    @DisplayName("GET /exams/{code}: Đề chưa xuất bản chuyển hướng về /exams với thông báo lỗi")
    void testExamIntroUnpublishedRedirects() throws Exception {
        mockMvc.perform(get("/exams/" + unpublishedPaper.getCode()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/exams"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @DisplayName("POST /exams/{code}/start: Bắt đầu bài thi và chuyển hướng vào phòng thi")
    void testStartExamAndRedirectToRoom() throws Exception {
        MockHttpSession session = new MockHttpSession();

        MvcResult result = mockMvc.perform(post("/exams/" + publishedPaper.getCode() + "/start")
                        .session(session)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("/exams/attempt/")))
                .andReturn();

        String location = result.getResponse().getRedirectedUrl();
        assertThat(location).isNotNull();
        String attemptId = location.substring(location.lastIndexOf('/') + 1);

        ExamAttempt attempt = takingService.getAttempt(attemptId, session);
        assertThat(attempt).isNotNull();
        assertThat(attempt.getPaperCode()).isEqualTo("JLPT-N5-PUB");
    }

    @Test
    @DisplayName("GET /exams/attempt/{id}: Phòng thi trực tuyến và bảng điều hướng câu hỏi")
    void testExamRoomDisplaysQuestionsAndPalette() throws Exception {
        MockHttpSession session = new MockHttpSession();
        ExamAttempt attempt = takingService.startExam(publishedPaper.getCode(), session);

        mockMvc.perform(get("/exams/attempt/" + attempt.getAttemptId()).session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("exams/taker/room"))
                .andExpect(model().attributeExists("attempt", "paper", "remainingSeconds"))
                .andExpect(content().string(containsString("JLPT-N5-PUB")))
                .andExpect(content().string(containsString("examTimerDisplay")))
                .andExpect(content().string(containsString("palette-btn-")))
                .andExpect(content().string(containsString("toggleTranslationBtn")));
    }

    @Test
    @DisplayName("GET /exams/attempt/{id}: Chặn quay lại sửa sau khi đã nộp, tự động chuyển về kết quả")
    void testExamRoomCompletedRedirectsToResult() throws Exception {
        MockHttpSession session = new MockHttpSession();
        ExamAttempt attempt = takingService.startExam(publishedPaper.getCode(), session);
        takingService.finishExam(attempt.getAttemptId(), session);

        mockMvc.perform(get("/exams/attempt/" + attempt.getAttemptId()).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/exams/attempt/" + attempt.getAttemptId() + "/result"));
    }

    @Test
    @DisplayName("POST /exams/attempt/{id}/answer: Lưu nháp qua AJAX thành công")
    void testSaveAnswerViaAjax() throws Exception {
        MockHttpSession session = new MockHttpSession();
        ExamAttempt attempt = takingService.startExam(publishedPaper.getCode(), session);
        Long questionId = attempt.getPaperDto().getSections().get(0).getQuestions().get(0).getId();

        mockMvc.perform(post("/exams/attempt/" + attempt.getAttemptId() + "/answer")
                        .session(session)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("questionId", String.valueOf(questionId))
                        .param("answer", "1001")
                        .param("markedForReview", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.questionId").value(questionId))
                .andExpect(jsonPath("$.answeredCount").value(1));

        assertThat(attempt.getAnswers()).containsEntry(questionId, "1001");
        assertThat(attempt.getMarkedForReview()).contains(questionId);
    }

    @Test
    @DisplayName("POST /exams/attempt/{id}/answer: Từ chối câu trả lời vượt quá 1000 ký tự với HTTP 400")
    void testSaveAnswerOverlongReturnsBadRequest() throws Exception {
        MockHttpSession session = new MockHttpSession();
        ExamAttempt attempt = takingService.startExam(publishedPaper.getCode(), session);
        Long questionId = attempt.getPaperDto().getSections().get(0).getQuestions().get(0).getId();

        String overlong = "B".repeat(1005);

        mockMvc.perform(post("/exams/attempt/" + attempt.getAttemptId() + "/answer")
                        .session(session)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("questionId", String.valueOf(questionId))
                        .param("answer", overlong))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("1000")));
    }

    @Test
    @DisplayName("POST /exams/attempt/{id}/finish: Nộp bài thành công và ngăn nộp lần hai")
    void testFinishExamAndPreventDoubleSubmission() throws Exception {
        MockHttpSession session = new MockHttpSession();
        ExamAttempt attempt = takingService.startExam(publishedPaper.getCode(), session);

        // Nộp lần 1
        mockMvc.perform(post("/exams/attempt/" + attempt.getAttemptId() + "/finish")
                        .session(session)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/exams/attempt/" + attempt.getAttemptId() + "/result"))
                .andExpect(flash().attributeExists("successMessage"));

        // Nộp lần 2: Vẫn an toàn chuyển hướng về kết quả
        mockMvc.perform(post("/exams/attempt/" + attempt.getAttemptId() + "/finish")
                        .session(session)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/exams/attempt/" + attempt.getAttemptId() + "/result"));
    }

    @Test
    @DisplayName("Bảo mật Session: Thí sinh từ session khác cố truy cập attemptId bị từ chối")
    void testDifferentSessionCannotAccessAttempt() throws Exception {
        MockHttpSession sessionUserA = new MockHttpSession();
        ExamAttempt attemptA = takingService.startExam(publishedPaper.getCode(), sessionUserA);

        MockHttpSession sessionUserB = new MockHttpSession();

        // User B cố vào phòng thi của User A
        mockMvc.perform(get("/exams/attempt/" + attemptA.getAttemptId()).session(sessionUserB))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/exams"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @DisplayName("Tham số lạ không gây lỗi 500")
    void testUnknownParametersDoNotCause500() throws Exception {
        mockMvc.perform(get("/exams")
                        .param("unexpectedParam", "malformed_value_!@#$%^&*()")
                        .param("category", "INVALID_CAT_NAME"))
                .andExpect(status().isOk());
    }
}
