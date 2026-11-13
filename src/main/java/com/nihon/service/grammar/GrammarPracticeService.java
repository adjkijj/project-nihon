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
import com.nihon.dto.grammar.GrammarQuizQuestionFeedback;
import com.nihon.dto.grammar.GrammarQuizSubmissionForm;
import com.nihon.dto.timed.TimedSessionStatus;
import com.nihon.entity.GrammarCategory;
import com.nihon.entity.GrammarProgress;
import com.nihon.entity.GrammarProgressId;
import com.nihon.entity.GrammarQuizChoice;
import com.nihon.entity.GrammarQuizQuestion;
import com.nihon.entity.GrammarTopic;
import com.nihon.entity.Topic;
import com.nihon.repository.GrammarProgressRepository;
import com.nihon.repository.GrammarQuizChoiceRepository;
import com.nihon.repository.GrammarQuizQuestionRepository;
import com.nihon.repository.GrammarTopicRepository;
import com.nihon.repository.TopicRepository;
import com.nihon.service.StudyHistoryService;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Dịch vụ luyện tập và theo dõi tiến độ học ngữ pháp (F30, F32, F33).
 */
@Service
@Slf4j
public class GrammarPracticeService {

    public static final String SESSION_ARENA_LAST_RESULT = "GRAMMAR_ARENA_LAST_RESULT";
    public static final int DEFAULT_RETENTION_DAYS = 365;

    private final GrammarTopicRepository topicRepository;
    private final GrammarProgressRepository progressRepository;
    private final GrammarQuizQuestionRepository questionRepository;
    private final GrammarQuizChoiceRepository choiceRepository;
    private final TopicRepository generalTopicRepository;
    private final StudyHistoryService studyHistoryService;

    public GrammarPracticeService(GrammarTopicRepository topicRepository,
                                  GrammarProgressRepository progressRepository,
                                  GrammarQuizQuestionRepository questionRepository,
                                  GrammarQuizChoiceRepository choiceRepository,
                                  TopicRepository generalTopicRepository,
                                  StudyHistoryService studyHistoryService) {
        this.topicRepository = topicRepository;
        this.progressRepository = progressRepository;
        this.questionRepository = questionRepository;
        this.choiceRepository = choiceRepository;
        this.generalTopicRepository = generalTopicRepository;
        this.studyHistoryService = studyHistoryService;
    }

    // =========================================================================
    // 1. Tiến độ học tập theo learner_id (F30)
    // =========================================================================

    public Set<Long> getLearnedGrammarIds(String learnerId) {
        if (learnerId == null || learnerId.isBlank()) {
            return Collections.emptySet();
        }
        return progressRepository.findLearnedGrammarIdsByLearnerId(learnerId.trim());
    }

    public long getLearnedCount(String learnerId) {
        if (learnerId == null || learnerId.isBlank()) {
            return 0;
        }
        return progressRepository.countByIdLearnerId(learnerId.trim());
    }

    public int calculateProgressPercent(String learnerId) {
        long total = topicRepository.count();
        if (total == 0) {
            return 0;
        }
        long learned = getLearnedCount(learnerId);
        return (int) Math.round((double) learned * 100.0 / total);
    }

