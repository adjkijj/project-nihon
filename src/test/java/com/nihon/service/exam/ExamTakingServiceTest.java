package com.nihon.service.exam;

import com.nihon.dto.exam.taker.ExamAnswerResponse;
import com.nihon.dto.exam.taker.ExamAnswerSubmission;
import com.nihon.dto.exam.taker.ExamAttemptStatus;
import com.nihon.dto.exam.taker.ExamSessionAttempt;
import com.nihon.entity.ExamCategory;
import com.nihon.entity.ExamChoice;
import com.nihon.entity.ExamPaper;
import com.nihon.entity.ExamQuestion;
import com.nihon.entity.ExamQuestionType;
import com.nihon.entity.ExamSection;
import com.nihon.repository.ExamPaperRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamTakingServiceTest {

    @Mock
    private ExamPaperRepository paperRepository;

    @Mock
    private com.nihon.service.exam.grading.ExamGradingService gradingService;

    @InjectMocks
    private ExamTakingService takingService;

    private ExamPaper testPaper;
    private MockHttpSession sessionA;
    private MockHttpSession sessionB;

    @BeforeEach
    void setUp() {
        sessionA = new MockHttpSession();
        sessionB = new MockHttpSession();

        testPaper = ExamPaper.builder()
                .id(1L)
                .code("TEST-N5-01")
                .title("Đề thi thử N5")
                .durationMinutes(30)
                .category(ExamCategory.MOCK)
                .published(true)
                .shuffleChoices(true)
                .sections(new ArrayList<>())
                .build();

        ExamSection sec1 = ExamSection.builder()
                .id(10L)
                .paper(testPaper)
                .title("Phần 1: Kiến thức")
                .sortOrder(1)
                .questions(new ArrayList<>())
                .build();

        ExamQuestion q1 = ExamQuestion.builder()
                .id(101L)
                .section(sec1)
                .sortOrder(1)
                .type(ExamQuestionType.MULTIPLE_CHOICE)
                .promptJa("あした")
                .points(BigDecimal.valueOf(1.0))
                .choices(new ArrayList<>())
                .build();

        q1.addChoice(ExamChoice.builder().id(1001L).sortOrder(1).text("A").correct(true).build());
        q1.addChoice(ExamChoice.builder().id(1002L).sortOrder(2).text("B").correct(false).build());
        q1.addChoice(ExamChoice.builder().id(1003L).sortOrder(3).text("C").correct(false).build());
        q1.addChoice(ExamChoice.builder().id(1004L).sortOrder(4).text("D").correct(false).build());

        sec1.addQuestion(q1);
        testPaper.addSection(sec1);
    }

    @Test
    @DisplayName("Bắt đầu bài thi thành công: tạo ExamAttempt, xáo trộn trắc nghiệm, ẩn hoàn toàn đáp án")
    void testStartExamSuccess() {
        when(paperRepository.findByCodeAndPublishedTrue("TEST-N5-01")).thenReturn(Optional.of(testPaper));

        ExamSessionAttempt attempt = takingService.startExam("TEST-N5-01", sessionA);

        assertThat(attempt).isNotNull();
        assertThat(attempt.getAttemptId()).isNotBlank();
        assertThat(attempt.getPaperCode()).isEqualTo("TEST-N5-01");
        assertThat(attempt.getStatus()).isEqualTo(ExamAttemptStatus.RUNNING);
        assertThat(attempt.getDeadline()).isAfter(Instant.now());
        assertThat(attempt.getPaperDto()).isNotNull();
        assertThat(attempt.getPaperDto().getTotalQuestions()).isEqualTo(1);

        // Kiểm tra DTO của thí sinh KHÔNG chứa cờ correct
        assertThat(attempt.getPaperDto().getSections().get(0).getQuestions().get(0).getChoices())
                .hasSize(4);

        // Lưu vào sessionA
        ExamSessionAttempt retrieved = takingService.getAttempt(attempt.getAttemptId(), sessionA);
        assertThat(retrieved).isSameAs(attempt);
    }

    @Test
    @DisplayName("Đề chưa xuất bản không thể bắt đầu làm bài")
    void testStartExamUnpublishedFails() {
        when(paperRepository.findByCodeAndPublishedTrue("TEST-UNPUB")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> takingService.startExam("TEST-UNPUB", sessionA))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("Lưu câu trả lời hợp lệ và cập nhật số câu đã làm")
    void testSaveAnswerValid() {
        when(paperRepository.findByCodeAndPublishedTrue("TEST-N5-01")).thenReturn(Optional.of(testPaper));
        ExamSessionAttempt attempt = takingService.startExam("TEST-N5-01", sessionA);

        ExamAnswerSubmission sub = ExamAnswerSubmission.builder()
                .questionId(101L)
                .answer("1002")
                .markedForReview(true)
                .build();

        ExamAnswerResponse resp = takingService.saveAnswer(attempt.getAttemptId(), sub, sessionA);

        assertThat(resp.getStatus()).isEqualTo("OK");
        assertThat(resp.getQuestionId()).isEqualTo(101L);
        assertThat(resp.getAnsweredCount()).isEqualTo(1);

        assertThat(attempt.getAnswers()).containsEntry(101L, "1002");
        assertThat(attempt.getMarkedForReview()).contains(101L);
    }

    @Test
    @DisplayName("Câu trả lời quá 1000 ký tự bị từ chối")
    void testSaveAnswerOverlongRejected() {
        when(paperRepository.findByCodeAndPublishedTrue("TEST-N5-01")).thenReturn(Optional.of(testPaper));
        ExamSessionAttempt attempt = takingService.startExam("TEST-N5-01", sessionA);

        String overlong = "A".repeat(1001);
        ExamAnswerSubmission sub = ExamAnswerSubmission.builder()
                .questionId(101L)
                .answer(overlong)
                .build();

        assertThatThrownBy(() -> takingService.saveAnswer(attempt.getAttemptId(), sub, sessionA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1000");
    }

    @Test
    @DisplayName("Trả lời sau khi hết hạn bị từ chối và bài thi chuyển sang EXPIRED")
    void testSaveAnswerAfterDeadlineRejected() {
        when(paperRepository.findByCodeAndPublishedTrue("TEST-N5-01")).thenReturn(Optional.of(testPaper));
        ExamSessionAttempt attempt = takingService.startExam("TEST-N5-01", sessionA);

        // Giả lập deadline đã qua 10 giây trước
        attempt.setDeadline(Instant.now().minusSeconds(10));

        ExamAnswerSubmission sub = ExamAnswerSubmission.builder()
                .questionId(101L)
                .answer("1001")
                .build();

        ExamAnswerResponse resp = takingService.saveAnswer(attempt.getAttemptId(), sub, sessionA);

        assertThat(resp.getStatus()).isEqualTo("EXPIRED");
        assertThat(attempt.getStatus()).isEqualTo(ExamAttemptStatus.EXPIRED);
    }

    @Test
    @DisplayName("Cách ly phiên: session khác không thể đọc hoặc sửa bài làm")
    void testSessionIsolation() {
        when(paperRepository.findByCodeAndPublishedTrue("TEST-N5-01")).thenReturn(Optional.of(testPaper));
        ExamSessionAttempt attemptA = takingService.startExam("TEST-N5-01", sessionA);

        // sessionB cố truy cập attempt của sessionA
        ExamSessionAttempt attemptB = takingService.getAttempt(attemptA.getAttemptId(), sessionB);
        assertThat(attemptB).isNull();

        ExamAnswerSubmission sub = ExamAnswerSubmission.builder()
                .questionId(101L)
                .answer("1001")
                .build();

        assertThatThrownBy(() -> takingService.saveAnswer(attemptA.getAttemptId(), sub, sessionB))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Ngăn nộp bài 2 lần: nộp lần 2 trả về kết quả đã hoàn thành không ghi đè")
    void testFinishExamPreventDoubleSubmission() {
        when(paperRepository.findByCodeAndPublishedTrue("TEST-N5-01")).thenReturn(Optional.of(testPaper));
        ExamSessionAttempt attempt = takingService.startExam("TEST-N5-01", sessionA);

        ExamSessionAttempt finished1 = takingService.finishExam(attempt.getAttemptId(), sessionA);
        assertThat(finished1.getStatus()).isEqualTo(ExamAttemptStatus.FINISHED);
        Instant firstSubmittedAt = finished1.getSubmittedAt();
        assertThat(firstSubmittedAt).isNotNull();

        // Nộp lần 2
        ExamSessionAttempt finished2 = takingService.finishExam(attempt.getAttemptId(), sessionA);
        assertThat(finished2.getStatus()).isEqualTo(ExamAttemptStatus.FINISHED);
        assertThat(finished2.getSubmittedAt()).isEqualTo(firstSubmittedAt);
    }
}
