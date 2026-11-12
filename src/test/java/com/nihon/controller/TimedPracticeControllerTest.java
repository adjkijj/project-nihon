package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.config.WebConfig;
import com.nihon.dto.timed.TimedAnswerForm;
import com.nihon.dto.timed.TimedAnswerRecord;
import com.nihon.dto.timed.TimedEvaluationMode;
import com.nihon.dto.timed.TimedPracticeType;
import com.nihon.dto.timed.TimedQuestionDto;
import com.nihon.dto.timed.TimedQuestionItem;
import com.nihon.dto.timed.TimedResultDto;
import com.nihon.dto.timed.TimedSession;
import com.nihon.dto.timed.TimedSessionStatus;
import com.nihon.dto.timed.TimedStartForm;
import com.nihon.entity.Topic;
import com.nihon.service.TimedPracticeService;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = {TimedPracticeController.class, GlobalExceptionHandler.class})
@Import({WebConfig.class, CsrfInterceptor.class})
class TimedPracticeControllerTest {

    private static final String CSRF_TOKEN = "test-csrf-timed-practice-xyz";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TimedPracticeService timedPracticeService;

    @MockitoBean
    private TopicService topicService;

    private MockHttpSession mockSession;
    private Topic sampleTopic;

    @BeforeEach
    void setUp() {
        mockSession = new MockHttpSession();
        mockSession.setAttribute(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN);

        sampleTopic = new Topic();
        sampleTopic.setId(1L);
        sampleTopic.setName("Chủ đề 1: Chào hỏi");
        when(topicService.findAllOrdered()).thenReturn(List.of(sampleTopic));
    }

    // ==========================================
    // 1. GET /practice/timed (Config Page)
    // ==========================================

    @Test
    @DisplayName("GET /practice/timed: Hiển thị form cấu hình khi chưa có bài thi nào đang diễn ra")
    void testIndex_noActiveSession() throws Exception {
        when(timedPracticeService.getActiveSession(any())).thenReturn(null);

        mockMvc.perform(get("/practice/timed").session(mockSession))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/timed-index"))
                .andExpect(model().attributeExists("topics", "startForm", "activeNav"))
                .andExpect(model().attributeDoesNotExist("activeSession"))
                .andExpect(content().string(containsString("Đấu trường tốc độ")));
    }

    @Test
    @DisplayName("GET /practice/timed: Hiển thị banner cảnh báo phiên đang chạy khi có bài thi RUNNING")
    void testIndex_withActiveSession() throws Exception {
        TimedSession runningSession = TimedSession.builder()
                .id(UUID.randomUUID().toString())
                .type(TimedPracticeType.JA_VI)
                .questionCount(10)
                .durationMinutes(5)
                .status(TimedSessionStatus.RUNNING)
                .currentIndex(2)
                .build();
        when(timedPracticeService.getActiveSession(any())).thenReturn(runningSession);

        mockMvc.perform(get("/practice/timed").session(mockSession))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/timed-index"))
                .andExpect(model().attributeExists("activeSession"))
                .andExpect(content().string(containsString("bài thi tính giờ đang diễn ra")));
    }

    // ==========================================
    // 2. POST /practice/timed/start (Start Session)
    // ==========================================