    @Transactional
    public GrammarProgressToggleResponse toggleProgress(Long grammarId, String learnerId) {
        if (learnerId == null || learnerId.isBlank()) {
            throw new IllegalArgumentException("Mã người học không hợp lệ.");
        }
        String cleanLearnerId = learnerId.trim();

        GrammarTopic topic = topicRepository.findById(grammarId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy chủ đề ngữ pháp với ID: " + grammarId));

        GrammarProgressId progressId = new GrammarProgressId(cleanLearnerId, grammarId);
        Optional<GrammarProgress> existing = progressRepository.findById(progressId);
        boolean nowLearned;

        if (existing.isPresent()) {
            progressRepository.delete(existing.get());
            progressRepository.flush();
            nowLearned = false;
        } else {
            GrammarProgress progress = GrammarProgress.builder()
                    .id(progressId)
                    .grammarTopic(topic)
                    .learnedAt(Instant.now())
                    .build();
            progressRepository.saveAndFlush(progress);
            nowLearned = true;
        }

        long totalLearned = progressRepository.countByIdLearnerId(cleanLearnerId);
        long totalTopics = topicRepository.count();
        int percent = totalTopics > 0 ? (int) Math.round((double) totalLearned * 100.0 / totalTopics) : 0;

        return GrammarProgressToggleResponse.builder()
                .grammarId(grammarId)
                .learned(nowLearned)
                .totalLearned(totalLearned)
                .totalTopics(totalTopics)
                .progressPercent(percent)
                .message(nowLearned ? "Đã đánh dấu đã học chủ đề." : "Đã bỏ đánh dấu đã học.")
                .build();
    }

    /**
     * Dọn dẹp tiến độ cũ theo chu kỳ retention-days (mặc định 365 ngày).
     */
    @Scheduled(cron = "0 0 3 * * ?") // 3h sáng mỗi ngày
    @Transactional
    public int cleanupExpiredProgress() {
        return cleanupExpiredProgress(DEFAULT_RETENTION_DAYS);
    }

    @Transactional
    public int cleanupExpiredProgress(int days) {
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        int deleted = progressRepository.deleteByLearnedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Dọn dẹp tiến độ học ngữ pháp: Đã xóa {} bản ghi cũ hơn {} ngày.", deleted, days);
        }
        return deleted;
    }

    // =========================================================================
    // 2. Chấm trắc nghiệm mini tại thẻ (F32)
    // =========================================================================

    @Transactional
    public GrammarQuizCheckResult checkMiniQuiz(GrammarQuizSubmissionForm form, String learnerId) {
        if (form == null || form.getGrammarId() == null) {
            throw new IllegalArgumentException("Dữ liệu nộp bài không hợp lệ.");
        }

        GrammarTopic topic = topicRepository.findById(form.getGrammarId())
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy chủ đề ngữ pháp với ID: " + form.getGrammarId()));

        List<GrammarQuizQuestion> questions = questionRepository.findByGrammarTopicIdOrderBySortOrderAsc(topic.getId());
        if (questions.isEmpty()) {
            throw new IllegalStateException("Chủ đề ngữ pháp này chưa có câu hỏi trắc nghiệm.");
        }

        Map<Long, Long> userChoiceMap = new HashMap<>();
        if (form.getAnswers() != null) {
            for (GrammarQuizAnswerItem ans : form.getAnswers()) {
                if (ans.getQuestionId() != null && ans.getChoiceId() != null) {
                    userChoiceMap.put(ans.getQuestionId(), ans.getChoiceId());
                }
            }
        }

        List<GrammarQuizQuestionFeedback> feedbacks = new ArrayList<>();
        List<StudyHistoryService.AnswerItem> historyAnswers = new ArrayList<>();
        int correctCount = 0;

        for (GrammarQuizQuestion q : questions) {
            Long userChoiceId = userChoiceMap.get(q.getId());
            GrammarQuizChoice correctChoice = null;
            GrammarQuizChoice userChoice = null;

            for (GrammarQuizChoice c : q.getChoices()) {
                if (c.isCorrect()) {
                    correctChoice = c;
                }
                if (userChoiceId != null && userChoiceId.equals(c.getId())) {
                    userChoice = c;
                }
            }

            boolean isCorrect = (correctChoice != null && userChoice != null && correctChoice.getId().equals(userChoice.getId()));
            if (isCorrect) {
                correctCount++;
            }

            feedbacks.add(GrammarQuizQuestionFeedback.builder()
                    .questionId(q.getId())
                    .prompt(q.getPrompt())
                    .userChoiceId(userChoiceId)
                    .correctChoiceId(correctChoice != null ? correctChoice.getId() : null)
                    .userChoiceText(userChoice != null ? userChoice.getText() : "(Chưa chọn)")
                    .correctChoiceText(correctChoice != null ? correctChoice.getText() : "")
                    .correct(isCorrect)
                    .explanation(q.getExplanation())
                    .build());

            historyAnswers.add(new StudyHistoryService.AnswerItem(null, null, isCorrect));
        }

        int total = questions.size();
        int scorePercent = total > 0 ? (int) Math.round((double) correctCount * 100.0 / total) : 0;
        boolean allCorrect = (correctCount == total && total > 0);

        // Ghi StudySession với mode GRAMMAR_QUIZ (F49 / Prompt 16)
        if (learnerId != null && !learnerId.isBlank()) {
            Long generalTopicId = (topic.getTopic() != null) ? topic.getTopic().getId() : null;
            studyHistoryService.saveSessionSafe(learnerId.trim(), "GRAMMAR_QUIZ", generalTopicId, historyAnswers, 30);
        }

        return GrammarQuizCheckResult.builder()
                .grammarId(topic.getId())
                .totalQuestions(total)
                .correctCount(correctCount)
                .allCorrect(allCorrect)
                .scorePercent(scorePercent)
                .questionResults(feedbacks)
                .build();
    }

