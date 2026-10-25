package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.config.WebConfig;
import com.nihon.dto.PracticeAnswerForm;
import com.nihon.dto.PracticeQuestionDto;
import com.nihon.dto.PracticeResultDto;
import com.nihon.entity.Topic;
import com.nihon.service.PracticeService;
import com.nihon.service.TopicService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(PracticeController.class)
@Import({WebConfig.class, CsrfInterceptor.class})
class PracticeControllerTest {

    private static final String CSRF_TOKEN = "practice-csrf-token-xyz";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PracticeService practiceService;

    @MockitoBean
    private TopicService topicService;

    @MockitoBean
    private com.nihon.service.StudyProgressService studyProgressService;

    private Topic testTopic;
    private PracticeQuestionDto questionDto;
    private PracticeResultDto resultDto;

    @BeforeEach
    void setUp() {
        testTopic = new Topic();
        testTopic.setId(1L);
        testTopic.setName("1. Chào hỏi");

        questionDto = PracticeQuestionDto.builder()
                .vocabId(10L)
                .japaneseText("私（わたし）")
                .topicName("1. Chào hỏi")
                .topicId(1L)
                .hasKanji(true)
                .requireReading(true)
                .readingMissingInSource(false)
                .build();

        resultDto = PracticeResultDto.builder()
                .vocabId(10L)
                .japaneseText("私（わたし）")
                .topicName("1. Chào hỏi")
                .topicId(1L)
                .userReading("わたし")
                .userMeaning("Tôi")
                .readingCorrect(true)
                .meaningCorrect(true)
                .overallCorrect(true)
                .expectedReading("わたし")
                .expectedMeaning("Tôi")
                .feedbackMessage("Chính xác hoàn toàn!")
                .build();

        when(topicService.findAllOrdered()).thenReturn(List.of(testTopic));
    }

    @Test
    @DisplayName("Hiển thị trang chọn chủ đề kiểm tra thành công")
    void testIndexDisplay() throws Exception {
        mockMvc.perform(get("/practice"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/index"))
                .andExpect(model().attributeExists("topics", "activeNav"))
                .andExpect(content().string(containsString("Kiểm Tra Từ Vựng (Nhật → Việt)")));
    }

    @Test
    @DisplayName("Hiển thị câu hỏi kiểm tra thành công")
    void testShowQuestionSuccess() throws Exception {
        when(practiceService.getNextQuestion(eq(1L), any())).thenReturn(Optional.of(questionDto));

        mockMvc.perform(get("/practice/question").param("topicId", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/question"))
                .andExpect(model().attributeExists("question", "form"))
                .andExpect(content().string(containsString("私（わたし）")))
                .andExpect(content().string(containsString("Cách đọc bằng Hiragana")))
                .andExpect(content().string(containsString("Nghĩa tiếng Việt")));
    }

    @Test
    @DisplayName("Không tìm thấy câu hỏi trong chủ đề -> chuyển hướng về trang chủ kèm thông báo lỗi")
    void testShowQuestionNotFound() throws Exception {
        when(practiceService.getNextQuestion(any(), any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/practice/question").param("topicId", "99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @DisplayName("Chấm câu trả lời hợp lệ kèm token CSRF -> trả về trang kết quả chi tiết")
    void testCheckAnswerSuccess() throws Exception {
        when(practiceService.checkAnswer(any(PracticeAnswerForm.class))).thenReturn(resultDto);

        mockMvc.perform(post("/practice/check")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("vocabId", "10")
                        .param("topicId", "1")
                        .param("userReading", "わたし")
                        .param("userMeaning", "Tôi"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/result"))
                .andExpect(model().attributeExists("result"))
                .andExpect(content().string(containsString("Chính xác hoàn toàn!")))
                .andExpect(content().string(containsString("私（わたし）")))
                .andExpect(content().string(containsString("Chuyển sang từ tiếp theo")));
    }

    @Test
    @DisplayName("Thiếu CSRF token khi gửi đáp án -> trả về 403 Forbidden")
    void testCheckAnswerMissingCsrfReturnsForbidden() throws Exception {
        mockMvc.perform(post("/practice/check")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        // no csrf param
                        .param("vocabId", "10")
                        .param("userMeaning", "Tôi"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Dữ liệu nộp thiếu ID từ vựng -> chuyển hướng an toàn kèm thông báo lỗi")
    void testCheckAnswerMissingVocabId() throws Exception {
        mockMvc.perform(post("/practice/check")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("userMeaning", "Tôi"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @DisplayName("Nộp ID từ vựng không tồn tại -> chuyển hướng an toàn về trang chủ")
    void testCheckAnswerNonExistentId() throws Exception {
        when(practiceService.checkAnswer(any(PracticeAnswerForm.class)))
                .thenThrow(new NoSuchElementException("Không tìm thấy từ vựng"));

        mockMvc.perform(post("/practice/check")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("vocabId", "999")
                        .param("userMeaning", "Tôi"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice"))
                .andExpect(flash().attributeExists("errorMessage"));
    }
}
