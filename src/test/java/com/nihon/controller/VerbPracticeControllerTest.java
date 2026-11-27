package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.config.WebConfig;
import com.nihon.conjugation.TargetForm;
import com.nihon.dto.verb.PracticeVerbQuestionDto;
import com.nihon.dto.verb.PracticeVerbResultDto;
import com.nihon.entity.Topic;
import com.nihon.service.StudyProgressService;
import com.nihon.service.TopicService;
import com.nihon.service.VerbPracticeService;
import com.nihon.service.VerbService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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

@WebMvcTest(controllers = {VerbPracticeController.class, GlobalExceptionHandler.class})
@Import({WebConfig.class, CsrfInterceptor.class})
class VerbPracticeControllerTest {

    private static final String CSRF_TOKEN = "test-csrf-token-vp-12345";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VerbPracticeService verbPracticeService;

    @MockitoBean
    private VerbService verbService;

    @MockitoBean
    private TopicService topicService;

    @MockitoBean
    private StudyProgressService studyProgressService;

    private Topic testTopic;

    @BeforeEach
    void setUp() {
        testTopic = new Topic();
        testTopic.setId(10L);
        testTopic.setName("Bài 1. Động từ");

        when(topicService.findAllOrdered()).thenReturn(List.of(testTopic));
        when(studyProgressService.getWrongVerbIds(any(), anyString(), any())).thenReturn(Collections.emptySet());
    }

    @Test
    @DisplayName("1. GET /verbs/practice: Trả về trang cấu hình với danh sách chủ đề và số từ sai")
    void testIndexSuccess() throws Exception {
        when(studyProgressService.getWrongVerbIds(any(), anyString(), any())).thenReturn(Set.of(1L, 2L));

        mockMvc.perform(get("/verbs/practice")
                        .param("targetForm", "TE")
                        .param("mode", "INPUT"))
                .andExpect(status().isOk())
                .andExpect(view().name("verbs/practice-index"))
                .andExpect(model().attributeExists("topics"))
                .andExpect(model().attribute("selectedForm", "TE"))
                .andExpect(model().attribute("selectedMode", "INPUT"))
                .andExpect(model().attribute("wrongCount", 2));
    }

    @Test
    @DisplayName("2. GET /verbs/practice/question: Hiển thị câu hỏi tự luận INPUT, tuyệt đối không lộ đáp án trong HTML")
    void testQuestionInputModeDoesNotLeakAnswer() throws Exception {
        PracticeVerbQuestionDto question = PracticeVerbQuestionDto.builder()
                .verbId(1L)
                .masuForm("のみます")
                .dictionaryForm("のむ")
                .kanjiText("飲む")
                .meaningVi("Uống")
                .verbGroup(1)
                .targetForm(TargetForm.TE)
                .prompt("のみます")
                .speechText("のみます")
                .mode("INPUT")
                .currentStreak(2)
                .build();

        when(verbPracticeService.getNextQuestion(any(), any(), any(), eq("INPUT"), any(), eq(false), any()))
                .thenReturn(Optional.of(question));

        mockMvc.perform(get("/verbs/practice/question")
                        .param("targetForm", "TE")
                        .param("mode", "INPUT"))
                .andExpect(status().isOk())
                .andExpect(view().name("verbs/practice-question"))
                .andExpect(model().attributeExists("question", "form"))
                .andExpect(content().string(containsString("のみます")))
                .andExpect(content().string(containsString("Uống")))
                .andExpect(content().string(containsString("verbTextInput")))
                // Đáp án đúng là "のんで" / "飲んで" - TUYỆT ĐỐI không được xuất hiện trên HTML câu hỏi!
                .andExpect(content().string(not(containsString("のんで"))))
                .andExpect(content().string(not(containsString("飲んで"))));
    }