    // =========================================================================
    // 3. Đấu trường phản xạ ngữ pháp (F33)
    // =========================================================================

    public synchronized GrammarArenaSession startArena(GrammarArenaStartForm form, HttpSession session) {
        if (form == null) {
            throw new IllegalArgumentException("Dữ liệu cấu hình đấu trường không hợp lệ.");
        }

        int durationMinutes = (form.getDurationMinutes() != null && GrammarArenaStartForm.ALLOWED_DURATIONS.contains(form.getDurationMinutes()))
                ? form.getDurationMinutes() : 5;
        int questionCount = (form.getQuestionCount() != null && GrammarArenaStartForm.ALLOWED_QUESTION_COUNTS.contains(form.getQuestionCount()))
                ? form.getQuestionCount() : 10;

        String categoryStr = (form.getCategory() != null && !form.getCategory().isBlank()) ? form.getCategory().trim().toUpperCase() : "ALL";
        GrammarCategory category = null;
        if (!"ALL".equals(categoryStr)) {
            category = GrammarCategory.fromString(categoryStr);
        }

        Long topicId = (form.getTopicId() != null && form.getTopicId() > 0) ? form.getTopicId() : null;
        String topicName = "Tất cả bài học";
        if (topicId != null) {
            Topic top = generalTopicRepository.findById(topicId)
                    .orElseThrow(() -> new NoSuchElementException("Không tìm thấy chủ đề bài học với ID: " + topicId));
            topicName = top.getName();
        }

        List<GrammarQuizQuestion> availableQuestions = questionRepository.findQuestionsForArena(category, topicId);
        if (availableQuestions == null || availableQuestions.isEmpty()) {
            throw new IllegalStateException("Không tìm thấy câu hỏi trắc nghiệm ngữ pháp nào phù hợp với bộ lọc đã chọn.");
        }

        // Quy tắc: Nếu số câu ít hơn số yêu cầu thì lặp lại có xáo trộn, không quá 3 lần mỗi câu.
        List<GrammarQuizQuestion> pool = new ArrayList<>();
        int n = availableQuestions.size();
        int maxAllowed = 3 * n;
        int targetCount = Math.min(questionCount, maxAllowed);

        if (n >= targetCount) {
            List<GrammarQuizQuestion> shuffled = new ArrayList<>(availableQuestions);
            Collections.shuffle(shuffled);
            pool.addAll(shuffled.subList(0, targetCount));
        } else {
            int repeats = (int) Math.ceil((double) targetCount / n);
            repeats = Math.min(3, Math.max(1, repeats));
            for (int r = 0; r < repeats; r++) {
                List<GrammarQuizQuestion> copy = new ArrayList<>(availableQuestions);
                Collections.shuffle(copy);
                pool.addAll(copy);
            }
            Collections.shuffle(pool);
            if (pool.size() > targetCount) {
                pool = pool.subList(0, targetCount);
            }
        }

        List<GrammarArenaQuestionItem> items = new ArrayList<>();
        int itemIndex = 1;
        for (GrammarQuizQuestion q : pool) {
            List<GrammarArenaChoiceItem> choices = new ArrayList<>();
            Long correctChoiceId = null;
            String correctChoiceText = "";

            List<GrammarQuizChoice> qChoices = new ArrayList<>(q.getChoices());
            Collections.shuffle(qChoices); // Xáo trộn đáp án để không lộ vị trí cố định

            int cSort = 1;
            for (GrammarQuizChoice c : qChoices) {
                choices.add(GrammarArenaChoiceItem.builder()
                        .choiceId(c.getId())
                        .sortOrder(cSort++)
                        .text(c.getText())
                        .build());
                if (c.isCorrect()) {
                    correctChoiceId = c.getId();
                    correctChoiceText = c.getText();
                }
            }

            items.add(GrammarArenaQuestionItem.builder()
                    .index(itemIndex++)
                    .questionId(q.getId())
                    .grammarId(q.getGrammarTopic().getId())
                    .grammarTitle(q.getGrammarTopic().getTitle())
                    .prompt(q.getPrompt())
                    .explanation(q.getExplanation())
                    .choices(choices)
                    .correctChoiceId(correctChoiceId)
                    .correctChoiceText(correctChoiceText)
                    .build());
        }

        Instant now = Instant.now();
        Instant deadline = now.plus(Duration.ofMinutes(durationMinutes));

        GrammarArenaSession arenaSession = GrammarArenaSession.builder()
                .id(UUID.randomUUID().toString())
                .category(categoryStr)
                .topicId(topicId)
                .topicName(topicName)
                .durationMinutes(durationMinutes)
                .questionCount(items.size())
                .startedAt(now)
                .deadline(deadline)
                .currentIndex(0)
                .currentStreak(0)
                .maxStreak(0)
                .questions(items)
                .answers(new ArrayList<>())
                .status(TimedSessionStatus.RUNNING)
                .build();

        session.setAttribute(GrammarArenaSession.SESSION_ATTR, arenaSession);
        return arenaSession;
    }

