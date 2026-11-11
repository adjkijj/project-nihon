package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.config.WebConfig;
import com.nihon.dto.PracticeChoiceOptionDto;
import com.nihon.dto.PracticeChoiceQuestionDto;
import com.nihon.dto.PracticeChoiceResultDto;
import com.nihon.entity.Topic;
import com.nihon.service.PracticeService;
import com.nihon.service.StudyProgressService;
import com.nihon.service.TopicService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = {PracticeChoiceController.class, GlobalExceptionHandler.class})
@Import({WebConfig.class, CsrfInterceptor.class})
class PracticeChoiceControllerTest {

    private static final String CSRF_TOKEN = "practice-choice-csrf-token-123";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PracticeService practiceService;

    @MockitoBean
    private TopicService topicService;

    @MockitoBean
    private StudyProgressService studyProgressService;

    private Topic sampleTopic;
    private PracticeChoiceQuestionDto sampleQuestion;

    @BeforeEach
    void setUp() {
        sampleTopic = new Topic();
        sampleTopic.setId(1L);
        sampleTopic.setName("Chủ đề 1");

        when(topicService.findAllOrdered()).thenReturn(List.of(sampleTopic));

        PracticeChoiceOptionDto opt1 = PracticeChoiceOptionDto.builder().vocabId(1L).displayText("sách").build();
        PracticeChoiceOptionDto opt2 = PracticeChoiceOptionDto.builder().vocabId(2L).displayText("bút").build();
        PracticeChoiceOptionDto opt3 = PracticeChoiceOptionDto.builder().vocabId(3L).displayText("vở").build();
        PracticeChoiceOptionDto opt4 = PracticeChoiceOptionDto.builder().vocabId(4L).displayText("bàn").build();

        sampleQuestion = PracticeChoiceQuestionDto.builder()
                .vocabId(1L)
                .questionPrompt("本")
                .reading("ほん")
                .direction("JA_VI")
                .topicId(1L)
                .topicName("Chủ đề 1")
                .options(List.of(opt1, opt2, opt3, opt4))
                .build();
    }

