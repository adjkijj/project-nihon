package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.config.WebConfig;
import com.nihon.dto.KanjiChoiceOptionDto;
import com.nihon.dto.KanjiPracticeMode;
import com.nihon.dto.PracticeKanjiQuestionDto;
import com.nihon.dto.PracticeKanjiResultDto;
import com.nihon.dto.TopicGroupDto;
import com.nihon.entity.Topic;
import com.nihon.service.KanjiPracticeService;
import com.nihon.service.KanjiService;
import com.nihon.service.StudyProgressService;
import com.nihon.service.TopicService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Kiểm thử Controller cho chế độ Luyện tập Kanji (F22, F23).
 */
@WebMvcTest(controllers = {KanjiPracticeController.class, GlobalExceptionHandler.class})
@Import({WebConfig.class, CsrfInterceptor.class})
class KanjiPracticeControllerTest {

    private static final String CSRF_TOKEN = "test-csrf-token-kanji-practice";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KanjiPracticeService kanjiPracticeService;

    @MockitoBean
    private KanjiService kanjiService;

    @MockitoBean
    private TopicService topicService;

    @MockitoBean
    private StudyProgressService studyProgressService;

    private Topic testTopic;
    private PracticeKanjiQuestionDto readingQuestion;
    private PracticeKanjiQuestionDto choiceQuestion;

    @BeforeEach
    void setUp() {
        testTopic = new Topic();
        testTopic.setId(1L);
        testTopic.setName("Bài 1");

        readingQuestion = PracticeKanjiQuestionDto.builder()
                .kanjiId(10L)
                .character("通")
                .mode(KanjiPracticeMode.KANJI_TO_READING)
                .topicId(1L)
                .topicName("Bài 1")
                .validReadingsCount(4)
                .build();

        choiceQuestion = PracticeKanjiQuestionDto.builder()
                .kanjiId(10L)
                .character("通")
                .mode(KanjiPracticeMode.KANJI_CHOICE)
                .topicId(1L)
                .topicName("Bài 1")
                .options(List.of(
                        new KanjiChoiceOptionDto(10L, "通", "THÔNG - Đi qua"),
                        new KanjiChoiceOptionDto(11L, "魚", "NGƯ - Con cá"),
                        new KanjiChoiceOptionDto(12L, "先", "TIÊN - Trước"),
                        new KanjiChoiceOptionDto(13L, "生", "SINH - Sống")
                ))
                .build();
    }

    // =========================================================================
    // 1. Màn hình chọn bài học và dạng bài (GET /practice/kanji)
    // =========================================================================