    @Test
    @DisplayName("POST /practice/timed/start: Bắt đầu bài thi thành công và chuyển hướng đến /practice/timed/run")
    void testStart_success() throws Exception {
        when(timedPracticeService.getActiveSession(any())).thenReturn(null);

        TimedSession started = TimedSession.builder()
                .id("test-session-id")
                .type(TimedPracticeType.JA_VI)
                .questionCount(10)
                .durationMinutes(5)
                .status(TimedSessionStatus.RUNNING)
                .build();
        when(timedPracticeService.startSession(any(TimedStartForm.class), any())).thenReturn(started);

        mockMvc.perform(post("/practice/timed/start")
                        .session(mockSession)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("type", "JA_VI")
                        .param("questionCount", "10")
                        .param("durationMinutes", "5")
                        .param("evaluationMode", "INSTANT"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/timed/run"));

        verify(timedPracticeService).startSession(any(TimedStartForm.class), any());
    }

    @Test
    @DisplayName("POST /practice/timed/start: Lỗi validation (thiếu type) chuyển hướng về trang cấu hình với flash error")
    void testStart_validationError_missingType() throws Exception {
        mockMvc.perform(post("/practice/timed/start")
                        .session(mockSession)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("questionCount", "10")
                        .param("durationMinutes", "5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/timed"))
                .andExpect(flash().attributeExists("errorMessage"));

        verify(timedPracticeService, never()).startSession(any(), any());
    }

    @Test
    @DisplayName("POST /practice/timed/start: Active session guard - Từ chối khởi tạo nếu bài cũ đang chạy mà chưa xác nhận hủy")
    void testStart_activeSessionGuard_withoutConfirmCancelOld() throws Exception {
        TimedSession runningSession = TimedSession.builder()
                .id("existing-running-id")
                .type(TimedPracticeType.JA_VI)
                .questionCount(10)
                .durationMinutes(5)
                .status(TimedSessionStatus.RUNNING)
                .build();
        when(timedPracticeService.getActiveSession(any())).thenReturn(runningSession);

        mockMvc.perform(post("/practice/timed/start")
                        .session(mockSession)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("type", "JA_VI")
                        .param("questionCount", "10")
                        .param("durationMinutes", "5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/timed"))
                .andExpect(flash().attribute("errorMessage", containsString("Bạn cần xác nhận hủy bài thi cũ trước khi bắt đầu bài mới.")));

        verify(timedPracticeService, never()).startSession(any(), any());
        verify(timedPracticeService, never()).cancelSession(any());
    }

    @Test
    @DisplayName("POST /practice/timed/start: Active session guard - Hủy bài cũ và bắt đầu bài mới khi confirmCancelOld=true")
    void testStart_activeSessionGuard_withConfirmCancelOld() throws Exception {
        TimedSession runningSession = TimedSession.builder()
                .id("existing-running-id")
                .type(TimedPracticeType.JA_VI)
                .questionCount(10)
                .durationMinutes(5)
                .status(TimedSessionStatus.RUNNING)
                .build();
        when(timedPracticeService.getActiveSession(any())).thenReturn(runningSession);

        TimedSession newSession = TimedSession.builder()
                .id("new-running-id")
                .type(TimedPracticeType.VI_JA)
                .questionCount(20)
                .durationMinutes(10)
                .status(TimedSessionStatus.RUNNING)
                .build();
        when(timedPracticeService.startSession(any(TimedStartForm.class), any())).thenReturn(newSession);

        mockMvc.perform(post("/practice/timed/start")
                        .session(mockSession)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("type", "VI_JA")
                        .param("questionCount", "20")
                        .param("durationMinutes", "10")
                        .param("confirmCancelOld", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/timed/run"));

        verify(timedPracticeService).cancelSession(any());
        verify(timedPracticeService).startSession(any(TimedStartForm.class), any());
    }

    @Test
    @DisplayName("POST /practice/timed/start: Xử lý ngoại lệ IllegalArgumentException từ Service (ví dụ không đủ từ vựng)")
    void testStart_serviceException_redirectsWithError() throws Exception {
        when(timedPracticeService.getActiveSession(any())).thenReturn(null);
        when(timedPracticeService.startSession(any(), any()))
                .thenThrow(new IllegalArgumentException("Không đủ dữ liệu câu hỏi trong chủ đề đã chọn."));

        mockMvc.perform(post("/practice/timed/start")
                        .session(mockSession)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("type", "JA_VI")
                        .param("questionCount", "30")
                        .param("durationMinutes", "15"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/timed"))
                .andExpect(flash().attribute("errorMessage", containsString("Không đủ dữ liệu câu hỏi")));
    }

    // ==========================================
    // 3. GET /practice/timed/run (Question Page)
    // ==========================================

    @Test
    @DisplayName("GET /practice/timed/run: Render màn hình câu hỏi đang làm khi còn hiệu lực")
    void testRun_activeQuestionExists() throws Exception {
        TimedQuestionDto questionDto = TimedQuestionDto.builder()
                .sessionId("test-session-uuid")
                .questionIndex(1)
                .totalQuestions(10)
                .remainingSeconds(280L)
                .type(TimedPracticeType.JA_VI)
                .prompt("学生")
                .evaluationMode(TimedEvaluationMode.INSTANT)
                .build();
        when(timedPracticeService.getCurrentQuestion(any())).thenReturn(questionDto);

        mockMvc.perform(get("/practice/timed/run").session(mockSession))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/timed-run"))
                .andExpect(model().attributeExists("question", "remainingSeconds", "activeNav", "answerForm"))
                .andExpect(content().string(containsString("学生")));
    }

    @Test
    @DisplayName("GET /practice/timed/run: Khi bài thi đã hết giờ hoặc hoàn tất, chuyển hướng sang trang kết quả")
    void testRun_sessionFinishedOrExpired_redirectsToResult() throws Exception {
        when(timedPracticeService.getCurrentQuestion(any())).thenReturn(null);

        mockMvc.perform(get("/practice/timed/run").session(mockSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/timed/result"));
    }

    // ==========================================
    // 4. POST /practice/timed/answer (Submit Answer)
    // ==========================================

    @Test
    @DisplayName("POST /practice/timed/answer: Trả lời thành công và còn câu tiếp theo -> chuyển hướng về /run")
    void testAnswer_continuesToNextQuestion() throws Exception {
        List<TimedQuestionItem> items = new ArrayList<>();
        items.add(TimedQuestionItem.builder().index(0).build());
        items.add(TimedQuestionItem.builder().index(1).build());

        TimedSession updatedSession = TimedSession.builder()
                .id("test-session-uuid")
                .type(TimedPracticeType.JA_VI)
                .status(TimedSessionStatus.RUNNING)
                .currentIndex(1)
                .questions(items)
                .answers(new ArrayList<>())
                .build();
        when(timedPracticeService.answerQuestion(any(TimedAnswerForm.class), any())).thenReturn(updatedSession);

        mockMvc.perform(post("/practice/timed/answer")
                        .session(mockSession)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("sessionId", "test-session-uuid")
                        .param("questionIndex", "1")
                        .param("userMeaning", "Học sinh"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/timed/run"));
    }

    @Test
    @DisplayName("POST /practice/timed/answer: Trả lời câu cuối cùng -> hoàn thành bài thi -> chuyển hướng sang /result")
    void testAnswer_finishesSession() throws Exception {
        List<TimedQuestionItem> items = new ArrayList<>();
        items.add(TimedQuestionItem.builder().index(0).build());

        TimedSession updatedSession = TimedSession.builder()
                .id("test-session-uuid")
                .type(TimedPracticeType.JA_VI)
                .status(TimedSessionStatus.FINISHED)
                .currentIndex(1)
                .questions(items)
                .answers(new ArrayList<>())
                .build();
        when(timedPracticeService.answerQuestion(any(TimedAnswerForm.class), any())).thenReturn(updatedSession);

        mockMvc.perform(post("/practice/timed/answer")
                        .session(mockSession)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("sessionId", "test-session-uuid")
                        .param("questionIndex", "1")
                        .param("userMeaning", "Học sinh"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/timed/result"));
    }

    @Test
    @DisplayName("POST /practice/timed/answer: Lỗi validation (thiếu sessionId) chuyển hướng về /run với flash error")
    void testAnswer_validationError_missingSessionId() throws Exception {
        mockMvc.perform(post("/practice/timed/answer")
                        .session(mockSession)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("questionIndex", "1")
                        .param("userMeaning", "Học sinh"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/timed/run"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @DisplayName("POST /practice/timed/answer: Ngoại lệ từ Service (sai sessionId) được bắt và hiển thị flash error")
    void testAnswer_serviceException() throws Exception {
        when(timedPracticeService.answerQuestion(any(), any()))
                .thenThrow(new IllegalArgumentException("Mã phiên làm bài không khớp hoặc không hợp lệ."));

        mockMvc.perform(post("/practice/timed/answer")
                        .session(mockSession)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("sessionId", "wrong-session-uuid")
                        .param("questionIndex", "1")
                        .param("userMeaning", "Học sinh"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/timed/run"))
                .andExpect(flash().attribute("errorMessage", containsString("Mã phiên làm bài không khớp")));
    }

    // ==========================================
    // 5. POST /practice/timed/finish (Finish Early / Timeout)
    // ==========================================

    @Test
    @DisplayName("POST /practice/timed/finish: Nộp bài sớm hoặc timeout chốt điểm và chuyển hướng sang /result")
    void testFinish_runningSessionFinalized() throws Exception {
        TimedSession runningSession = TimedSession.builder()
                .id("running-session-uuid")
                .type(TimedPracticeType.JA_VI)
                .questionCount(10)
                .durationMinutes(5)
                .status(TimedSessionStatus.RUNNING)
                .startedAt(Instant.now().minusSeconds(60))
                .deadline(Instant.now().plusSeconds(240))
                .build();
        mockSession.setAttribute(TimedPracticeService.SESSION_TIMED_PRACTICE, runningSession);

        mockMvc.perform(post("/practice/timed/finish")
                        .session(mockSession)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("sessionId", "running-session-uuid"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/timed/result"));

        verify(timedPracticeService).finalizeSession(eq(runningSession), any(), eq(TimedSessionStatus.FINISHED));
    }

    // ==========================================
    // 6. GET /practice/timed/result (Result Summary)
    // ==========================================

    @Test
    @DisplayName("GET /practice/timed/result: Hiển thị bảng tổng kết kết quả thi")
    void testResult_success() throws Exception {
        TimedResultDto resultDto = TimedResultDto.builder()
                .sessionId("test-result-session")
                .type(TimedPracticeType.JA_VI)
                .typeName("Nhật - Việt")
                .totalQuestions(10)
                .answeredCount(10)
                .correctCount(8)
                .incorrectCount(2)
                .accuracyPercentage(80)
                .timeSpentSeconds(150L)
                .timeSpentFormatted("02:30")
                .maxStreak(5)
                .status(TimedSessionStatus.FINISHED)
                .records(List.of(
                        TimedAnswerRecord.builder()
                                .questionIndex(1)
                                .prompt("学生")
                                .userAnswer("Học sinh")
                                .expectedAnswer("Nghĩa: Học sinh")
                                .correct(true)
                                .feedbackMessage("Chính xác!")
                                .build()
                ))
                .build();
        when(timedPracticeService.getResult(any())).thenReturn(resultDto);

        mockMvc.perform(get("/practice/timed/result").session(mockSession))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/timed-result"))
                .andExpect(model().attributeExists("result", "activeNav"))
                .andExpect(content().string(containsString("Kết quả bài thi tính giờ")))
                .andExpect(content().string(containsString("80%")));
    }

    @Test
    @DisplayName("GET /practice/timed/result: Chưa có kết quả chuyển hướng về /practice/timed")
    void testResult_noResult_redirectsToIndex() throws Exception {
        when(timedPracticeService.getResult(any())).thenReturn(null);

        mockMvc.perform(get("/practice/timed/result").session(mockSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/timed"));
    }

    // ==========================================
    // 7. POST /practice/timed/cancel (Cancel Practice)
    // ==========================================

    @Test
    @DisplayName("POST /practice/timed/cancel: Hủy bài thi thành công và chuyển hướng về /practice/timed")
    void testCancel() throws Exception {
        mockMvc.perform(post("/practice/timed/cancel")
                        .session(mockSession)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/timed"))
                .andExpect(flash().attribute("successMessage", containsString("Đã hủy bài thi tính giờ.")));

        verify(timedPracticeService).cancelSession(any());
    }
}