    @Test
    @DisplayName("GET /practice/choice: trả về 200 và giao diện cấu hình trắc nghiệm")
    void testIndex_returns200() throws Exception {
        mockMvc.perform(get("/practice/choice"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/choice-index"))
                .andExpect(model().attribute("selectedDirection", "JA_VI"))
                .andExpect(content().string(containsString("Luyện Tập Trắc Nghiệm 4 Lựa Chọn")));
    }

    @Test
    @DisplayName("GET /practice/choice với tham số lạ (direction=abc, topicId=-1): không gây lỗi 500")
    void testIndexWithInvalidParams_handlesSafelyWithout500() throws Exception {
        mockMvc.perform(get("/practice/choice")
                        .param("direction", "abc")
                        .param("topicId", "-1"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/choice-index"))
                .andExpect(model().attribute("selectedDirection", "JA_VI"))
                .andExpect(model().attribute("errorMessage", "ID không hợp lệ"));
    }

    @Test
    @DisplayName("GET /practice/choice/question: hiển thị câu hỏi và HTML không chứa dấu hiệu đáp án đúng")
    void testShowQuestion_rendersQuestionWithoutRevealingCorrectAnswer() throws Exception {
        when(practiceService.getChoiceQuestion(eq(1L), eq("JA_VI"), isNull()))
                .thenReturn(Optional.of(sampleQuestion));

        mockMvc.perform(get("/practice/choice/question")
                        .param("topicId", "1")
                        .param("direction", "JA_VI"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/choice-question"))
                .andExpect(content().string(containsString("本")))
                .andExpect(content().string(containsString("sách")))
                .andExpect(content().string(containsString("bút")))
                .andExpect(content().string(containsString("vở")))
                .andExpect(content().string(containsString("bàn")))
                // HTML câu hỏi tuyệt đối KHÔNG chứa class hoặc thuộc tính báo đáp án đúng
                .andExpect(content().string(not(containsString("is-correct"))))
                .andExpect(content().string(not(containsString("correct-answer"))))
                .andExpect(content().string(not(containsString("data-correct"))));
    }

    @Test
    @DisplayName("GET /practice/choice/question khi không đủ 4 từ: báo lỗi và chuyển hướng về trang chọn")
    void testShowQuestionWhenInsufficientData_redirectsWithFlashError() throws Exception {
        when(practiceService.getChoiceQuestion(eq(1L), eq("JA_VI"), isNull()))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/practice/choice/question")
                        .param("topicId", "1")
                        .param("direction", "JA_VI"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/choice?topicId=1&direction=JA_VI"))
                .andExpect(flash().attribute("errorMessage", containsString("Chưa đủ dữ liệu để tạo câu hỏi")));
    }

    @Test
    @DisplayName("POST /practice/choice/check: chấm đúng và trả về kết quả chính xác")
    void testCheckAnswer_correctAnswerReturnsResult() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN);
        session.setAttribute(PracticeChoiceController.SESSION_CHOICE_VOCAB_ID, 1L);
        session.setAttribute(PracticeChoiceController.SESSION_CHOICE_OPTION_IDS, List.of(1L, 2L, 3L, 4L));

        PracticeChoiceResultDto result = PracticeChoiceResultDto.builder()
                .vocabId(1L)
                .selectedVocabId(1L)
                .correct(true)
                .direction("JA_VI")
                .correctJapaneseText("本")
                .correctMeaningVi("sách")
                .feedbackMessage("Chính xác! Bạn đã chọn đáp án đúng.")
                .build();

        when(practiceService.checkChoiceAnswer(any(), any())).thenReturn(result);

        mockMvc.perform(post("/practice/choice/check")
                        .param("_csrf", CSRF_TOKEN)
                        .param("vocabId", "1")
                        .param("choiceVocabId", "1")
                        .param("direction", "JA_VI")
                        .param("topicId", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/choice-result"))
                .andExpect(model().attributeExists("result"))
                .andExpect(content().string(containsString("Chính xác!")));
    }

    @Test
    @DisplayName("POST /practice/choice/check: chấm sai và hiển thị phản hồi chi tiết")
    void testCheckAnswer_incorrectAnswerReturnsResult() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN);
        session.setAttribute(PracticeChoiceController.SESSION_CHOICE_VOCAB_ID, 1L);
        session.setAttribute(PracticeChoiceController.SESSION_CHOICE_OPTION_IDS, List.of(1L, 2L, 3L, 4L));

        PracticeChoiceResultDto result = PracticeChoiceResultDto.builder()
                .vocabId(1L)
                .selectedVocabId(2L)
                .correct(false)
                .direction("JA_VI")
                .correctJapaneseText("本")
                .correctMeaningVi("sách")
                .selectedAnswerText("bút")
                .feedbackMessage("Chưa chính xác! Bạn đã chọn: bút. Đáp án đúng là: sách.")
                .build();

        when(practiceService.checkChoiceAnswer(any(), any())).thenReturn(result);

        mockMvc.perform(post("/practice/choice/check")
                        .param("_csrf", CSRF_TOKEN)
                        .param("vocabId", "1")
                        .param("choiceVocabId", "2")
                        .param("direction", "JA_VI")
                        .param("topicId", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/choice-result"))
                .andExpect(model().attributeExists("result"))
                .andExpect(content().string(containsString("Chưa chính xác!")));
    }

    @Test
    @DisplayName("POST /practice/choice/check: từ chối choiceVocabId không nằm trong bộ lựa chọn đã sinh")
    void testCheckAnswer_rejectsChoiceNotInGeneratedOptions() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN);
        session.setAttribute(PracticeChoiceController.SESSION_CHOICE_VOCAB_ID, 1L);
        session.setAttribute(PracticeChoiceController.SESSION_CHOICE_OPTION_IDS, List.of(1L, 2L, 3L, 4L));

        mockMvc.perform(post("/practice/choice/check")
                        .param("_csrf", CSRF_TOKEN)
                        .param("vocabId", "1")
                        .param("choiceVocabId", "999") // không nằm trong [1, 2, 3, 4]
                        .param("direction", "JA_VI")
                        .param("topicId", "1")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/choice/question?topicId=1&direction=JA_VI"))
                .andExpect(flash().attribute("errorMessage", containsString("Phương án lựa chọn không hợp lệ.")));
    }

    @Test
    @DisplayName("POST /practice/choice/check: khi không có bộ lựa chọn trong session (hết hạn) -> chuyển hướng và báo lỗi")
    void testCheckAnswer_whenSessionExpired_redirectsWithExpiredMessage() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN);
        // Không set CHOICE_QUESTION_VOCAB_ID và CHOICE_QUESTION_OPTION_IDS trong session

        mockMvc.perform(post("/practice/choice/check")
                        .param("_csrf", CSRF_TOKEN)
                        .param("vocabId", "1")
                        .param("choiceVocabId", "1")
                        .param("direction", "JA_VI")
                        .param("topicId", "1")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/choice?topicId=1&direction=JA_VI"))
                .andExpect(flash().attribute("errorMessage", containsString("Câu hỏi đã hết hạn")));
    }
}

