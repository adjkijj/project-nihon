package com.nihon.service;

import com.nihon.dto.PracticeRecordDto;
import com.nihon.dto.PracticeResultDto;
import com.nihon.dto.PracticeViJaResultDto;
import com.nihon.dto.StudyProgressSummaryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StudyProgressServiceTest {

    private StudyProgressService studyProgressService;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        studyProgressService = new StudyProgressService();
        session = new MockHttpSession();
    }

    @Test
    @DisplayName("Lịch sử trống: tổng câu = 0, đúng = 0, sai = 0, tỷ lệ = 0%, danh sách rỗng")
    void testEmptyHistorySummary() {
        StudyProgressSummaryDto summary = studyProgressService.getSummary(session, "ALL", null);

        assertThat(summary.getTotalAnswered()).isEqualTo(0);
        assertThat(summary.getCorrectCount()).isEqualTo(0);
        assertThat(summary.getIncorrectCount()).isEqualTo(0);
        assertThat(summary.getAccuracyPercentage()).isEqualTo(0);
        assertThat(summary.getRecords()).isEmpty();
        assertThat(summary.getIncorrectRecords()).isEmpty();
    }

    @Test
    @DisplayName("Tính toán điểm số: tính đúng tổng số câu, số câu đúng/sai và tỷ lệ chính xác")
    void testScoreCalculationWithCorrectAndIncorrectAnswers() {
        // Ghi nhận câu 1: Đúng (Nhật -> Việt)
        PracticeResultDto result1 = PracticeResultDto.builder()
                .vocabId(1L)
                .japaneseText("本（ほん）")
                .topicName("Bài 1")
                .overallCorrect(true)
                .userReading("ほん")
                .userMeaning("Sách")
                .expectedReading("ほん")
                .expectedMeaning("Sách")
                .requireReading(true)
                .feedbackMessage("Chính xác")
                .build();
        studyProgressService.recordJaViAttempt(session, result1, 10L);

        // Ghi nhận câu 2: Sai (Nhật -> Việt)
        PracticeResultDto result2 = PracticeResultDto.builder()
                .vocabId(2L)
                .japaneseText("車（くるま）")
                .topicName("Bài 1")
                .overallCorrect(false)
                .userReading("くるま")
                .userMeaning("Máy bay")
                .expectedReading("くるま")
                .expectedMeaning("Ô tô, Xe hơi")
                .requireReading(true)
                .feedbackMessage("Chưa đúng")
                .build();
        studyProgressService.recordJaViAttempt(session, result2, 10L);

        // Ghi nhận câu 3: Đúng (Việt -> Nhật)
        PracticeViJaResultDto result3 = PracticeViJaResultDto.builder()
                .vocabId(3L)
                .meaningVi("Nước")
                .topicName("Bài 2")
                .correct(true)
                .userJapanese("水")
                .expectedJapanese("水（みず）")
                .expectedReading("みず")
                .feedbackMessage("Chính xác")
                .build();
        studyProgressService.recordViJaAttempt(session, result3, 20L);

        // Tính tổng quan: 3 câu, 2 đúng, 1 sai -> Tỷ lệ 2/3 = 67%
        StudyProgressSummaryDto summary = studyProgressService.getSummary(session, "ALL", null);

        assertThat(summary.getTotalAnswered()).isEqualTo(3);
        assertThat(summary.getCorrectCount()).isEqualTo(2);
        assertThat(summary.getIncorrectCount()).isEqualTo(1);
        assertThat(summary.getAccuracyPercentage()).isEqualTo(67);
        assertThat(summary.getRecords()).hasSize(3);
        assertThat(summary.getIncorrectRecords()).hasSize(1);
    }

    @Test
    @DisplayName("Danh sách câu trả lời sai: chỉ chứa các câu trả lời sai để ôn tập lại")
    void testIncorrectAnswersListContainsOnlyMistakesForRevision() {
        // 1 câu đúng
        PracticeViJaResultDto correctResult = PracticeViJaResultDto.builder()
                .vocabId(101L)
                .meaningVi("Tôi")
                .topicName("Đại từ")
                .correct(true)
                .userJapanese("私")
                .expectedJapanese("私（わたし）")
                .expectedReading("わたし")
                .build();
        studyProgressService.recordViJaAttempt(session, correctResult, 1L);

        // 1 câu sai
        PracticeViJaResultDto mistakeResult = PracticeViJaResultDto.builder()
                .vocabId(102L)
                .meaningVi("Bạn")
                .topicName("Đại từ")
                .correct(false)
                .userJapanese("わたし")
                .expectedJapanese("あなた")
                .feedbackMessage("Chưa chính xác")
                .build();
        studyProgressService.recordViJaAttempt(session, mistakeResult, 1L);

        StudyProgressSummaryDto summary = studyProgressService.getSummary(session, "ALL", null);

        assertThat(summary.getIncorrectRecords()).hasSize(1);
        PracticeRecordDto mistake = summary.getIncorrectRecords().get(0);
        assertThat(mistake.getVocabId()).isEqualTo(102L);
        assertThat(mistake.getQuestionPrompt()).isEqualTo("Bạn");
        assertThat(mistake.getUserAnswer()).isEqualTo("わたし");
        assertThat(mistake.getExpectedAnswer()).contains("あなた");
        assertThat(mistake.isCorrect()).isFalse();
    }

    @Test
    @DisplayName("Lọc lịch sử theo chế độ kiểm tra (JA_VI vs VI_JA)")
    void testFilterByPracticeMode() {
        // Ghi nhận 2 câu JA_VI
        PracticeResultDto javi = PracticeResultDto.builder()
                .vocabId(1L)
                .japaneseText("山（やま）")
                .overallCorrect(true)
                .userMeaning("Núi")
                .expectedMeaning("Núi")
                .build();
        studyProgressService.recordJaViAttempt(session, javi, 1L);
        studyProgressService.recordJaViAttempt(session, javi, 1L);

        // Ghi nhận 1 câu VI_JA
        PracticeViJaResultDto vija = PracticeViJaResultDto.builder()
                .vocabId(2L)
                .meaningVi("Biển")
                .correct(false)
                .userJapanese("やま")
                .expectedJapanese("海")
                .build();
        studyProgressService.recordViJaAttempt(session, vija, 2L);

        // Lọc JA_VI
        StudyProgressSummaryDto jaViSummary = studyProgressService.getSummary(session, "JA_VI", null);
        assertThat(jaViSummary.getTotalAnswered()).isEqualTo(2);
        assertThat(jaViSummary.getCorrectCount()).isEqualTo(2);
        assertThat(jaViSummary.getIncorrectCount()).isEqualTo(0);
        assertThat(jaViSummary.getAccuracyPercentage()).isEqualTo(100);

        // Lọc VI_JA
        StudyProgressSummaryDto viJaSummary = studyProgressService.getSummary(session, "VI_JA", null);
        assertThat(viJaSummary.getTotalAnswered()).isEqualTo(1);
        assertThat(viJaSummary.getCorrectCount()).isEqualTo(0);
        assertThat(viJaSummary.getIncorrectCount()).isEqualTo(1);
        assertThat(viJaSummary.getAccuracyPercentage()).isEqualTo(0);

        // Tất cả (ALL)
        StudyProgressSummaryDto allSummary = studyProgressService.getSummary(session, "ALL", null);
        assertThat(allSummary.getTotalAnswered()).isEqualTo(3);
        assertThat(allSummary.getCorrectCount()).isEqualTo(2);
        assertThat(allSummary.getIncorrectCount()).isEqualTo(1);
        assertThat(allSummary.getAccuracyPercentage()).isEqualTo(67);
    }

    @Test
    @DisplayName("Lọc lịch sử theo chủ đề bài học")
    void testFilterByTopic() {
        PracticeResultDto resTopic1 = PracticeResultDto.builder()
                .vocabId(1L)
                .japaneseText("犬（いぬ）")
                .overallCorrect(true)
                .userMeaning("Chó")
                .expectedMeaning("Chó")
                .build();
        studyProgressService.recordJaViAttempt(session, resTopic1, 10L);

        PracticeResultDto resTopic2 = PracticeResultDto.builder()
                .vocabId(2L)
                .japaneseText("猫（ねこ）")
                .overallCorrect(false)
                .userMeaning("Hổ")
                .expectedMeaning("Mèo")
                .build();
        studyProgressService.recordJaViAttempt(session, resTopic2, 20L);

        // Lọc theo Topic 10
        StudyProgressSummaryDto topic10Summary = studyProgressService.getSummary(session, "ALL", 10L);
        assertThat(topic10Summary.getTotalAnswered()).isEqualTo(1);
        assertThat(topic10Summary.getCorrectCount()).isEqualTo(1);
        assertThat(topic10Summary.getIncorrectCount()).isEqualTo(0);

        // Lọc theo Topic 20
        StudyProgressSummaryDto topic20Summary = studyProgressService.getSummary(session, "ALL", 20L);
        assertThat(topic20Summary.getTotalAnswered()).isEqualTo(1);
        assertThat(topic20Summary.getCorrectCount()).isEqualTo(0);
        assertThat(topic20Summary.getIncorrectCount()).isEqualTo(1);

        // Lọc theo Topic không tồn tại trong lịch sử
        StudyProgressSummaryDto topic99Summary = studyProgressService.getSummary(session, "ALL", 99L);
        assertThat(topic99Summary.getTotalAnswered()).isEqualTo(0);
    }

    @Test
    @DisplayName("Xóa tiến độ trong session: khôi phục về trạng thái ban đầu")
    void testClearProgress() {
        PracticeResultDto res = PracticeResultDto.builder()
                .vocabId(1L)
                .japaneseText("日")
                .overallCorrect(true)
                .userMeaning("Ngày")
                .expectedMeaning("Ngày")
                .build();
        studyProgressService.recordJaViAttempt(session, res, 1L);

        assertThat(studyProgressService.getSummary(session, "ALL", null).getTotalAnswered()).isEqualTo(1);

        // Xóa tiến độ
        studyProgressService.clearProgress(session);

        StudyProgressSummaryDto cleared = studyProgressService.getSummary(session, "ALL", null);
        assertThat(cleared.getTotalAnswered()).isEqualTo(0);
        assertThat(cleared.getRecords()).isEmpty();
    }

    @Test
    @DisplayName("An toàn với session null: không ném ngoại lệ và trả về kết quả rỗng")
    void testNullSessionSafety() {
        studyProgressService.recordJaViAttempt(null, null, null);
        studyProgressService.recordViJaAttempt(null, null, null);
        studyProgressService.clearProgress(null);

        StudyProgressSummaryDto summary = studyProgressService.getSummary(null, "ALL", null);
        assertThat(summary.getTotalAnswered()).isEqualTo(0);
        assertThat(summary.getRecords()).isEmpty();
    }
}