    @Test
    @DisplayName("GET /practice/kanji hiển thị 3 dạng bài và danh sách bài học phân nhóm")
    void index_rendersModeCardsAndTopicGroups() throws Exception {
        TopicGroupDto group = new TopicGroupDto("Kanji N5", List.of(testTopic));
        when(kanjiService.getKanjiTopics()).thenReturn(List.of(testTopic));
        when(kanjiService.getKanjiTopicGroups()).thenReturn(List.of(group));
        when(studyProgressService.getWrongKanjiIds(any(), any(), any())).thenReturn(Collections.emptySet());

        mockMvc.perform(get("/practice/kanji"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/kanji-index"))
                .andExpect(model().attributeExists("topics", "topicGroups", "selectedMode"))
                .andExpect(content().string(containsString("Đoán âm đọc (On / Kun)")))
                .andExpect(content().string(containsString("Viết chữ Kanji")))
                .andExpect(content().string(containsString("Trắc nghiệm 4 lựa chọn")))
                .andExpect(content().string(containsString("<optgroup label=\"Kanji N5\">")));
    }

    // =========================================================================
    // 2. Màn hình câu hỏi (GET /practice/kanji/question)
    // =========================================================================

    @Test
    @DisplayName("GET /practice/kanji/question (KANJI_TO_READING) không chứa đáp án trong source HTML")
    void showQuestion_kanjiToReading_doesNotLeakExpectedAnswer() throws Exception {
        when(kanjiPracticeService.getNextQuestion(any(), eq(KanjiPracticeMode.KANJI_TO_READING), any(), any(), any()))
                .thenReturn(Optional.of(readingQuestion));

        mockMvc.perform(get("/practice/kanji/question").param("mode", "KANJI_TO_READING"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/kanji-question"))
                .andExpect(content().string(containsString("kanji-large-display")))
                .andExpect(content().string(containsString("通")))
                .andExpect(content().string(containsString("💡 Hiện gợi ý")))
                // Tuyệt đối không chứa chuỗi âm đọc đáp án "つう" hay "とお"
                .andExpect(content().string(not(containsString("つう"))))
                .andExpect(content().string(not(containsString("とお"))));
    }

    @Test
    @DisplayName("GET /practice/kanji/question (KANJI_CHOICE) hiển thị 4 lựa chọn trắc nghiệm")
    void showQuestion_kanjiChoice_rendersFourOptions() throws Exception {
        when(kanjiPracticeService.getNextQuestion(any(), eq(KanjiPracticeMode.KANJI_CHOICE), any(), any(), any()))
                .thenReturn(Optional.of(choiceQuestion));

        mockMvc.perform(get("/practice/kanji/question").param("mode", "KANJI_CHOICE"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/kanji-question"))
                .andExpect(content().string(containsString("THÔNG - Đi qua")))
                .andExpect(content().string(containsString("NGƯ - Con cá")))
                .andExpect(content().string(containsString("choice-keyboard-hint")));
    }

    @Test
    @DisplayName("GET /practice/kanji/question khi hết Kanji hợp lệ chuyển hướng về trang chọn kèm thông báo")
    void showQuestion_noEligibleKanji_redirectsToIndex() throws Exception {
        when(kanjiPracticeService.getNextQuestion(any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/practice/kanji/question").param("mode", "KANJI_TO_READING"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/kanji?mode=KANJI_TO_READING"));
    }

    @Test
    @DisplayName("GET /practice/kanji/question với retryWrongOnly=true khi chưa có từ sai thì chuyển về trang chọn")
    void showQuestion_retryWrongOnlyEmpty_redirectsToIndex() throws Exception {
        when(studyProgressService.getWrongKanjiIds(any(), any(), any())).thenReturn(Collections.emptySet());

        mockMvc.perform(get("/practice/kanji/question").param("retryWrongOnly", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/kanji?mode=KANJI_TO_READING"));
    }

    // =========================================================================
    // 3. Chấm điểm phía máy chủ (POST /practice/kanji/check)
    // =========================================================================

    @Test
    @DisplayName("POST /practice/kanji/check chấm điểm thành công và hiển thị trang kết quả")
    void checkAnswer_validSubmission_rendersResultView() throws Exception {
        PracticeKanjiResultDto resultDto = PracticeKanjiResultDto.builder()
                .kanjiId(10L)
                .character("通")
                .mode(KanjiPracticeMode.KANJI_TO_READING)
                .topicId(1L)
                .topicName("Bài 1")
                .userAnswer("つう")
                .expectedAnswer("つう, とお, かよ")
                .allValidReadings(List.of("つう", "とお", "かよ"))
                .correct(true)
                .feedbackMessage("Chính xác! Âm đọc của 通 là: つう, とお, かよ")
                .hanViet("THÔNG")
                .meaningVi("Đi qua")
                .build();

        when(kanjiPracticeService.checkAnswer(any(), any())).thenReturn(resultDto);
        when(studyProgressService.getWrongKanjiIds(any(), any(), any())).thenReturn(Collections.emptySet());
        when(studyProgressService.getSummary(any(), any(), any())).thenReturn(com.nihon.dto.StudyProgressSummaryDto.builder()
                .totalAnswered(1)
                .correctCount(1)
                .incorrectCount(0)
                .accuracyPercentage(100)
                .build());

        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/practice/kanji/check")
                        .session(session)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("kanjiId", "10")
                        .param("mode", "KANJI_TO_READING")
                        .param("answer", "つう"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/kanji-result"))
                .andExpect(model().attributeExists("result", "sessionStats"))
                .andExpect(content().string(containsString("Chính xác! Xuất sắc!")))
                .andExpect(content().string(containsString("Câu tiếp theo →")));

        verify(studyProgressService).recordKanjiAttempt(
                eq(session), eq(10L), eq("通"), eq("KANJI_READING"), eq(1L), any(), eq("つう"), any(), eq(true), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVALID_MODE", "HACKER", ""})
    @DisplayName("Tham số mode không hợp lệ tự động fallback an toàn, không gây lỗi 500")
    void invalidMode_doesNotTrigger500(String badMode) throws Exception {
        when(kanjiService.getKanjiTopics()).thenReturn(List.of(testTopic));
        when(kanjiService.getKanjiTopicGroups()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/practice/kanji").param("mode", badMode))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/kanji-index"));
    }

    @Test
    @DisplayName("Tham số âm hoặc vượt ngưỡng không gây lỗi 500")
    void negativeParameters_handledSafely() throws Exception {
        when(kanjiService.getKanjiTopics()).thenReturn(List.of(testTopic));
        when(kanjiService.getKanjiTopicGroups()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/practice/kanji")
                        .param("topicId", "-9999")
                        .param("mode", "KANJI_TO_READING"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/kanji-index"))
                .andExpect(model().attributeExists("errorMessage"));
    }
}