    public GrammarArenaSession getActiveArenaSession(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object attr = session.getAttribute(GrammarArenaSession.SESSION_ATTR);
        if (!(attr instanceof GrammarArenaSession arenaSession)) {
            return null;
        }

        Instant now = Instant.now();
        if (arenaSession.getStatus() == TimedSessionStatus.RUNNING) {
            if (arenaSession.isExpired(now)) {
                finalizeArenaSession(session, TimedSessionStatus.EXPIRED, null);
                return null;
            }
            return arenaSession;
        }
        return null;
    }

    public GrammarArenaQuestionDto getCurrentArenaQuestion(HttpSession session) {
        GrammarArenaSession arenaSession = getActiveArenaSession(session);
        if (arenaSession == null || !arenaSession.isRunning()) {
            return null;
        }

        if (arenaSession.getCurrentIndex() >= arenaSession.getQuestions().size()) {
            finalizeArenaSession(session, TimedSessionStatus.FINISHED, null);
            return null;
        }

        Instant now = Instant.now();
        GrammarArenaQuestionItem q = arenaSession.getQuestions().get(arenaSession.getCurrentIndex());

        GrammarArenaAnswerRecord lastResult = null;
        if (!arenaSession.getAnswers().isEmpty() && arenaSession.getCurrentIndex() > 0) {
            lastResult = arenaSession.getAnswers().get(arenaSession.getAnswers().size() - 1);
        }

        return GrammarArenaQuestionDto.builder()
                .sessionId(arenaSession.getId())
                .questionIndex(arenaSession.getCurrentIndex() + 1)
                .totalQuestions(arenaSession.getQuestionCount())
                .remainingSeconds(arenaSession.getRemainingSeconds(now))
                .prompt(q.getPrompt())
                .grammarTitle(q.getGrammarTitle())
                .choices(q.getChoices())
                .currentStreak(arenaSession.getCurrentStreak())
                .maxStreak(arenaSession.getMaxStreak())
                .lastResult(lastResult)
                .build();
    }

