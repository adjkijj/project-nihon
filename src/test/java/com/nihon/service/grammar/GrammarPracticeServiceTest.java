package com.nihon.service.grammar;

import com.nihon.dto.grammar.GrammarArenaAnswerRecord;
import com.nihon.dto.grammar.GrammarArenaChoiceItem;
import com.nihon.dto.grammar.GrammarArenaQuestionDto;
import com.nihon.dto.grammar.GrammarArenaQuestionItem;
import com.nihon.dto.grammar.GrammarArenaResultDto;
import com.nihon.dto.grammar.GrammarArenaSession;
import com.nihon.dto.grammar.GrammarArenaStartForm;
import com.nihon.dto.grammar.GrammarProgressToggleResponse;
import com.nihon.dto.grammar.GrammarQuizAnswerItem;
import com.nihon.dto.grammar.GrammarQuizCheckResult;
import com.nihon.dto.grammar.GrammarQuizChoiceForm;
import com.nihon.dto.grammar.GrammarQuizQuestionForm;
import com.nihon.dto.grammar.GrammarQuizSubmissionForm;
import com.nihon.dto.grammar.GrammarTopicForm;
import com.nihon.entity.GrammarCategory;
import com.nihon.entity.GrammarProgress;
import com.nihon.entity.GrammarProgressId;
import com.nihon.entity.GrammarQuizChoice;
import com.nihon.entity.GrammarQuizQuestion;
import com.nihon.entity.GrammarTopic;
import com.nihon.repository.GrammarProgressRepository;
import com.nihon.repository.GrammarQuizQuestionRepository;
import com.nihon.repository.GrammarTopicRepository;
import com.nihon.repository.StudySessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class GrammarPracticeServiceTest {

    @Autowired
    private GrammarPracticeService practiceService;

    @Autowired
    private GrammarService grammarService;

    @Autowired
    private GrammarTopicRepository topicRepository;

    @Autowired
    private GrammarProgressRepository progressRepository;

    @Autowired
    private GrammarQuizQuestionRepository questionRepository;

    @Autowired
    private StudySessionRepository studySessionRepository;

    private GrammarTopic topic1;
    private GrammarTopic topic2;
    private GrammarQuizQuestion q1;
    private GrammarQuizChoice q1ChoiceCorrect;
    private GrammarQuizChoice q1ChoiceWrong;

    private final String learnerA = UUID.randomUUID().toString();
    private final String learnerB = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        // Tạo chủ đề 1
        GrammarTopicForm form1 = GrammarTopicForm.builder()
                .code("TEST-F30-01")
                .title("Cấu trúc Test 1")
                .category("GRAMMAR")
                .formula("A + から")
                .explanation("Giải thích test 1")
                .build();
        topic1 = grammarService.createTopic(form1);

        // Tạo câu hỏi cho chủ đề 1
        List<GrammarQuizChoiceForm> choices = List.of(
                GrammarQuizChoiceForm.builder().sortOrder(1).text("から").correct(true).build(),
                GrammarQuizChoiceForm.builder().sortOrder(2).text("まで").correct(false).build(),
                GrammarQuizChoiceForm.builder().sortOrder(3).text("より").correct(false).build(),
                GrammarQuizChoiceForm.builder().sortOrder(4).text("ほど").correct(false).build()
        );
        GrammarQuizQuestionForm qForm = GrammarQuizQuestionForm.builder()
                .prompt("Đề bài test 1")
                .explanation("Giải thích câu 1")
                .choices(choices)
                .correctChoiceIndex(0)
                .build();
        q1 = grammarService.addQuestion(topic1.getId(), qForm);

        for (GrammarQuizChoice c : q1.getChoices()) {
            if (c.isCorrect()) {
                q1ChoiceCorrect = c;
            } else if (q1ChoiceWrong == null) {
                q1ChoiceWrong = c;
            }
        }

        // Tạo chủ đề 2
        GrammarTopicForm form2 = GrammarTopicForm.builder()
                .code("TEST-F30-02")
                .title("Kiến thức bổ sung Test 2")
                .category("SUPPLEMENT")
                .explanation("Giải thích test 2")
                .build();
        topic2 = grammarService.createTopic(form2);
    }

    @Test
    @DisplayName("F30 Tiến độ: Đánh dấu đã học, hủy đánh dấu (bỏ trạng thái) và cập nhật phần trăm")
    void testToggleProgressAndPercentage() {
        // 1. Ban đầu chưa học
        assertThat(practiceService.getLearnedCount(learnerA)).isZero();
        assertThat(practiceService.calculateProgressPercent(learnerA)).isZero();

        // 2. Đánh dấu đã học topic1
        GrammarProgressToggleResponse res1 = practiceService.toggleProgress(topic1.getId(), learnerA);
        assertThat(res1.isLearned()).isTrue();
        assertThat(res1.getTotalLearned()).isEqualTo(1);
        assertThat(res1.getProgressPercent()).isGreaterThan(0);
        assertThat(practiceService.getLearnedGrammarIds(learnerA)).contains(topic1.getId());

        // 3. Đánh dấu đã học topic2
        GrammarProgressToggleResponse res2 = practiceService.toggleProgress(topic2.getId(), learnerA);
        assertThat(res2.isLearned()).isTrue();
        assertThat(res2.getTotalLearned()).isEqualTo(2);

        // 4. Hủy đánh dấu topic1 (bỏ trạng thái)
        GrammarProgressToggleResponse res3 = practiceService.toggleProgress(topic1.getId(), learnerA);
        assertThat(res3.isLearned()).isFalse();
        assertThat(res3.getTotalLearned()).isEqualTo(1);
        assertThat(practiceService.getLearnedGrammarIds(learnerA)).doesNotContain(topic1.getId());
        assertThat(practiceService.getLearnedGrammarIds(learnerA)).contains(topic2.getId());
    }

    @Test
    @DisplayName("F30 Cách ly người học: Tiến độ theo learner_id, A không thấy tiến độ của B")
    void testLearnerIsolation() {
        // Learner A học topic 1
        practiceService.toggleProgress(topic1.getId(), learnerA);

        Set<Long> learnedA = practiceService.getLearnedGrammarIds(learnerA);
        Set<Long> learnedB = practiceService.getLearnedGrammarIds(learnerB);

        assertThat(learnedA).contains(topic1.getId());
        assertThat(learnedB).doesNotContain(topic1.getId());
        assertThat(practiceService.getLearnedCount(learnerB)).isZero();

        // Learner B học topic 2
        practiceService.toggleProgress(topic2.getId(), learnerB);
        assertThat(practiceService.getLearnedGrammarIds(learnerA)).doesNotContain(topic2.getId());
        assertThat(practiceService.getLearnedGrammarIds(learnerB)).contains(topic2.getId());
    }

    @Test
    @DisplayName("F32 Chấm trắc nghiệm mini tại server: Trả kết quả, giải thích và ghi StudySession")
    void testCheckMiniQuiz() {
        long sessionCountBefore = studySessionRepository.count();

        // Nộp đáp án đúng
        GrammarQuizSubmissionForm formCorrect = GrammarQuizSubmissionForm.builder()
                .grammarId(topic1.getId())
                .answers(List.of(new GrammarQuizAnswerItem(q1.getId(), q1ChoiceCorrect.getId())))
                .build();

        GrammarQuizCheckResult result = practiceService.checkMiniQuiz(formCorrect, learnerA);
        assertThat(result.getTotalQuestions()).isEqualTo(1);
        assertThat(result.getCorrectCount()).isEqualTo(1);
        assertThat(result.isAllCorrect()).isTrue();
        assertThat(result.getScorePercent()).isEqualTo(100);
        assertThat(result.getQuestionResults().get(0).isCorrect()).isTrue();
        assertThat(result.getQuestionResults().get(0).getExplanation()).isEqualTo("Giải thích câu 1");

        // Kiểm tra đã ghi một StudySession mode GRAMMAR_QUIZ
        assertThat(studySessionRepository.count()).isEqualTo(sessionCountBefore + 1);

        // Nộp đáp án sai
        GrammarQuizSubmissionForm formWrong = GrammarQuizSubmissionForm.builder()
                .grammarId(topic1.getId())
                .answers(List.of(new GrammarQuizAnswerItem(q1.getId(), q1ChoiceWrong.getId())))
                .build();

        GrammarQuizCheckResult wrongResult = practiceService.checkMiniQuiz(formWrong, learnerA);
        assertThat(wrongResult.getCorrectCount()).isZero();
        assertThat(wrongResult.isAllCorrect()).isFalse();
        assertThat(wrongResult.getQuestionResults().get(0).isCorrect()).isFalse();
        assertThat(wrongResult.getQuestionResults().get(0).getCorrectChoiceId()).isEqualTo(q1ChoiceCorrect.getId());
    }

    @Test
    @DisplayName("F33 Đấu trường phản xạ: Khởi tạo phiên, xáo trộn câu hỏi, streak và tính điểm")
    void testArenaLifecycle() {
        MockHttpSession session = new MockHttpSession();

        // 1. Tạo đấu trường 10 câu (khi chỉ có 1 câu hỏi có sẵn -> quy tắc lặp lại tối đa 3 lần có xáo trộn)
        GrammarArenaStartForm startForm = GrammarArenaStartForm.builder()
                .category("ALL")
                .durationMinutes(2)
                .questionCount(10)
                .build();

        GrammarArenaSession arena = practiceService.startArena(startForm, session);
        assertThat(arena).isNotNull();
        assertThat(arena.isRunning()).isTrue();
        // Với 1 câu hỏi, quy tắc giới hạn tối đa không quá 3 lần mỗi câu -> target = min(10, 3 * 1) = 3 câu
        assertThat(arena.getQuestionCount()).isEqualTo(3);
        assertThat(arena.getQuestions()).hasSize(3);

        // Kiểm tra phương án lựa chọn không chứa cờ đúng
        GrammarArenaQuestionItem firstQ = arena.getQuestions().get(0);
        for (GrammarArenaChoiceItem c : firstQ.getChoices()) {
            assertThat(c.getChoiceId()).isNotNull();
            assertThat(c.getText()).isNotBlank();
        }

        // 2. Lấy câu hỏi hiện tại ra DTO cho client
        GrammarArenaQuestionDto currentQDto = practiceService.getCurrentArenaQuestion(session);
        assertThat(currentQDto).isNotNull();
        assertThat(currentQDto.getQuestionIndex()).isEqualTo(1);
        assertThat(currentQDto.getRemainingSeconds()).isGreaterThan(0);

        // 3. Trả lời câu 1: Đúng -> Streak = 1
        boolean ans1 = practiceService.submitArenaAnswer(q1ChoiceCorrect.getId(), session, learnerA);
        assertThat(ans1).isTrue();
        assertThat(arena.getCurrentStreak()).isEqualTo(1);
        assertThat(arena.getMaxStreak()).isEqualTo(1);

        // 4. Trả lời câu 2: Đúng -> Streak = 2
        boolean ans2 = practiceService.submitArenaAnswer(q1ChoiceCorrect.getId(), session, learnerA);
        assertThat(ans2).isTrue();
        assertThat(arena.getCurrentStreak()).isEqualTo(2);
        assertThat(arena.getMaxStreak()).isEqualTo(2);

        // 5. Trả lời câu 3: Sai -> Streak về 0, MaxStreak vẫn là 2
        boolean ans3 = practiceService.submitArenaAnswer(q1ChoiceWrong.getId(), session, learnerA);
        assertThat(ans3).isFalse();
        assertThat(arena.getCurrentStreak()).isZero();
        assertThat(arena.getMaxStreak()).isEqualTo(2);

        // 6. Hoàn thành bài thi -> Tổng kết kết quả
        GrammarArenaResultDto result = practiceService.getLastArenaResult(session);
        assertThat(result).isNotNull();
        assertThat(result.getTotalQuestions()).isEqualTo(3);
        assertThat(result.getCorrectCount()).isEqualTo(2);
        assertThat(result.getMaxStreak()).isEqualTo(2);
        assertThat(result.getAccuracyPercent()).isEqualTo(67);
    }

    @Test
    @DisplayName("F30 Tác vụ dọn dẹp tiến độ quá hạn theo retention-days")
    void testCleanupExpiredProgress() {
        // Tạo một bản ghi tiến độ cách đây 400 ngày
        GrammarProgress oldProgress = GrammarProgress.builder()
                .id(new GrammarProgressId(learnerA, topic1.getId()))
                .grammarTopic(topic1)
                .learnedAt(Instant.now().minus(400, ChronoUnit.DAYS))
                .build();
        progressRepository.save(oldProgress);

        // Dọn dẹp với retention-days = 365
        int deleted = practiceService.cleanupExpiredProgress(365);
        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(progressRepository.existsByIdLearnerIdAndIdGrammarId(learnerA, topic1.getId())).isFalse();
    }
}