    @Test
    @DisplayName("3. GET /verbs/practice/question: Hiển thị câu hỏi trắc nghiệm CHOICE với 4 phương án")
    void testQuestionChoiceMode() throws Exception {
        PracticeVerbQuestionDto question = PracticeVerbQuestionDto.builder()
                .verbId(2L)
                .masuForm("たべます")
                .dictionaryForm("たべる")
                .kanjiText("食べる")
                .meaningVi("Ăn")
                .verbGroup(2)
                .targetForm(TargetForm.TE)
                .prompt("たべます")
                .speechText("たべます")
                .mode("CHOICE")
                .choices(List.of("たべて", "たべって", "たべんで", "たべいて"))
                .currentStreak(0)
                .build();

        when(verbPracticeService.getNextQuestion(any(), any(), any(), eq("CHOICE"), any(), eq(false), any()))
                .thenReturn(Optional.of(question));

        mockMvc.perform(get("/verbs/practice/question")
                        .param("targetForm", "TE")
                        .param("mode", "CHOICE"))
                .andExpect(status().isOk())
                .andExpect(view().name("verbs/practice-question"))
                .andExpect(content().string(containsString("vp-choice-btn")))
                .andExpect(content().string(containsString("たべて")))
                .andExpect(content().string(containsString("たべって")));
    }

    @Test
    @DisplayName("4. GET /verbs/practice/question: Chế độ ôn từ sai chuyển hướng nếu chưa có từ sai")
    void testQuestionRetryWrongOnlyRedirectWhenEmpty() throws Exception {
        when(verbPracticeService.getNextQuestion(any(), any(), any(), any(), any(), eq(true), any()))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/verbs/practice/question")
                        .param("targetForm", "TE")
                        .param("retryWrongOnly", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/verbs/practice?targetForm=TE&mode=INPUT"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @DisplayName("5. POST /verbs/practice/check: Trả lời đúng nhận kết quả chính xác và tăng streak")
    void testCheckAnswerCorrect() throws Exception {
        PracticeVerbResultDto result = PracticeVerbResultDto.builder()
                .verbId(1L)
                .prompt("のみます")
                .targetForm(TargetForm.TE)
                .userAnswer("のんで")
                .expectedKana("のんで")
                .expectedKanji("飲んで")
                .correct(true)
                .feedbackRule("Nhóm 1 đuôi む -> んで")
                .verbGroup(1)
                .meaningVi("Uống")
                .mode("INPUT")
                .currentStreak(1)
                .build();

        when(verbPracticeService.checkAnswer(any(), any())).thenReturn(result);

        mockMvc.perform(post("/verbs/practice/check")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param("_csrf", CSRF_TOKEN)
                        .param("verbId", "1")
                        .param("targetForm", "TE")
                        .param("mode", "INPUT")
                        .param("answer", "のんで"))
                .andExpect(status().isOk())
                .andExpect(view().name("verbs/practice-result"))
                .andExpect(model().attribute("result", result))
                .andExpect(content().string(containsString("CHÍNH XÁC")))
                .andExpect(content().string(containsString("のんで (飲んで)")));
    }

    @Test
    @DisplayName("6. POST /verbs/practice/check: Trả lời sai nhận giải thích quy tắc chia thể")
    void testCheckAnswerIncorrectWithFeedbackRule() throws Exception {
        PracticeVerbResultDto result = PracticeVerbResultDto.builder()
                .verbId(1L)
                .prompt("のみます")
                .targetForm(TargetForm.TE)
                .userAnswer("のんだ")
                .expectedKana("のんで")
                .expectedKanji("飲んで")
                .correct(false)
                .feedbackRule("Nhóm 1 đuôi む: chuyển む thành んで (ví dụ: のむ ➔ のんで).")
                .verbGroup(1)
                .meaningVi("Uống")
                .mode("INPUT")
                .currentStreak(0)
                .build();

        when(verbPracticeService.checkAnswer(any(), any())).thenReturn(result);

        mockMvc.perform(post("/verbs/practice/check")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param("_csrf", CSRF_TOKEN)
                        .param("verbId", "1")
                        .param("targetForm", "TE")
                        .param("mode", "INPUT")
                        .param("answer", "のんだ"))
                .andExpect(status().isOk())
                .andExpect(view().name("verbs/practice-result"))
                .andExpect(model().attribute("result", result))
                .andExpect(content().string(containsString("CHƯA CHÍNH XÁC")))
                .andExpect(content().string(containsString("Quy tắc chia thể")));
    }

    @Test
    @DisplayName("7. POST /verbs/practice/check: Form validation lỗi khi thiếu verbId hoặc answer vượt 100 ký tự")
    void testCheckAnswerValidationFailure() throws Exception {
        mockMvc.perform(post("/verbs/practice/check")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param("_csrf", CSRF_TOKEN)
                        .param("targetForm", "TE")
                        .param("answer", "a".repeat(105)))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
    }
}