    public synchronized boolean submitArenaAnswer(Long choiceId, HttpSession session, String learnerId) {
        GrammarArenaSession arenaSession = getActiveArenaSession(session);
        if (arenaSession == null || !arenaSession.isRunning()) {
            return false;
        }

        Instant now = Instant.now();
        if (arenaSession.isExpired(now)) {
            finalizeArenaSession(session, TimedSessionStatus.EXPIRED, learnerId);
            return false;
        }

        int currIdx = arenaSession.getCurrentIndex();
        if (currIdx >= arenaSession.getQuestions().size()) {
            finalizeArenaSession(session, TimedSessionStatus.FINISHED, learnerId);
            return false;
        }

        GrammarArenaQuestionItem q = arenaSession.getQuestions().get(currIdx);
        String userChoiceText = "(Bỏ qua)";
        if (choiceId != null) {
            for (GrammarArenaChoiceItem c : q.getChoices()) {
                if (c.getChoiceId().equals(choiceId)) {
                    userChoiceText = c.getText();
                    break;
                }
            }
        }

        boolean isCorrect = (choiceId != null && choiceId.equals(q.getCorrectChoiceId()));

        // Cập nhật Streak
        int streak = arenaSession.getCurrentStreak();
        if (isCorrect) {
            streak++;
            arenaSession.setCurrentStreak(streak);
            if (streak > arenaSession.getMaxStreak()) {
                arenaSession.setMaxStreak(streak);
            }
        } else {
            arenaSession.setCurrentStreak(0);
        }

        GrammarArenaAnswerRecord rec = GrammarArenaAnswerRecord.builder()
                .questionIndex(currIdx + 1)
                .questionId(q.getQuestionId())
                .prompt(q.getPrompt())
                .userChoiceId(choiceId)
                .userChoiceText(userChoiceText)
                .correctChoiceId(q.getCorrectChoiceId())
                .correctChoiceText(q.getCorrectChoiceText())
                .correct(isCorrect)
                .explanation(q.getExplanation())
                .streakAfter(arenaSession.getCurrentStreak())
                .build();

        arenaSession.getAnswers().add(rec);
        arenaSession.setCurrentIndex(currIdx + 1);

        if (arenaSession.getCurrentIndex() >= arenaSession.getQuestions().size()) {
            finalizeArenaSession(session, TimedSessionStatus.FINISHED, learnerId);
        }

        return isCorrect;
    }

    public synchronized GrammarArenaResultDto finishArenaSession(HttpSession session, String learnerId) {
        return finalizeArenaSession(session, TimedSessionStatus.FINISHED, learnerId);
    }

    public synchronized GrammarArenaResultDto finalizeArenaSession(HttpSession session, TimedSessionStatus status, String learnerId) {
        if (session == null) {
            return null;
        }
        Object attr = session.getAttribute(GrammarArenaSession.SESSION_ATTR);
        if (!(attr instanceof GrammarArenaSession arenaSession)) {
            Object lastResult = session.getAttribute(SESSION_ARENA_LAST_RESULT);
            if (lastResult instanceof GrammarArenaResultDto res) {
                return res;
            }
            return null;
        }

        arenaSession.setStatus(status);
        Instant now = Instant.now();

        GrammarArenaResultDto result = GrammarArenaResultDto.builder()
                .sessionId(arenaSession.getId())
                .category(arenaSession.getCategory())
                .topicName(arenaSession.getTopicName())
                .totalQuestions(arenaSession.getQuestionCount())
                .answeredCount(arenaSession.getAnswers().size())
                .correctCount(arenaSession.getCorrectCount())
                .accuracyPercent(arenaSession.getAccuracyPercent())
                .maxStreak(arenaSession.getMaxStreak())
                .durationSeconds(arenaSession.getDurationSeconds(now))
                .status(status)
                .details(new ArrayList<>(arenaSession.getAnswers()))
                .build();

        // Ghi StudySession với mode TIMED_GRAMMAR (F49 / Prompt 16)
        if (learnerId != null && !learnerId.isBlank() && !arenaSession.getAnswers().isEmpty()) {
            List<StudyHistoryService.AnswerItem> historyAnswers = arenaSession.getAnswers().stream()
                    .map(a -> new StudyHistoryService.AnswerItem(null, null, a.isCorrect()))
                    .collect(Collectors.toList());
            studyHistoryService.saveSessionSafe(learnerId.trim(), "TIMED_GRAMMAR", arenaSession.getTopicId(),
                    historyAnswers, result.getDurationSeconds());
        }

        session.removeAttribute(GrammarArenaSession.SESSION_ATTR);
        session.setAttribute(SESSION_ARENA_LAST_RESULT, result);
        return result;
    }

    public GrammarArenaResultDto getLastArenaResult(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object lastResult = session.getAttribute(SESSION_ARENA_LAST_RESULT);
        if (lastResult instanceof GrammarArenaResultDto res) {
            return res;
        }
        return null;
    }
}
