package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.config.WebConfig;
import com.nihon.dto.PracticeViJaAnswerForm;
import com.nihon.dto.PracticeViJaQuestionDto;
import com.nihon.dto.PracticeViJaResultDto;
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

@WebMvcTest(PracticeViJaController.class)
@Import({WebConfig.class, CsrfInterceptor.class})
class PracticeViJaControllerTest {

    private static final String CSRF_TOKEN = "vija-csrf-token-abc";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PracticeService practiceService;

    @MockitoBean
    private TopicService topicService;

    @MockitoBean
    private com.nihon.service.StudyProgressService studyProgressService;

    private Topic testTopic;
    private PracticeViJaQuestionDto questionDto;
    private PracticeViJaResultDto resultDto;

    @BeforeEach
    void setUp() {
        testTopic = new Topic();
        testTopic.setId(1L);
        testTopic.setName("1. Chào hỏi");

        questionDto = PracticeViJaQuestionDto.builder()
                .vocabId(10L)
                .meaningVi("Tôi")
                .topicName("1. Chào hỏi")
                .topicId(1L)
                .hasKanji(true)
                .build();

        resultDto = PracticeViJaResultDto.builder()
                .vocabId(10L)
                .meaningVi("Tôi")
                .userJapanese("私")
                .expectedJapanese("私（わたし）")
                .expectedReading("わたし")
                .acceptedVariants(List.of("私", "私（わたし）"))
                .correct(true)
                .topicName("1. Chào hỏi")
                .topicId(1L)
                .feedbackMessage("Chính xác! Bạn đã viết đúng từ tiếng Nhật.")
                .build();

        when(topicService.findAllOrdered()).thenReturn(List.of(testTopic));
    }

    @Test
    @DisplayName("Hiển thị trang bắt đầu chế độ Việt -> Nhật thành công")
    void testIndexDisplay() throws Exception {
        mockMvc.perform(get("/practice/vi-ja"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/vi-ja-index"))
                .andExpect(model().attributeExists("topics", "activeNav", "mode"))
                .andExpect(content().string(containsString("Kiểm Tra Từ Vựng (Việt → Nhật)")));
    }

    @Test
    @DisplayName("Hiển thị câu hỏi Việt -> Nhật thành công, hiển thị nghĩa tiếng Việt")
    void testShowQuestionSuccess() throws Exception {
        when(practiceService.getReverseQuestion(eq(1L), any())).thenReturn(Optional.of(questionDto));

        mockMvc.perform(get("/practice/vi-ja/question").param("topicId", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/vi-ja-question"))
                .andExpect(model().attributeExists("question", "form"))
                .andExpect(content().string(containsString("Tôi")))
                .andExpect(content().string(containsString("Từ tiếng Nhật (Kanji/Kana)")));
    }

    @Test
    @DisplayName("Không tìm thấy câu hỏi trong chủ đề -> chuyển hướng về index kèm thông báo lỗi")
    void testShowQuestionNotFound() throws Exception {
        when(practiceService.getReverseQuestion(any(), any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/practice/vi-ja/question").param("topicId", "99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/vi-ja"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @DisplayName("Chấm câu trả lời hợp lệ kèm token CSRF -> trả về trang kết quả chi tiết")
    void testCheckAnswerSuccess() throws Exception {
        when(practiceService.checkReverseAnswer(any(PracticeViJaAnswerForm.class))).thenReturn(resultDto);

        mockMvc.perform(post("/practice/vi-ja/check")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("vocabId", "10")
                        .param("topicId", "1")
                        .param("userJapanese", "私"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/vi-ja-result"))
                .andExpect(model().attributeExists("result"))
                .andExpect(content().string(containsString("Chính xác!")))
                .andExpect(content().string(containsString("私（わたし）")))
                .andExpect(content().string(containsString("Chuyển sang từ tiếp theo")));
    }

    @Test
    @DisplayName("Thiếu CSRF token khi gửi đáp án -> trả về 403 Forbidden")
    void testCheckAnswerMissingCsrfReturnsForbidden() throws Exception {
        mockMvc.perform(post("/practice/vi-ja/check")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        // no csrf param
                        .param("vocabId", "10")
                        .param("userJapanese", "私"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Dữ liệu nộp thiếu ID từ vựng -> chuyển hướng an toàn kèm thông báo lỗi")
    void testCheckAnswerMissingVocabId() throws Exception {
        mockMvc.perform(post("/practice/vi-ja/check")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("userJapanese", "私"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @DisplayName("Nộp ID từ vựng không tồn tại -> chuyển hướng an toàn về trang chủ")
    void testCheckAnswerNonExistentId() throws Exception {
        when(practiceService.checkReverseAnswer(any(PracticeViJaAnswerForm.class)))
                .thenThrow(new NoSuchElementException("Không tìm thấy từ vựng"));

        mockMvc.perform(post("/practice/vi-ja/check")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("vocabId", "999")
                        .param("userJapanese", "私"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/vi-ja"))
                .andExpect(flash().attributeExists("errorMessage"));
    }
}
