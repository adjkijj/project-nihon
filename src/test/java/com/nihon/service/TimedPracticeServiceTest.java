package com.nihon.service;

import com.nihon.dto.KanjiPracticeMode;
import com.nihon.dto.PracticeAnswerForm;
import com.nihon.dto.PracticeChoiceAnswerForm;
import com.nihon.dto.PracticeChoiceOptionDto;
import com.nihon.dto.PracticeChoiceResultDto;
import com.nihon.dto.PracticeKanjiAnswerForm;
import com.nihon.dto.PracticeKanjiResultDto;
import com.nihon.dto.PracticeResultDto;
import com.nihon.dto.PracticeViJaAnswerForm;
import com.nihon.dto.PracticeViJaResultDto;
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
import com.nihon.entity.KanjiEntry;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.repository.KanjiEntryRepository;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VocabularyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimedPracticeServiceTest {

    @Mock
    private VocabularyRepository vocabularyRepository;

    @Mock
    private KanjiEntryRepository kanjiEntryRepository;

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private PracticeService practiceService;

    @Mock
    private KanjiPracticeService kanjiPracticeService;

    @Mock
    private StudyProgressService studyProgressService;

    @InjectMocks
    private TimedPracticeService timedPracticeService;

    private MockHttpSession httpSession;
    private Topic sampleTopic;
    private List<Vocabulary> sampleVocabs;
    private List<KanjiEntry> sampleKanjis;

    @BeforeEach
    void setUp() {
        httpSession = new MockHttpSession();

        sampleTopic = new Topic();
        sampleTopic.setId(1L);
        sampleTopic.setName("Chủ đề 1: Chào hỏi");

        sampleVocabs = new ArrayList<>();
        for (long i = 1; i <= 35; i++) {
            Vocabulary v = new Vocabulary();
            v.setId(i);
            v.setJapaneseText("単語" + i);
            v.setReading("たんご" + i);
            v.setMeaningVi("Từ vựng " + i);
            v.setHasKanji(true);
            v.setTopic(sampleTopic);
            sampleVocabs.add(v);
        }

        sampleKanjis = new ArrayList<>();
        for (long i = 1; i <= 35; i++) {
            KanjiEntry k = new KanjiEntry();
            k.setId(i);
            k.setCharacter(String.valueOf((char) ('一' + i)));
            k.setOnyomi("イチ");
            k.setKunyomi("ひと");
            k.setMeaningVi("Nghĩa Hán " + i);
            k.setTopic(sampleTopic);
            sampleKanjis.add(k);
        }
    }

    // ==========================================
    // 1. WHITELIST ENFORCEMENT & PRESET
    // ==========================================

    @Test
    @DisplayName("Whitelist: Khởi tạo thành công với các số câu hỏi (10, 20, 30) và thời lượng (2, 3, 5, 10, 15 phút) hợp lệ")
    void testStartSession_whitelistEnforcement_validOptions() {
        when(vocabularyRepository.findAll()).thenReturn(sampleVocabs);

        for (int count : List.of(10, 20, 30)) {
            for (int duration : List.of(2, 3, 5, 10, 15)) {
                MockHttpSession session = new MockHttpSession();
                TimedStartForm form = TimedStartForm.builder()
                        .type(TimedPracticeType.JA_VI)
                        .questionCount(count)
                        .durationMinutes(duration)
                        .evaluationMode(TimedEvaluationMode.INSTANT)
                        .build();

                TimedSession started = timedPracticeService.startSession(form, session);
                assertThat(started).isNotNull();
                assertThat(started.getQuestionCount()).isEqualTo(count);
                assertThat(started.getDurationMinutes()).isEqualTo(duration);
                assertThat(started.getStatus()).isEqualTo(TimedSessionStatus.RUNNING);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 15, 25, 40, 50, 0, -10})
    @DisplayName("Whitelist: Từ chối số câu hỏi không nằm trong danh sách trắng (10, 20, 30)")
    void testStartSession_whitelistEnforcement_invalidQuestionCounts(int invalidCount) {
        TimedStartForm form = TimedStartForm.builder()
                .type(TimedPracticeType.JA_VI)
                .questionCount(invalidCount)
                .durationMinutes(5)
                .build();

        assertThatThrownBy(() -> timedPracticeService.startSession(form, httpSession))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Số lượng câu hỏi không hợp lệ");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4, 6, 7, 8, 12, 20, 0, -5})
    @DisplayName("Whitelist: Từ chối thời lượng không nằm trong danh sách trắng (2, 3, 5, 10, 15)")
    void testStartSession_whitelistEnforcement_invalidDurations(int invalidDuration) {
        TimedStartForm form = TimedStartForm.builder()
                .type(TimedPracticeType.JA_VI)
                .questionCount(10)
                .durationMinutes(invalidDuration)
                .build();

        assertThatThrownBy(() -> timedPracticeService.startSession(form, httpSession))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Thời lượng không hợp lệ");
    }

    @Test
    @DisplayName("Gói tốc độ (F17: Đấu trường tốc độ): Tự động ép cấu hình 20 câu / 3 phút bất kể input từ client")
    void testStartSession_speedPresetEnforcement() {
        when(vocabularyRepository.findAll()).thenReturn(sampleVocabs);

        // Client cố tình truyền 10 câu và 15 phút, nhưng bật isSpeedPreset = true
        TimedStartForm form = TimedStartForm.builder()
                .type(TimedPracticeType.JA_VI)
                .questionCount(10)
                .durationMinutes(15)
                .isSpeedPreset(true)
                .build();

        TimedSession started = timedPracticeService.startSession(form, httpSession);
        assertThat(started.isSpeedPreset()).isTrue();
        assertThat(started.getQuestionCount()).isEqualTo(20);
        assertThat(started.getDurationMinutes()).isEqualTo(3);
    }

    // ==========================================
    // 2. ACTIVE SESSION GUARD
    // ==========================================

    @Test
    @DisplayName("Active session guard: Khởi tạo bài mới khi bài cũ đang chạy mà không xác nhận hủy sẽ bị từ chối")
    void testStartSession_activeSessionGuard_promptWhenRunning() {
        when(vocabularyRepository.findAll()).thenReturn(sampleVocabs);

        TimedStartForm firstForm = TimedStartForm.builder()
                .type(TimedPracticeType.JA_VI)
                .questionCount(10)
                .durationMinutes(5)
                .build();
        timedPracticeService.startSession(firstForm, httpSession);

        // Khởi tạo bài thứ hai mà không có confirmCancelOld
        TimedStartForm secondForm = TimedStartForm.builder()
                .type(TimedPracticeType.VI_JA)
                .questionCount(10)
                .durationMinutes(5)
                .confirmCancelOld(false)
                .build();

        assertThatThrownBy(() -> timedPracticeService.startSession(secondForm, httpSession))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Đang có bài thi tính giờ đang diễn ra. Bạn cần xác nhận hủy");
    }

    @Test
    @DisplayName("Active session guard: Khởi tạo bài mới khi bài cũ đang chạy có confirmCancelOld=true sẽ hủy bài cũ và bắt đầu bài mới")
    void testStartSession_activeSessionGuard_replaceWhenConfirmed() {
        when(vocabularyRepository.findAll()).thenReturn(sampleVocabs);

        TimedStartForm firstForm = TimedStartForm.builder()
                .type(TimedPracticeType.JA_VI)
                .questionCount(10)
                .durationMinutes(5)
                .build();
        TimedSession firstSession = timedPracticeService.startSession(firstForm, httpSession);
        String firstId = firstSession.getId();

        // Khởi tạo bài mới với confirmCancelOld = true
        TimedStartForm secondForm = TimedStartForm.builder()
                .type(TimedPracticeType.VI_JA)
                .questionCount(20)
                .durationMinutes(10)
                .confirmCancelOld(true)
                .build();

        TimedSession secondSession = timedPracticeService.startSession(secondForm, httpSession);
        assertThat(secondSession).isNotNull();
        assertThat(secondSession.getId()).isNotEqualTo(firstId);
        assertThat(secondSession.getType()).isEqualTo(TimedPracticeType.VI_JA);
        assertThat(secondSession.getQuestionCount()).isEqualTo(20);
        assertThat(secondSession.getDurationMinutes()).isEqualTo(10);
    }

    // ==========================================
    // 3. SERVER CONTROLS DEADLINE & REMAINING TIME
    // ==========================================

    @Test
    @DisplayName("Server deadline: Deadline được thiết lập cố định từ server, client không thể can thiệp")
    void testServerStrictlyControlsDeadline() {
        when(vocabularyRepository.findAll()).thenReturn(sampleVocabs);

        Instant before = Instant.now();
        TimedStartForm form = TimedStartForm.builder()
                .type(TimedPracticeType.JA_VI)
                .questionCount(10)
                .durationMinutes(5)
                .build();

        TimedSession started = timedPracticeService.startSession(form, httpSession);
        Instant after = Instant.now();

        assertThat(started.getStartedAt()).isBetween(before, after);
        Duration expectedDuration = Duration.ofMinutes(5);
        assertThat(Duration.between(started.getStartedAt(), started.getDeadline())).isEqualTo(expectedDuration);

        // Số giây còn lại được tính từ server
        long remaining = started.getRemainingSeconds(Instant.now());
        assertThat(remaining).isBetween(295L, 300L);
    }

    // ==========================================
    // 4. TIMEOUT, 3S GRACE PERIOD & EXPIRED STATUS
    // ==========================================

    @Test
    @DisplayName("Deadline enforcement: Nộp bài sau khi hết hạn (+ 3s ân hạn) bị từ chối và bài thi chuyển sang EXPIRED")
    void testAnswerQuestion_afterDeadlinePlus3sGrace_isRejectedAndMarkedExpired() {
        when(vocabularyRepository.findAll()).thenReturn(sampleVocabs);

        TimedStartForm form = TimedStartForm.builder()
                .type(TimedPracticeType.JA_VI)
                .questionCount(10)
                .durationMinutes(2)
                .build();

        TimedSession sessionData = timedPracticeService.startSession(form, httpSession);

        // Giả lập đồng hồ đã trôi qua deadline + 4 giây (> 3s grace)
        sessionData.setDeadline(Instant.now().minusSeconds(4));
        assertThat(sessionData.isExpired(Instant.now())).isTrue();

        TimedAnswerForm answerForm = TimedAnswerForm.builder()
                .sessionId(sessionData.getId())
                .questionIndex(1)
                .userMeaning("Nghĩa test")
                .build();

        TimedSession result = timedPracticeService.answerQuestion(answerForm, httpSession);

        assertThat(result.getStatus()).isEqualTo(TimedSessionStatus.EXPIRED);
        // Kiểm tra tất cả câu hỏi được chốt kết quả và câu chưa làm có nhãn "(Chưa trả lời)"
        assertThat(result.getAnswers().size()).isEqualTo(10);
        for (TimedAnswerRecord r : result.getAnswers()) {
            assertThat(r.getUserAnswer()).isEqualTo("(Chưa trả lời)");
            assertThat(r.isCorrect()).isFalse();
        }
        verify(practiceService, never()).checkAnswer(any());
        verify(studyProgressService, times(10)).recordTimedAttempt(any(), any(), any(), any(), any(), any(), any(), eq("(Chưa trả lời)"), any(), eq(false), any());
    }

    @Test
    @DisplayName("Grace period: Nộp bài trong khoảng 3s ân hạn vẫn được chấp nhận và chấm điểm")
    void testAnswerQuestion_withinGracePeriod_isAccepted() {
        when(vocabularyRepository.findAll()).thenReturn(sampleVocabs);

        PracticeResultDto mockResult = PracticeResultDto.builder()
                .overallCorrect(true)
                .expectedMeaning("Từ vựng 1")
                .feedbackMessage("Chính xác!")
                .build();
        when(practiceService.checkAnswer(any(PracticeAnswerForm.class))).thenReturn(mockResult);

        TimedStartForm form = TimedStartForm.builder()
                .type(TimedPracticeType.JA_VI)
                .questionCount(10)
                .durationMinutes(2)
                .build();

        TimedSession sessionData = timedPracticeService.startSession(form, httpSession);

        // Đặt deadline đã quá 1 giây (nhỏ hơn 3s grace)
        sessionData.setDeadline(Instant.now().minusSeconds(1));
        assertThat(sessionData.isExpired(Instant.now())).isFalse();

        TimedAnswerForm answerForm = TimedAnswerForm.builder()
                .sessionId(sessionData.getId())
                .questionIndex(1)
                .userMeaning("Từ vựng 1")
                .build();

        TimedSession updated = timedPracticeService.answerQuestion(answerForm, httpSession);

        assertThat(updated.getStatus()).isEqualTo(TimedSessionStatus.RUNNING);
        assertThat(updated.getCurrentIndex()).isEqualTo(1);
        assertThat(updated.getAnswers()).hasSize(1);
        assertThat(updated.getAnswers().get(0).isCorrect()).isTrue();
    }

    // ==========================================
    // 5. UNATTEMPTED QUESTIONS GRADED AS INCORRECT ON EXPIRATION
    // ==========================================

    @Test
    @DisplayName("Hết giờ: Những câu đã làm được giữ điểm, những câu chưa làm bị chấm sai với nhãn '(Chưa trả lời)'")
    void testWhenTimeExpires_allAnsweredQuestionsGraded_unattemptedGradedAsIncorrect() {
        when(vocabularyRepository.findAll()).thenReturn(sampleVocabs);

        PracticeResultDto mockResult = PracticeResultDto.builder()
                .overallCorrect(true)
                .expectedMeaning("Từ vựng 1")
                .feedbackMessage("Chính xác!")
                .build();
        when(practiceService.checkAnswer(any(PracticeAnswerForm.class))).thenReturn(mockResult);

        TimedStartForm form = TimedStartForm.builder()
                .type(TimedPracticeType.JA_VI)
                .questionCount(10)
                .durationMinutes(2)
                .build();

        TimedSession sessionData = timedPracticeService.startSession(form, httpSession);

        // Trả lời 2 câu đầu tiên thành công
        TimedAnswerForm a1 = TimedAnswerForm.builder()
                .sessionId(sessionData.getId())
                .questionIndex(1)
                .userMeaning("Từ vựng 1")
                .build();
        timedPracticeService.answerQuestion(a1, httpSession);

        TimedAnswerForm a2 = TimedAnswerForm.builder()
                .sessionId(sessionData.getId())
                .questionIndex(2)
                .userMeaning("Từ vựng 2")
                .build();
        timedPracticeService.answerQuestion(a2, httpSession);

        // Hết giờ xảy ra
        sessionData.setDeadline(Instant.now().minusSeconds(5));
        timedPracticeService.finalizeSession(sessionData, httpSession, TimedSessionStatus.EXPIRED);

        assertThat(sessionData.getStatus()).isEqualTo(TimedSessionStatus.EXPIRED);
        assertThat(sessionData.getAnswers()).hasSize(10);

        // 2 câu đầu tiên đúng
        assertThat(sessionData.getAnswers().get(0).isCorrect()).isTrue();
        assertThat(sessionData.getAnswers().get(1).isCorrect()).isTrue();

        // 8 câu sau bị chấm sai
        for (int i = 2; i < 10; i++) {
            TimedAnswerRecord unans = sessionData.getAnswers().get(i);
            assertThat(unans.getUserAnswer()).isEqualTo("(Chưa trả lời)");
            assertThat(unans.isCorrect()).isFalse();
        }

        TimedResultDto resultDto = timedPracticeService.getResult(httpSession);
        assertThat(resultDto.getTotalQuestions()).isEqualTo(10);
        assertThat(resultDto.getAnsweredCount()).isEqualTo(2);
        assertThat(resultDto.getCorrectCount()).isEqualTo(2);
        assertThat(resultDto.getIncorrectCount()).isEqualTo(8);
        assertThat(resultDto.getAccuracyPercentage()).isEqualTo(20);
        assertThat(resultDto.getStatus()).isEqualTo(TimedSessionStatus.EXPIRED);
    }

    // ==========================================
    // 6. EVALUATION MODES: INSTANT VS AT_END
    // ==========================================

    @Test
    @DisplayName("Evaluation mode INSTANT: Trả về lastResult của câu vừa làm ở câu hỏi tiếp theo")
    void testEvaluationMode_instant_providesLastResult() {
        when(vocabularyRepository.findAll()).thenReturn(sampleVocabs);

        PracticeResultDto mockResult = PracticeResultDto.builder()
                .overallCorrect(true)
                .expectedMeaning("Từ vựng 1")
                .feedbackMessage("Chính xác!")
                .build();
        when(practiceService.checkAnswer(any())).thenReturn(mockResult);

        TimedStartForm form = TimedStartForm.builder()
                .type(TimedPracticeType.JA_VI)
                .questionCount(10)
                .durationMinutes(5)
                .evaluationMode(TimedEvaluationMode.INSTANT)
                .build();

        TimedSession sessionData = timedPracticeService.startSession(form, httpSession);

        // Câu hỏi đầu tiên chưa có lastResult
        TimedQuestionDto q1 = timedPracticeService.getCurrentQuestion(httpSession);
        assertThat(q1.getLastResult()).isNull();

        // Trả lời câu 1
        TimedAnswerForm a1 = TimedAnswerForm.builder()
                .sessionId(sessionData.getId())
                .questionIndex(1)
                .userMeaning("Từ vựng 1")
                .build();
        timedPracticeService.answerQuestion(a1, httpSession);

        // Câu hỏi 2 (currentIndex = 1) PHẢI có lastResult
        TimedQuestionDto q2 = timedPracticeService.getCurrentQuestion(httpSession);
        assertThat(q2.getLastResult()).isNotNull();
        assertThat(q2.getLastResult().isCorrect()).isTrue();
        assertThat(q2.getLastResult().getQuestionIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("Evaluation mode AT_END: Không trả về lastResult trong quá trình làm bài")
    void testEvaluationMode_atEnd_doesNotProvideLastResult() {
        when(vocabularyRepository.findAll()).thenReturn(sampleVocabs);

        PracticeResultDto mockResult = PracticeResultDto.builder()
                .overallCorrect(true)
                .expectedMeaning("Từ vựng 1")
                .feedbackMessage("Chính xác!")
                .build();
        when(practiceService.checkAnswer(any())).thenReturn(mockResult);

        TimedStartForm form = TimedStartForm.builder()
                .type(TimedPracticeType.JA_VI)
                .questionCount(10)
                .durationMinutes(5)
                .evaluationMode(TimedEvaluationMode.AT_END)
                .build();

        TimedSession sessionData = timedPracticeService.startSession(form, httpSession);

        // Trả lời câu 1
        TimedAnswerForm a1 = TimedAnswerForm.builder()
                .sessionId(sessionData.getId())
                .questionIndex(1)
                .userMeaning("Từ vựng 1")
                .build();
        timedPracticeService.answerQuestion(a1, httpSession);

        // Câu hỏi 2 trong chế độ AT_END: lastResult PHẢI là null
        TimedQuestionDto q2 = timedPracticeService.getCurrentQuestion(httpSession);
        assertThat(q2.getLastResult()).isNull();
    }

    // ==========================================
    // 7. SESSION ID & INTEGRITY VALIDATION
    // ==========================================

    @Test
    @DisplayName("Session integrity: Gửi sai sessionId bị từ chối với IllegalArgumentException")
    void testAnswerQuestion_wrongSessionId_rejected() {
        when(vocabularyRepository.findAll()).thenReturn(sampleVocabs);

        TimedStartForm form = TimedStartForm.builder()
                .type(TimedPracticeType.JA_VI)
                .questionCount(10)
                .durationMinutes(5)
                .build();
        timedPracticeService.startSession(form, httpSession);

        TimedAnswerForm answerForm = TimedAnswerForm.builder()
                .sessionId("random-wrong-uuid-1234")
                .questionIndex(1)
                .userMeaning("Trả lời sai session")
                .build();

        assertThatThrownBy(() -> timedPracticeService.answerQuestion(answerForm, httpSession))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mã phiên làm bài không khớp hoặc không hợp lệ");
    }

    @Test
    @DisplayName("Session integrity: Nộp bài khi chưa có session trong HttpSession bị từ chối với IllegalStateException")
    void testAnswerQuestion_noSession_rejected() {
        TimedAnswerForm answerForm = TimedAnswerForm.builder()
                .sessionId("session-not-present")
                .questionIndex(1)
                .userMeaning("Trả lời")
                .build();

        assertThatThrownBy(() -> timedPracticeService.answerQuestion(answerForm, httpSession))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Không tìm thấy phiên làm bài hợp lệ");
    }

    // ==========================================
    // 8. STREAK CALCULATION (F17 & GENERAL)
    // ==========================================

    @Test
    @DisplayName("Streak: Tính toán chuỗi đúng liên tiếp và cập nhật maxStreak chính xác")
    void testStreakCalculation() {
        when(vocabularyRepository.findAll()).thenReturn(sampleVocabs);

        PracticeResultDto correctRes = PracticeResultDto.builder().overallCorrect(true).feedbackMessage("Đúng").build();
        PracticeResultDto wrongRes = PracticeResultDto.builder().overallCorrect(false).feedbackMessage("Sai").build();

        TimedStartForm form = TimedStartForm.builder()
                .type(TimedPracticeType.JA_VI)
                .questionCount(10)
                .durationMinutes(5)
                .build();

        TimedSession sessionData = timedPracticeService.startSession(form, httpSession);
        String sId = sessionData.getId();

        // Câu 1: Đúng -> streak = 1, max = 1
        when(practiceService.checkAnswer(any())).thenReturn(correctRes);
        timedPracticeService.answerQuestion(TimedAnswerForm.builder().sessionId(sId).questionIndex(1).userMeaning("A").build(), httpSession);
        assertThat(sessionData.getCurrentStreak()).isEqualTo(1);
        assertThat(sessionData.getMaxStreak()).isEqualTo(1);

        // Câu 2: Đúng -> streak = 2, max = 2
        timedPracticeService.answerQuestion(TimedAnswerForm.builder().sessionId(sId).questionIndex(2).userMeaning("B").build(), httpSession);
        assertThat(sessionData.getCurrentStreak()).isEqualTo(2);
        assertThat(sessionData.getMaxStreak()).isEqualTo(2);

        // Câu 3: Sai -> streak reset về 0, max vẫn là 2
        when(practiceService.checkAnswer(any())).thenReturn(wrongRes);
        timedPracticeService.answerQuestion(TimedAnswerForm.builder().sessionId(sId).questionIndex(3).userMeaning("C").build(), httpSession);
        assertThat(sessionData.getCurrentStreak()).isEqualTo(0);
        assertThat(sessionData.getMaxStreak()).isEqualTo(2);

        // Câu 4: Đúng -> streak = 1, max vẫn là 2
        when(practiceService.checkAnswer(any())).thenReturn(correctRes);
        timedPracticeService.answerQuestion(TimedAnswerForm.builder().sessionId(sId).questionIndex(4).userMeaning("D").build(), httpSession);
        assertThat(sessionData.getCurrentStreak()).isEqualTo(1);
        assertThat(sessionData.getMaxStreak()).isEqualTo(2);
    }

    // ==========================================
    // 9. EARLY FINISH SUBMITS & GRADES
    // ==========================================

    @Test
    @DisplayName("Nộp bài sớm: Chốt điểm các câu đã làm và đánh dấu các câu còn lại là '(Chưa trả lời)'")
    void testEarlyFinish_submitsAndGradesAllAnsweredQuestions() {
        when(vocabularyRepository.findAll()).thenReturn(sampleVocabs);

        PracticeResultDto correctRes = PracticeResultDto.builder().overallCorrect(true).expectedMeaning("M").feedbackMessage("Đúng").build();
        when(practiceService.checkAnswer(any())).thenReturn(correctRes);

        TimedStartForm form = TimedStartForm.builder()
                .type(TimedPracticeType.JA_VI)
                .questionCount(10)
                .durationMinutes(5)
                .build();

        TimedSession sessionData = timedPracticeService.startSession(form, httpSession);

        // Người dùng trả lời 3 câu đầu
        for (int i = 1; i <= 3; i++) {
            timedPracticeService.answerQuestion(TimedAnswerForm.builder()
                    .sessionId(sessionData.getId())
                    .questionIndex(i)
                    .userMeaning("Ans " + i)
                    .build(), httpSession);
        }

        // Bấm nộp bài sớm
        TimedSession finalized = timedPracticeService.finalizeSession(sessionData, httpSession, TimedSessionStatus.FINISHED);

        assertThat(finalized.getStatus()).isEqualTo(TimedSessionStatus.FINISHED);
        assertThat(finalized.getAnswers()).hasSize(10);

        TimedResultDto result = timedPracticeService.getResult(httpSession);
        assertThat(result.getAnsweredCount()).isEqualTo(3);
        assertThat(result.getCorrectCount()).isEqualTo(3);
        assertThat(result.getIncorrectCount()).isEqualTo(7);
        assertThat(result.getAccuracyPercentage()).isEqualTo(30);
    }

    // ==========================================
    // 10. KANJI_READING MODE INTEGRATION
    // ==========================================

    @Test
    @DisplayName("Chế độ KANJI_READING: Tích hợp với KanjiPracticeService và chấm điểm chính xác")
    void testKanjiReadingModeIntegration() {
        when(kanjiEntryRepository.findAll()).thenReturn(sampleKanjis);
        when(kanjiPracticeService.extractValidReadings(any(KanjiEntry.class))).thenReturn(List.of("イチ", "ひと"));

        PracticeKanjiResultDto mockKanjiResult = PracticeKanjiResultDto.builder()
                .correct(true)
                .expectedAnswer("イチ, ひと")
                .userAnswer("イチ")
                .feedbackMessage("Chính xác!")
                .build();
        when(kanjiPracticeService.checkAnswer(any(PracticeKanjiAnswerForm.class), eq(httpSession))).thenReturn(mockKanjiResult);

        TimedStartForm form = TimedStartForm.builder()
                .type(TimedPracticeType.KANJI_READING)
                .questionCount(10)
                .durationMinutes(3)
                .build();

        TimedSession sessionData = timedPracticeService.startSession(form, httpSession);
        assertThat(sessionData.getType()).isEqualTo(TimedPracticeType.KANJI_READING);

        TimedAnswerForm answerForm = TimedAnswerForm.builder()
                .sessionId(sessionData.getId())
                .questionIndex(1)
                .kanjiAnswer("イチ")
                .build();

        TimedSession updated = timedPracticeService.answerQuestion(answerForm, httpSession);
        assertThat(updated.getAnswers()).hasSize(1);
        assertThat(updated.getAnswers().get(0).isCorrect()).isTrue();
        assertThat(updated.getAnswers().get(0).getUserAnswer()).isEqualTo("イチ");
    }

    // ==========================================
    // 11. CANCEL SESSION
    // ==========================================

    @Test
    @DisplayName("Hủy bài thi: Xóa hoàn toàn phiên làm việc trong HttpSession")
    void testCancelSession_clearsSession() {
        when(vocabularyRepository.findAll()).thenReturn(sampleVocabs);

        TimedStartForm form = TimedStartForm.builder()
                .type(TimedPracticeType.JA_VI)
                .questionCount(10)
                .durationMinutes(5)
                .build();

        timedPracticeService.startSession(form, httpSession);
        assertThat(httpSession.getAttribute(TimedPracticeService.SESSION_TIMED_PRACTICE)).isNotNull();

        timedPracticeService.cancelSession(httpSession);
        assertThat(httpSession.getAttribute(TimedPracticeService.SESSION_TIMED_PRACTICE)).isNull();
    }
}
