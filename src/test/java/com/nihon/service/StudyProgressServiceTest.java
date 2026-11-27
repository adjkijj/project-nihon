package com.nihon.service;

import com.nihon.dto.PracticeChoiceResultDto;
import com.nihon.dto.PracticeRecordDto;
import com.nihon.dto.PracticeResultDto;
import com.nihon.dto.PracticeViJaResultDto;
import com.nihon.dto.StudyProgressSummaryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;
import java.util.Set;

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

    @Test
    @DisplayName("Tham số mode không hợp lệ trong getSummary được coi như ALL")
    void testInvalidModeTreatedAsAll() {
        PracticeResultDto javi = PracticeResultDto.builder()
                .vocabId(1L)
                .japaneseText("山（やま）")
                .overallCorrect(true)
                .userMeaning("Núi")
                .expectedMeaning("Núi")
                .build();
        studyProgressService.recordJaViAttempt(session, javi, 1L);

        PracticeViJaResultDto vija = PracticeViJaResultDto.builder()
                .vocabId(2L)
                .meaningVi("Biển")
                .correct(false)
                .userJapanese("やま")
                .expectedJapanese("海")
                .build();
        studyProgressService.recordViJaAttempt(session, vija, 2L);

        StudyProgressSummaryDto summaryAbc = studyProgressService.getSummary(session, "abc", null);
        StudyProgressSummaryDto summaryAll = studyProgressService.getSummary(session, "ALL", null);

        assertThat(summaryAbc.getCurrentModeFilter()).isEqualTo("ALL");
        assertThat(summaryAbc.getTotalAnswered()).isEqualTo(summaryAll.getTotalAnswered());
        assertThat(summaryAbc.getCorrectCount()).isEqualTo(summaryAll.getCorrectCount());
        assertThat(summaryAbc.getIncorrectCount()).isEqualTo(summaryAll.getIncorrectCount());
    }

    @Test
    @DisplayName("Giới hạn lịch sử session tối đa MAX_SESSION_RECORDS (500 bản ghi mới nhất)")
    void testSessionHistoryCapAtMaxRecords() {
        for (int i = 1; i <= StudyProgressService.MAX_SESSION_RECORDS + 25; i++) {
            PracticeResultDto res = PracticeResultDto.builder()
                    .vocabId((long) i)
                    .japaneseText("Từ #" + i)
                    .overallCorrect(true)
                    .userMeaning("Nghĩa #" + i)
                    .expectedMeaning("Nghĩa #" + i)
                    .build();
            studyProgressService.recordJaViAttempt(session, res, 1L);
        }

        List<PracticeRecordDto> history = studyProgressService.getHistory(session);
        assertThat(history).hasSize(StudyProgressService.MAX_SESSION_RECORDS);
        assertThat(history.get(0).getQuestionPrompt()).isEqualTo("Từ #" + (StudyProgressService.MAX_SESSION_RECORDS + 25));
        assertThat(history.get(history.size() - 1).getQuestionPrompt()).isEqualTo("Từ #26");
    }

    @Test
    @DisplayName("Ghi nhận và lọc chính xác các bài trắc nghiệm MC_JA_VI và MC_VI_JA")
    void testChoiceAttemptRecordingAndFiltering() {
        PracticeChoiceResultDto mcJaVi = PracticeChoiceResultDto.builder()
                .vocabId(1L)
                .direction("JA_VI")
                .questionPrompt("食べる")
                .selectedAnswerText("ăn")
                .correctAnswerText("ăn")
                .correct(true)
                .feedbackMessage("Chính xác!")
                .build();
        studyProgressService.recordChoiceAttempt(session, mcJaVi, 10L);

        PracticeChoiceResultDto mcViJa = PracticeChoiceResultDto.builder()
                .vocabId(2L)
                .direction("VI_JA")
                .questionPrompt("uống")
                .selectedAnswerText("たべる")
                .correctAnswerText("のむ")
                .correct(false)
                .feedbackMessage("Chưa chính xác!")
                .build();
        studyProgressService.recordChoiceAttempt(session, mcViJa, 10L);

        // Lọc theo MC_JA_VI
        StudyProgressSummaryDto jaViSummary = studyProgressService.getSummary(session, "MC_JA_VI", null);
        assertThat(jaViSummary.getTotalAnswered()).isEqualTo(1);
        assertThat(jaViSummary.getCorrectCount()).isEqualTo(1);
        assertThat(jaViSummary.getCurrentModeFilter()).isEqualTo("MC_JA_VI");

        // Lọc theo MC_VI_JA
        StudyProgressSummaryDto viJaSummary = studyProgressService.getSummary(session, "MC_VI_JA", null);
        assertThat(viJaSummary.getTotalAnswered()).isEqualTo(1);
        assertThat(viJaSummary.getCorrectCount()).isEqualTo(0);
        assertThat(viJaSummary.getCurrentModeFilter()).isEqualTo("MC_VI_JA");

        // Tổng hợp ALL
        StudyProgressSummaryDto allSummary = studyProgressService.getSummary(session, "ALL", null);
        assertThat(allSummary.getTotalAnswered()).isEqualTo(2);
        assertThat(allSummary.getCorrectCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Test 1: Làm sai 3 từ, đúng 1 trong số đó ở lần sau: tập còn 2")
    void testWrongVocabTracking_3Wrong_1Corrected_Leaves2() {
        // Làm sai từ 1, 2, 3 trong JA_VI
        studyProgressService.recordJaViAttempt(session, PracticeResultDto.builder()
                .vocabId(1L)
                .overallCorrect(false)
                .build(), 10L);
        studyProgressService.recordJaViAttempt(session, PracticeResultDto.builder()
                .vocabId(2L)
                .overallCorrect(false)
                .build(), 10L);
        studyProgressService.recordJaViAttempt(session, PracticeResultDto.builder()
                .vocabId(3L)
                .overallCorrect(false)
                .build(), 10L);

        Set<Long> wrongInitial = studyProgressService.getWrongVocabIds(session, "JA_VI", null);
        assertThat(wrongInitial).containsExactlyInAnyOrder(1L, 2L, 3L);

        // Trả lời đúng từ 2 ở lần sau
        studyProgressService.recordJaViAttempt(session, PracticeResultDto.builder()
                .vocabId(2L)
                .overallCorrect(true)
                .build(), 10L);

        Set<Long> wrongAfterCorrection = studyProgressService.getWrongVocabIds(session, "JA_VI", null);
        assertThat(wrongAfterCorrection).hasSize(2)
                .containsExactlyInAnyOrder(1L, 3L)
                .doesNotContain(2L);
    }

    @Test
    @DisplayName("Test 2: Tập theo mode: từ sai ở JA_VI không lẫn vào tập VI_JA")
    void testWrongVocabTracking_ModeIsolation_JaViVsViJa() {
        // Làm sai từ 10 ở JA_VI
        studyProgressService.recordJaViAttempt(session, PracticeResultDto.builder()
                .vocabId(10L)
                .overallCorrect(false)
                .build(), 1L);

        // Làm sai từ 20 ở VI_JA
        studyProgressService.recordViJaAttempt(session, PracticeViJaResultDto.builder()
                .vocabId(20L)
                .correct(false)
                .build(), 1L);

        Set<Long> jaViWrong = studyProgressService.getWrongVocabIds(session, "JA_VI", null);
        Set<Long> viJaWrong = studyProgressService.getWrongVocabIds(session, "VI_JA", null);
        Set<Long> allWrong = studyProgressService.getWrongVocabIds(session, "ALL", null);

        assertThat(jaViWrong).containsExactly(10L).doesNotContain(20L);
        assertThat(viJaWrong).containsExactly(20L).doesNotContain(10L);
        assertThat(allWrong).containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    @DisplayName("Chuẩn hóa chế độ normalizeWrongMode tuân theo whitelist JA_VI, VI_JA, ALL")
    void testNormalizeWrongModeWhitelist() {
        assertThat(studyProgressService.normalizeWrongMode("ja_vi")).isEqualTo("JA_VI");
        assertThat(studyProgressService.normalizeWrongMode("VI_JA")).isEqualTo("VI_JA");
        assertThat(studyProgressService.normalizeWrongMode("ALL")).isEqualTo("ALL");
        assertThat(studyProgressService.normalizeWrongMode(null)).isEqualTo("ALL");
        assertThat(studyProgressService.normalizeWrongMode("")).isEqualTo("ALL");
        assertThat(studyProgressService.normalizeWrongMode("abc")).isEqualTo("ALL");
    }
}
