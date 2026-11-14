package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.dto.grammar.GrammarQuizChoiceForm;
import com.nihon.dto.grammar.GrammarQuizQuestionForm;
import com.nihon.dto.grammar.GrammarTopicForm;
import com.nihon.entity.GrammarQuizChoice;
import com.nihon.entity.GrammarQuizQuestion;
import com.nihon.entity.GrammarTopic;
import com.nihon.entity.Topic;
import com.nihon.history.LearnerIdentityFilter;
import com.nihon.repository.TopicRepository;
import com.nihon.service.grammar.GrammarService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GrammarLearningIntegrationTest {

    private static final String CSRF_TOKEN = "test-csrf-grammar-learning";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GrammarService grammarService;

    @Autowired
    private TopicRepository generalTopicRepository;

    private GrammarTopic testGrammar;
    private GrammarTopic testSupplement;
    private Topic testGeneralTopic;
    private GrammarQuizQuestion testQuestion;
    private Long correctChoiceId;
    private Long wrongChoiceId;
    private final String learnerId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        // Tạo chủ đề bài học
        testGeneralTopic = new Topic();
        testGeneralTopic.setName("Bài học Test Ngữ pháp");
        testGeneralTopic.setSortOrder(99);
        testGeneralTopic = generalTopicRepository.save(testGeneralTopic);

        // Tạo 1 chủ đề ngữ pháp liên kết với bài học
        GrammarTopicForm gForm = GrammarTopicForm.builder()
                .code("N5-TEST-LRN-01")
                .title("Cấu trúc Test Học tập")
                .category("GRAMMAR")
                .topicId(testGeneralTopic.getId())
                .formula("V-te + から")
                .explanation("Giải thích học tập: - Ý 1\n- Ý 2")
                .notes("Ghi chú học tập")
                .build();
        testGrammar = grammarService.createTopic(gForm);

        // Thêm câu hỏi trắc nghiệm
        List<GrammarQuizChoiceForm> choices = List.of(
                GrammarQuizChoiceForm.builder().sortOrder(1).text("たべて").correct(true).build(),
                GrammarQuizChoiceForm.builder().sortOrder(2).text("たべた").correct(false).build(),
                GrammarQuizChoiceForm.builder().sortOrder(3).text("たべる").correct(false).build()
        );
        GrammarQuizQuestionForm qForm = GrammarQuizQuestionForm.builder()
                .prompt("ごはんを（　）から、ねます。")
                .explanation("Sau khi ăn cơm thì đi ngủ.")
                .choices(choices)
                .correctChoiceIndex(0)
                .build();
        testQuestion = grammarService.addQuestion(testGrammar.getId(), qForm);

        for (GrammarQuizChoice c : testQuestion.getChoices()) {
            if (c.isCorrect()) {
                correctChoiceId = c.getId();
            } else if (wrongChoiceId == null) {
                wrongChoiceId = c.getId();
            }
        }

        // Tạo 1 chủ đề kiến thức bổ sung
        GrammarTopicForm sForm = GrammarTopicForm.builder()
                .code("N5-TEST-SUP-01")
                .title("Kiến thức bổ sung Test")
                .category("SUPPLEMENT")
                .explanation("Giải thích kiến thức bổ sung")
                .build();
        testSupplement = grammarService.createTopic(sForm);
    }

    @Test
    @DisplayName("F30, F35: Xem trang /grammar, lọc theo category, bài học, tìm kiếm và tham số lạ không 500")
    void testLearnerPageFilterAndSearch() throws Exception {
        Cookie learnerCookie = new Cookie(LearnerIdentityFilter.COOKIE_NAME, learnerId);

        // 1. Xem trang chính mặc định (category=GRAMMAR)
        mockMvc.perform(get("/grammar")
                        .cookie(learnerCookie))
                .andExpect(status().isOk())
                .andExpect(view().name("grammar/index"))
                .andExpect(model().attributeExists("topicsPage", "learnedGrammarIds", "progressPercent"))
                .andExpect(content().string(containsString("Cấu trúc Test Học tập")));

        // 2. Chuyển sang tab Kiến thức bổ sung (F35)
        mockMvc.perform(get("/grammar")
                        .param("category", "SUPPLEMENT")
                        .cookie(learnerCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Kiến thức bổ sung Test")));

        // 3. Lọc theo bài học
        mockMvc.perform(get("/grammar")
                        .param("category", "ALL")
                        .param("topicId", testGeneralTopic.getId().toString())
                        .cookie(learnerCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cấu trúc Test Học tập")));

        // 4. Tìm kiếm từ khóa
        mockMvc.perform(get("/grammar")
                        .param("category", "ALL")
                        .param("keyword", "Học tập")
                        .cookie(learnerCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cấu trúc Test Học tập")));

        // 5. Thử nghiệm tham số bất thường (không bao giờ gây 500)
        mockMvc.perform(get("/grammar")
                        .param("page", "-999")
                        .param("category", "UNKNOWN_CATEGORY_XYZ")
                        .param("topicId", "-1")
                        .param("keyword", "   @#$%^&*()_+<>   ")
                        .cookie(learnerCookie))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("F32 An toàn dữ liệu: Trang chưa nộp không bao giờ lộ cờ đáp án đúng ra HTML client")
    void testUnsubmittedPageDoesNotExposeCorrectFlag() throws Exception {
        Cookie learnerCookie = new Cookie(LearnerIdentityFilter.COOKIE_NAME, learnerId);

        MvcResult result = mockMvc.perform(get("/grammar")
                        .param("category", "ALL")
                        .cookie(learnerCookie))
                .andExpect(status().isOk())
                .andReturn();

        String html = result.getResponse().getContentAsString();
        // Kiểm tra trong khối trắc nghiệm không có thuộc tính hoặc text thể hiện correct
        assertThat(html).doesNotContain("choice-correct");
        assertThat(html).doesNotContain("correct=\"true\"");
        assertThat(html).doesNotContain("✓ Đáp án đúng");
    }

    @Test
    @DisplayName("F30 Tiến độ: Endpoint POST /grammar/{id}/toggle-progress hoạt động qua cả Form và AJAX")
    void testToggleProgressEndpoint() throws Exception {
        Cookie learnerCookie = new Cookie(LearnerIdentityFilter.COOKIE_NAME, learnerId);

        // 1. AJAX toggle lần 1 -> Đã học
        mockMvc.perform(post("/grammar/" + testGrammar.getId() + "/toggle-progress")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .header("Accept", "application/json")
                        .cookie(learnerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.learned").value(true))
                .andExpect(jsonPath("$.totalLearned").isNumber())
                .andExpect(jsonPath("$.progressPercent").isNumber());

        // 2. AJAX toggle lần 2 -> Bỏ trạng thái đã học
        mockMvc.perform(post("/grammar/" + testGrammar.getId() + "/toggle-progress")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .header("Accept", "application/json")
                        .cookie(learnerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.learned").value(false));

        // 3. Form POST fallback -> Redirect về /grammar
        mockMvc.perform(post("/grammar/" + testGrammar.getId() + "/toggle-progress")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .cookie(learnerCookie))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/grammar"));
    }

    @Test
    @DisplayName("F32 Chấm trắc nghiệm mini: Endpoint POST /grammar/{id}/quiz/check trả kết quả chính xác")
    void testMiniQuizCheckEndpoint() throws Exception {
        Cookie learnerCookie = new Cookie(LearnerIdentityFilter.COOKIE_NAME, learnerId);

        // Nộp đáp án qua Form/AJAX
        mockMvc.perform(post("/grammar/" + testGrammar.getId() + "/quiz/check")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("grammarId", testGrammar.getId().toString())
                        .param("answers[0].questionId", testQuestion.getId().toString())
                        .param("answers[0].choiceId", correctChoiceId.toString())
                        .header("Accept", "application/json")
                        .cookie(learnerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allCorrect").value(true))
                .andExpect(jsonPath("$.correctCount").value(1))
                .andExpect(jsonPath("$.totalQuestions").value(1))
                .andExpect(jsonPath("$.questionResults[0].correct").value(true))
                .andExpect(jsonPath("$.questionResults[0].explanation").value("Sau khi ăn cơm thì đi ngủ."));
    }

    @Test
    @DisplayName("F33 Đấu trường phản xạ: Khởi tạo, làm bài thi, streak, hết giờ/kết thúc và tổng kết")
    void testArenaLifecycle() throws Exception {
        Cookie learnerCookie = new Cookie(LearnerIdentityFilter.COOKIE_NAME, learnerId);
        MockHttpSession session = new MockHttpSession();

        // 1. Mở màn hình cấu hình đấu trường
        mockMvc.perform(get("/grammar/arena")
                        .session(session)
                        .cookie(learnerCookie))
                .andExpect(status().isOk())
                .andExpect(view().name("grammar/arena/index"))
                .andExpect(model().attributeExists("startForm", "generalTopics"));

        // 2. Bắt đầu đấu trường
        mockMvc.perform(post("/grammar/arena/start")
                        .session(session)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("category", "ALL")
                        .param("durationMinutes", "5")
                        .param("questionCount", "10")
                        .cookie(learnerCookie))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/grammar/arena/run"));

        // 3. Mở trang làm bài đấu trường
        mockMvc.perform(get("/grammar/arena/run")
                        .session(session)
                        .cookie(learnerCookie))
                .andExpect(status().isOk())
                .andExpect(view().name("grammar/arena/run"))
                .andExpect(model().attributeExists("question"))
                .andExpect(content().string(containsString("Chuỗi đúng")));

        // 4. Trả lời câu hỏi
        mockMvc.perform(post("/grammar/arena/answer")
                        .session(session)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("choiceId", correctChoiceId.toString())
                        .cookie(learnerCookie))
                .andExpect(status().is3xxRedirection());

        // 5. Kết thúc bài thi
        mockMvc.perform(post("/grammar/arena/finish")
                        .session(session)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .cookie(learnerCookie))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/grammar/arena/result"));

        // 6. Xem màn hình tổng kết kết quả
        mockMvc.perform(get("/grammar/arena/result")
                        .session(session)
                        .cookie(learnerCookie))
                .andExpect(status().isOk())
                .andExpect(view().name("grammar/arena/result"))
                .andExpect(model().attributeExists("result"))
                .andExpect(content().string(containsString("Tổng Kết Đấu Trường")));
    }

    @Test
    @DisplayName("F34 Tra nhanh thể động từ: Endpoint GET /verbs/lab trả JSON chuẩn cho widget")
    void testQuickVerbLabJsonEndpoint() throws Exception {
        mockMvc.perform(get("/verbs/lab")
                        .param("input", "のむ")
                        .param("group", "1")
                        .header("Accept", "application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasResult").value(true))
                .andExpect(jsonPath("$.dictionaryForm").value("のむ"))
                .andExpect(jsonPath("$.masuForm").value("のみます"))
                .andExpect(jsonPath("$.teForm").value("のんで"))
                .andExpect(jsonPath("$.taForm").value("のんだ"))
                .andExpect(jsonPath("$.naiForm").value("のまない"));
    }

    @Test
    @DisplayName("F39 Trắc nghiệm theo bài học: Trang chi tiết bài học /topics/{id} hiện nút 'Bài tập ngữ pháp của bài này'")
    void testTopicDetailShowsGrammarButton() throws Exception {
        mockMvc.perform(get("/topics/" + testGeneralTopic.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bài tập ngữ pháp của bài này")));
    }
}

