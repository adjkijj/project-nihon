package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.config.WebConfig;
import com.nihon.dto.PracticeAnswerForm;
import com.nihon.dto.PracticeQuestionDto;
import com.nihon.dto.PracticeResultDto;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Kiểm thử tính năng F83: Tự chuyển câu trên /practice
 * - Checkbox "Tự chuyển câu" hiển thị trên /practice, /practice/question, /practice/result.
 * - Script static/js/practice-next.js được liên kết bên ngoài, không có script inline hay onclick.
 * - Lưu trạng thái vào localStorage với khóa nihon_auto_next, mặc định tắt (false).
 * - Khi phản hồi đúng và checkbox bật: chờ 650 ms rồi chuyển câu.
 * - Khi phản hồi sai: không tự động chuyển câu.
 */
@WebMvcTest(PracticeController.class)
@Import({WebConfig.class, CsrfInterceptor.class})
class PracticeAutoNextTest {

    private static final String CSRF_TOKEN = "practice-auto-next-csrf-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PracticeService practiceService;

    @MockitoBean
    private TopicService topicService;

    @MockitoBean
    private StudyProgressService studyProgressService;

    private Topic testTopic;
    private PracticeQuestionDto questionDto;
    private PracticeResultDto correctResult;
    private PracticeResultDto wrongResult;

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

        correctResult = PracticeResultDto.builder()
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

        wrongResult = PracticeResultDto.builder()
                .vocabId(10L)
                .japaneseText("私（わたし）")
                .topicName("1. Chào hỏi")
                .topicId(1L)
                .userReading("あなた")
                .userMeaning("Bạn")
                .readingCorrect(false)
                .meaningCorrect(false)
                .overallCorrect(false)
                .expectedReading("わたし")
                .expectedMeaning("Tôi")
                .feedbackMessage("Chưa chính xác.")
                .build();

        when(topicService.findAllOrdered()).thenReturn(List.of(testTopic));
    }

    @Test
    @DisplayName("Trang /practice hiển thị checkbox 'Tự chuyển câu' và liên kết script practice-next.js")
    void testIndexContainsAutoNextCheckboxAndScript() throws Exception {
        mockMvc.perform(get("/practice"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/index"))
                .andExpect(content().string(containsString("Tự chuyển câu")))
                .andExpect(content().string(containsString("id=\"autoNextToggle\"")))
                .andExpect(content().string(containsString("practice-next.js")));
    }

    @Test
    @DisplayName("Trang /practice/question hiển thị checkbox 'Tự chuyển câu' và liên kết script practice-next.js")
    void testQuestionContainsAutoNextCheckboxAndScript() throws Exception {
        when(practiceService.getNextQuestion(eq(1L), any())).thenReturn(Optional.of(questionDto));

        mockMvc.perform(get("/practice/question").param("topicId", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/question"))
                .andExpect(content().string(containsString("Tự chuyển câu")))
                .andExpect(content().string(containsString("id=\"autoNextToggle\"")))
                .andExpect(content().string(containsString("practice-next.js")));
    }

    @Test
    @DisplayName("Trang kết quả /practice/check (phản hồi đúng) chứa data-practice-correct='true', nút btnNext và checkbox Tự chuyển câu")
    void testCheckAnswerCorrectResultRendersAutoNextElements() throws Exception {
        when(practiceService.checkAnswer(any(PracticeAnswerForm.class))).thenReturn(correctResult);

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
                .andExpect(content().string(containsString("data-practice-correct=\"true\"")))
                .andExpect(content().string(containsString("status-banner-success")))
                .andExpect(content().string(containsString("id=\"btnNext\"")))
                .andExpect(content().string(containsString("Tự chuyển câu")))
                .andExpect(content().string(containsString("id=\"autoNextToggle\"")))
                .andExpect(content().string(containsString("practice-next.js")));
    }

    @Test
    @DisplayName("Trang kết quả /practice/check (phản hồi sai) chứa data-practice-correct='false'")
    void testCheckAnswerWrongResultRendersIncorrectStatus() throws Exception {
        when(practiceService.checkAnswer(any(PracticeAnswerForm.class))).thenReturn(wrongResult);

        mockMvc.perform(post("/practice/check")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("vocabId", "10")
                        .param("topicId", "1")
                        .param("userReading", "あなた")
                        .param("userMeaning", "Bạn"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/result"))
                .andExpect(content().string(containsString("data-practice-correct=\"false\"")))
                .andExpect(content().string(containsString("status-banner-danger")));
    }

    @Test
    @DisplayName("Các template practice không chứa JavaScript inline hay onclick tuân thủ CSP")
    void testTemplatesHaveNoInlineScriptOrOnclick() throws IOException {
        String[] templates = {
                "src/main/resources/templates/practice/index.html",
                "src/main/resources/templates/practice/question.html",
                "src/main/resources/templates/practice/result.html"
        };

        for (String tPath : templates) {
            String content = Files.readString(Paths.get(tPath), StandardCharsets.UTF_8)
                    .replaceAll("(?s)<!--.*?-->", "");
            assertFalse(content.matches("(?s).*<script(?![^>]*\\ssrc=)(?![^>]*\\sth:src=)[^>]*>.*"),
                    tPath + " không được chứa thẻ script inline!");
            assertFalse(content.contains("onclick="), tPath + " không được dùng onclick!");
            assertFalse(content.contains("onchange="), tPath + " không được dùng onchange inline!");
        }
    }

    @Test
    @DisplayName("File practice-next.js chứa khóa nihon_auto_next, bọc try/catch, delay 650ms và kiểm tra phản hồi đúng")
    void testScriptContainsRequiredLogic() throws IOException {
        Path scriptPath = Paths.get("src/main/resources/static/js/practice-next.js");
        assertTrue(Files.exists(scriptPath), "static/js/practice-next.js phải tồn tại!");

        String js = Files.readString(scriptPath, StandardCharsets.UTF_8);
        assertTrue(js.contains("nihon_auto_next"), "Script phải dùng khóa localStorage 'nihon_auto_next'!");
        assertTrue(js.contains("try") && js.contains("catch"), "Thao tác localStorage phải được bọc trong try/catch!");
        assertTrue(js.contains("650"), "Script phải chờ 650 ms rồi chuyển câu!");
        assertTrue(js.contains("isReactionCorrect") || js.contains("data-practice-correct") || js.contains("status-banner-success"),
                "Script phải kiểm tra phản hồi có đúng hay không!");
        assertTrue(js.contains("btnNext"), "Script phải kích hoạt nút btnNext!");
    }
}

