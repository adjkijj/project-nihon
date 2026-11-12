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
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Dịch vụ luyện tập tính giờ (F42, F50, F17).
 * Quản lý vòng đời bài thi đếm ngược, kiểm soát hạn chót tại máy chủ,
 * tính chuỗi đúng liên tiếp (streak) và lưu vết tiến độ học tập.
 */
@Service
@Transactional(readOnly = true)
public class TimedPracticeService {

    public static final String SESSION_TIMED_PRACTICE = TimedSession.SESSION_TIMED_PRACTICE;

    private static final Set<Integer> ALLOWED_QUESTION_COUNTS = Set.of(10, 20, 30);
    private static final Set<Integer> ALLOWED_DURATIONS = Set.of(2, 3, 5, 10, 15);

    private final VocabularyRepository vocabularyRepository;
    private final KanjiEntryRepository kanjiEntryRepository;
    private final TopicRepository topicRepository;
    private final PracticeService practiceService;
    private final KanjiPracticeService kanjiPracticeService;
    private final StudyProgressService studyProgressService;
    private final StudyHistoryService studyHistoryService;

    public TimedPracticeService(VocabularyRepository vocabularyRepository,
                                KanjiEntryRepository kanjiEntryRepository,
                                TopicRepository topicRepository,
                                PracticeService practiceService,
                                KanjiPracticeService kanjiPracticeService,
                                StudyProgressService studyProgressService,
                                  StudyHistoryService studyHistoryService) {
        this.studyHistoryService = studyHistoryService;
        this.vocabularyRepository = vocabularyRepository;
        this.kanjiEntryRepository = kanjiEntryRepository;
        this.topicRepository = topicRepository;
        this.practiceService = practiceService;
        this.kanjiPracticeService = kanjiPracticeService;
        this.studyProgressService = studyProgressService;
    }

    /**
     * Lấy phiên luyện tập đang diễn ra trong HttpSession (nếu có và còn hiệu lực).
     * Nếu phiên đã quá thời gian, tự động đánh dấu EXPIRED và hoàn tất.
     */
    public TimedSession getActiveSession(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object attr = session.getAttribute(SESSION_TIMED_PRACTICE);
        if (!(attr instanceof TimedSession sessionData)) {
            return null;
        }

        if (sessionData.getStatus() == TimedSessionStatus.RUNNING) {
            Instant now = Instant.now();
            if (sessionData.isExpired(now)) {
                finalizeSession(sessionData, session, TimedSessionStatus.EXPIRED);
                return null;
            }
            return sessionData;
        }

        return null;
    }

    /**
     * Khởi tạo một phiên luyện tập tính giờ mới sau khi thẩm định cấu hình.
     */
    public synchronized TimedSession startSession(TimedStartForm form, HttpSession session) {
        if (form == null) {
            throw new IllegalArgumentException("Dữ liệu cấu hình không hợp lệ.");
        }

        // 1. Kiểm tra phiên đang chạy
        TimedSession currentActive = getActiveSession(session);
        if (currentActive != null && currentActive.isRunning()) {
            if (!Boolean.TRUE.equals(form.getConfirmCancelOld())) {
                throw new IllegalStateException("Đang có bài thi tính giờ đang diễn ra. Bạn cần xác nhận hủy trước khi bắt đầu bài mới.");
            }
        }

        // 2. Thiết lập cấu hình gói tốc độ (F17: Đấu trường tốc độ - 20 câu / 3 phút)
        boolean isSpeed = Boolean.TRUE.equals(form.getIsSpeedPreset());
        int questionCount;
        int durationMinutes;

        if (isSpeed) {
            questionCount = 20;
            durationMinutes = 3;
        } else {
            if (form.getQuestionCount() == null || !ALLOWED_QUESTION_COUNTS.contains(form.getQuestionCount())) {
                throw new IllegalArgumentException("Số lượng câu hỏi không hợp lệ. Vui lòng chọn 10, 20 hoặc 30 câu.");
            }
            if (form.getDurationMinutes() == null || !ALLOWED_DURATIONS.contains(form.getDurationMinutes())) {
                throw new IllegalArgumentException("Thời lượng không hợp lệ. Vui lòng chọn 2, 3, 5, 10 hoặc 15 phút.");
            }
            questionCount = form.getQuestionCount();
            durationMinutes = form.getDurationMinutes();
        }

        TimedPracticeType type = form.getType();
        if (type == null) {
            throw new IllegalArgumentException("Vui lòng chọn hình thức luyện tập.");
        }

        TimedEvaluationMode evaluationMode = form.getEvaluationMode() != null
                ? form.getEvaluationMode()
                : TimedEvaluationMode.INSTANT;

        // 3. Thẩm định chủ đề và lấy tên chủ đề
        final Long effectiveTopicId = (form.getTopicId() != null && form.getTopicId() > 0) ? form.getTopicId() : null;
        String topicName;
        if (effectiveTopicId != null) {
            Topic topic = topicRepository.findById(effectiveTopicId)
                    .orElseThrow(() -> new NoSuchElementException("Không tìm thấy chủ đề với mã: " + effectiveTopicId));
            topicName = topic.getName();
        } else {
            topicName = (type == TimedPracticeType.KANJI_READING) ? "Tất cả bài học Kanji" : "Tất cả chủ đề";
        }

        // 4. Sinh danh sách câu hỏi ngẫu nhiên theo chế độ
        List<TimedQuestionItem> questions = generateQuestions(type, effectiveTopicId, questionCount);

        // 5. Khởi tạo phiên
        Instant now = Instant.now();
        Instant deadline = now.plus(Duration.ofMinutes(durationMinutes));

        TimedSession timedSession = TimedSession.builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .topicId(effectiveTopicId)
                .topicName(topicName)
                .questionCount(questionCount)
                .durationMinutes(durationMinutes)
                .startedAt(now)
                .deadline(deadline)
                .evaluationMode(evaluationMode)
                .isSpeedPreset(isSpeed)
                .currentIndex(0)
                .questions(questions)
                .answers(new ArrayList<>())
                .status(TimedSessionStatus.RUNNING)
                .currentStreak(0)
                .maxStreak(0)
                .build();

        session.setAttribute(SESSION_TIMED_PRACTICE, timedSession);
        return timedSession;
    }

    /**
     * Lấy thông tin câu hỏi hiện tại cho máy khách.
     * Trả về null nếu phiên đã hết hạn hoặc đã hoàn thành (chuyển hướng sang kết quả).
     */
    public TimedQuestionDto getCurrentQuestion(HttpSession session) {
        if (session == null) {
            return null;
        }

        Object attr = session.getAttribute(SESSION_TIMED_PRACTICE);
        if (!(attr instanceof TimedSession sessionData)) {
            return null;
        }

        Instant now = Instant.now();
        if (sessionData.getStatus() == TimedSessionStatus.RUNNING) {
            if (sessionData.isExpired(now)) {
                finalizeSession(sessionData, session, TimedSessionStatus.EXPIRED);
                return null;
            }

            if (sessionData.getCurrentIndex() >= sessionData.getQuestions().size()) {
                finalizeSession(sessionData, session, TimedSessionStatus.FINISHED);
                return null;
            }

            TimedQuestionItem item = sessionData.getQuestions().get(sessionData.getCurrentIndex());

            TimedAnswerRecord lastResult = null;
            if (sessionData.getEvaluationMode() == TimedEvaluationMode.INSTANT
                    && !sessionData.getAnswers().isEmpty()
                    && sessionData.getCurrentIndex() > 0) {
                lastResult = sessionData.getAnswers().get(sessionData.getAnswers().size() - 1);
            }

            return TimedQuestionDto.builder()
                    .sessionId(sessionData.getId())
                    .questionIndex(sessionData.getCurrentIndex() + 1)
                    .totalQuestions(sessionData.getQuestionCount())
                    .remainingSeconds(sessionData.getRemainingSeconds(now))
                    .type(sessionData.getType())
                    .prompt(item.getPrompt())
                    .baseJapaneseText(item.getBaseJapaneseText())
                    .speechText(item.getSpeechText())
                    .hasKanji(item.isHasKanji())
                    .requireReading(item.isRequireReading())
                    .choices(item.getChoices())
                    .evaluationMode(sessionData.getEvaluationMode())
                    .currentStreak(sessionData.getCurrentStreak())
                    .isSpeedPreset(sessionData.isSpeedPreset())
                    .lastResult(lastResult)
                    .build();
        }

        return null;
    }

    /**
     * Chấm điểm câu trả lời từ người học và chuyển tiếp sang câu kế tiếp.
     * Sử dụng hoàn toàn các phương thức chấm điểm sẵn có từ PracticeService và KanjiPracticeService.
     */
    public synchronized TimedSession answerQuestion(TimedAnswerForm form, HttpSession session) {
        if (session == null) {
            throw new IllegalStateException("Phiên làm việc không tồn tại.");
        }

        Object attr = session.getAttribute(SESSION_TIMED_PRACTICE);
        if (!(attr instanceof TimedSession sessionData)) {
            throw new IllegalStateException("Không tìm thấy phiên làm bài hợp lệ.");
        }

        if (form == null || form.getSessionId() == null || !sessionData.getId().equals(form.getSessionId())) {
            throw new IllegalArgumentException("Mã phiên làm bài không khớp hoặc không hợp lệ.");
        }

        Instant now = Instant.now();

        // Kiểm tra thời gian: nếu đã quá hạn chót (+ 3 giây ân hạn) thì đánh dấu EXPIRED và kết thúc
        if (sessionData.isExpired(now)) {
            finalizeSession(sessionData, session, TimedSessionStatus.EXPIRED);
            return sessionData;
        }

        if (sessionData.getStatus() != TimedSessionStatus.RUNNING) {
            throw new IllegalStateException("Phiên làm bài đã kết thúc hoặc không ở trạng thái hoạt động.");
        }

        if (sessionData.getCurrentIndex() >= sessionData.getQuestions().size()) {
            finalizeSession(sessionData, session, TimedSessionStatus.FINISHED);
            return sessionData;
        }

        TimedQuestionItem item = sessionData.getQuestions().get(sessionData.getCurrentIndex());

        // Chấm điểm bằng các phương thức nghiệp vụ đã có
        boolean correct;
        String userAnswer;
        String expectedAnswer;
        String feedbackMessage;

        switch (sessionData.getType()) {
            case JA_VI -> {
                PracticeAnswerForm pForm = new PracticeAnswerForm();
                pForm.setVocabId(item.getVocabId());
                pForm.setUserReading(form.getUserReading());
                pForm.setUserMeaning(form.getUserMeaning() != null ? form.getUserMeaning() : form.getAnswer());
                pForm.setTopicId(sessionData.getTopicId());

                PracticeResultDto pResult = practiceService.checkAnswer(pForm);
                correct = pResult.isOverallCorrect();
                expectedAnswer = (pResult.isRequireReading() ? "Cách đọc: " + pResult.getExpectedReading() + " | " : "")
                        + "Nghĩa: " + pResult.getExpectedMeaning();
                userAnswer = (pResult.isRequireReading() ? "Cách đọc: " + (form.getUserReading() != null ? form.getUserReading().trim() : "") + " | " : "")
                        + "Nghĩa: " + (form.getUserMeaning() != null ? form.getUserMeaning().trim() : (form.getAnswer() != null ? form.getAnswer().trim() : ""));
                feedbackMessage = pResult.getFeedbackMessage();
            }
            case VI_JA -> {
                PracticeViJaAnswerForm pForm = new PracticeViJaAnswerForm();
                pForm.setVocabId(item.getVocabId());
                pForm.setUserJapanese(form.getUserJapanese() != null ? form.getUserJapanese() : form.getAnswer());
                pForm.setTopicId(sessionData.getTopicId());

                PracticeViJaResultDto pResult = practiceService.checkReverseAnswer(pForm);
                correct = pResult.isCorrect();
                expectedAnswer = pResult.getExpectedJapanese()
                        + (pResult.getExpectedReading() != null && !pResult.getExpectedReading().isBlank() ? " (" + pResult.getExpectedReading().strip() + ")" : "");
                userAnswer = form.getUserJapanese() != null ? form.getUserJapanese().trim() : (form.getAnswer() != null ? form.getAnswer().trim() : "");
                feedbackMessage = pResult.getFeedbackMessage();
            }
            case CHOICE -> {
                Long choiceId = form.getChoiceVocabId();
                if (choiceId == null && form.getAnswer() != null && !form.getAnswer().isBlank()) {
                    try {
                        choiceId = Long.parseLong(form.getAnswer().trim());
                    } catch (NumberFormatException ignored) {}
                }

                PracticeChoiceAnswerForm pForm = PracticeChoiceAnswerForm.builder()
                        .vocabId(item.getVocabId())
                        .choiceVocabId(choiceId != null ? choiceId : -1L)
                        .direction("JA_VI")
                        .topicId(sessionData.getTopicId())
                        .build();

                List<Long> validOptionIds = item.getChoices() != null
                        ? item.getChoices().stream().map(PracticeChoiceOptionDto::getVocabId).toList()
                        : Collections.emptyList();

                try {
                    PracticeChoiceResultDto pResult = practiceService.checkChoiceAnswer(pForm, validOptionIds);
                    correct = pResult.isCorrect();
                    expectedAnswer = pResult.getCorrectAnswerText();
                    userAnswer = pResult.getSelectedAnswerText();
                    feedbackMessage = pResult.getFeedbackMessage();
                } catch (IllegalArgumentException | NoSuchElementException ex) {
                    correct = false;
                    expectedAnswer = item.getMeaningVi();
                    userAnswer = "(Lựa chọn không hợp lệ)";
                    feedbackMessage = "Lựa chọn không hợp lệ hoặc đã xảy ra lỗi.";
                }
            }
            case KANJI_READING -> {
                PracticeKanjiAnswerForm pForm = PracticeKanjiAnswerForm.builder()
                        .kanjiId(item.getKanjiId())
                        .answer(form.getKanjiAnswer() != null ? form.getKanjiAnswer() : form.getAnswer())
                        .mode(KanjiPracticeMode.KANJI_TO_READING.name())
                        .topicId(sessionData.getTopicId())
                        .build();

                PracticeKanjiResultDto pResult = kanjiPracticeService.checkAnswer(pForm, session);
                correct = pResult.isCorrect();
                expectedAnswer = pResult.getExpectedAnswer();
                userAnswer = pResult.getUserAnswer();
                feedbackMessage = pResult.getFeedbackMessage();
            }
            default -> throw new IllegalStateException("Hình thức luyện tập không được hỗ trợ: " + sessionData.getType());
        }

        // Cập nhật chuỗi làm đúng liên tiếp (streak)
        if (correct) {
            sessionData.setCurrentStreak(sessionData.getCurrentStreak() + 1);
            if (sessionData.getCurrentStreak() > sessionData.getMaxStreak()) {
                sessionData.setMaxStreak(sessionData.getCurrentStreak());
            }
        } else {
            sessionData.setCurrentStreak(0);
        }

        // Lưu bản ghi câu trả lời
        TimedAnswerRecord record = TimedAnswerRecord.builder()
                .questionIndex(sessionData.getCurrentIndex() + 1)
                .vocabId(item.getVocabId())
                .kanjiId(item.getKanjiId())
                .prompt(item.getPrompt())
                .userAnswer(userAnswer)
                .expectedAnswer(expectedAnswer)
                .correct(correct)
                .feedbackMessage(feedbackMessage)
                .answeredAt(now)
                .build();
        sessionData.getAnswers().add(record);

        // Chuyển sang câu tiếp theo
        sessionData.setCurrentIndex(sessionData.getCurrentIndex() + 1);

        if (sessionData.getCurrentIndex() >= sessionData.getQuestions().size()) {
            finalizeSession(sessionData, session, TimedSessionStatus.FINISHED);
        } else {
            session.setAttribute(SESSION_TIMED_PRACTICE, sessionData);
        }

        return sessionData;
    }

    /**
     * Hoàn tất phiên luyện tập tính giờ (do hết giờ, nộp bài sớm hoặc đã trả lời xong).
     * Các câu chưa kịp làm sẽ bị tính là sai với câu trả lời "(Chưa trả lời)".
     * Tự động đồng bộ lịch sử vào StudyProgressService.
     */
    public synchronized TimedSession finalizeSession(TimedSession sessionData, HttpSession session, TimedSessionStatus finalStatus) {
        if (sessionData == null || session == null) {
            return sessionData;
        }

        if (sessionData.getStatus() == TimedSessionStatus.RUNNING) {
            sessionData.setStatus(finalStatus);

            // Xử lý các câu hỏi chưa được trả lời
            for (int i = sessionData.getCurrentIndex(); i < sessionData.getQuestions().size(); i++) {
                TimedQuestionItem unansItem = sessionData.getQuestions().get(i);
                String expected = resolveExpectedAnswer(unansItem, sessionData.getType());

                TimedAnswerRecord unansRecord = TimedAnswerRecord.builder()
                        .questionIndex(i + 1)
                        .vocabId(unansItem.getVocabId())
                        .kanjiId(unansItem.getKanjiId())
                        .prompt(unansItem.getPrompt())
                        .userAnswer("(Chưa trả lời)")
                        .expectedAnswer(expected)
                        .correct(false)
                        .feedbackMessage("Chưa hoàn thành do hết thời gian hoặc nộp bài sớm.")
                        .answeredAt(Instant.now())
                        .build();
                sessionData.getAnswers().add(unansRecord);
            }

            // Ghi nhận toàn bộ kết quả vào StudyProgressService
            String timedMode = "TIMED_" + sessionData.getType().name();
            for (TimedAnswerRecord r : sessionData.getAnswers()) {
                studyProgressService.recordTimedAttempt(
                        session,
                        r.getVocabId(),
                        r.getKanjiId(),
                        sessionData.getTopicId(),
                        sessionData.getTopicName(),
                        timedMode,
                        r.getPrompt(),
                        r.getUserAnswer(),
                        r.getExpectedAnswer(),
                        r.isCorrect(),
                        r.getFeedbackMessage()
                );
            }

            // Lưu lịch sử bền vững đúng một lần (chỉ khi trình duyệt có cookie học viên hợp lệ)
            saveTimedHistory(sessionData);

            session.setAttribute(SESSION_TIMED_PRACTICE, sessionData);
        }

        return sessionData;
    }

    private void saveTimedHistory(TimedSession sessionData) {
        try {
            java.util.Optional<String> learner = com.nihon.history.LearnerContext.current();
            if (learner.isEmpty() || sessionData.getAnswers().isEmpty()) {
                return;
            }
            java.util.List<StudyHistoryService.AnswerItem> items = sessionData.getAnswers().stream()
                    .map(r -> new StudyHistoryService.AnswerItem(r.getVocabId(), r.getKanjiId(), r.isCorrect()))
                    .toList();
            long elapsed = sessionData.getStartedAt() == null ? 0
                    : Math.max(0, Instant.now().getEpochSecond() - sessionData.getStartedAt().getEpochSecond());
            long cap = sessionData.getDurationMinutes() * 60L;
            int duration = (int) (cap > 0 ? Math.min(elapsed, cap) : elapsed);
            studyHistoryService.saveSessionSafe(learner.get(), "TIMED_" + sessionData.getType().name(),
                    sessionData.getTopicId(), items, duration);
        } catch (RuntimeException ignored) {
            // lịch sử là tính năng phụ, không được làm hỏng luồng luyện tập
        }
    }

    /**
     * Lấy dữ liệu tổng kết kết quả phiên luyện tập tính giờ.
     */
    public TimedResultDto getResult(HttpSession session) {
        if (session == null) {
            return null;
        }

        Object attr = session.getAttribute(SESSION_TIMED_PRACTICE);
        if (!(attr instanceof TimedSession sessionData)) {
            return null;
        }

        if (sessionData.getStatus() == TimedSessionStatus.RUNNING) {
            Instant now = Instant.now();
            TimedSessionStatus finalStatus = sessionData.isExpired(now)
                    ? TimedSessionStatus.EXPIRED
                    : TimedSessionStatus.FINISHED;
            finalizeSession(sessionData, session, finalStatus);
        }

        int totalQuestions = sessionData.getQuestionCount();
        int correctCount = (int) sessionData.getAnswers().stream().filter(TimedAnswerRecord::isCorrect).count();
        int answeredCount = (int) sessionData.getAnswers().stream().filter(a -> !"(Chưa trả lời)".equals(a.getUserAnswer())).count();
        int incorrectCount = totalQuestions - correctCount;
        int accuracyPercentage = totalQuestions > 0 ? (int) Math.round((double) correctCount * 100.0 / totalQuestions) : 0;

        long durationTotalSeconds = sessionData.getDurationMinutes() * 60L;
        long timeSpentSeconds;
        if (sessionData.getStatus() == TimedSessionStatus.EXPIRED) {
            timeSpentSeconds = durationTotalSeconds;
        } else {
            long spent = Duration.between(sessionData.getStartedAt(), Instant.now()).toSeconds();
            timeSpentSeconds = Math.min(durationTotalSeconds, Math.max(0, spent));
        }

        String timeSpentFormatted = String.format("%02d:%02d", timeSpentSeconds / 60, timeSpentSeconds % 60);

        return TimedResultDto.builder()
                .sessionId(sessionData.getId())
                .type(sessionData.getType())
                .typeName(sessionData.getType().getDisplayName())
                .topicId(sessionData.getTopicId())
                .topicName(sessionData.getTopicName())
                .totalQuestions(totalQuestions)
                .answeredCount(answeredCount)
                .correctCount(correctCount)
                .incorrectCount(incorrectCount)
                .accuracyPercentage(accuracyPercentage)
                .timeSpentSeconds(timeSpentSeconds)
                .timeSpentFormatted(timeSpentFormatted)
                .totalTimeSeconds(durationTotalSeconds)
                .maxStreak(sessionData.getMaxStreak())
                .status(sessionData.getStatus())
                .isSpeedPreset(sessionData.isSpeedPreset())
                .records(sessionData.getAnswers())
                .build();
    }

    /**
     * Hủy bỏ phiên luyện tập tính giờ hiện tại.
     */
    public void cancelSession(HttpSession session) {
        if (session != null) {
            session.removeAttribute(SESSION_TIMED_PRACTICE);
        }
    }

    /**
     * Sinh danh sách câu hỏi ngẫu nhiên không lặp lại cho bài thi tính giờ.
     */
    private List<TimedQuestionItem> generateQuestions(TimedPracticeType type, Long topicId, int questionCount) {
        if (type == TimedPracticeType.KANJI_READING) {
            List<KanjiEntry> kanjis = (topicId != null && topicId > 0)
                    ? kanjiEntryRepository.findByTopicIdOrderBySourceLineAsc(topicId)
                    : kanjiEntryRepository.findAll();

            List<KanjiEntry> eligible = kanjis.stream()
                    .filter(k -> k != null && k.getId() != null
                            && k.getCharacter() != null && !k.getCharacter().isBlank()
                            && !kanjiPracticeService.extractValidReadings(k).isEmpty())
                    .collect(Collectors.toCollection(ArrayList::new));

            if (eligible.size() < questionCount) {
                throw new IllegalArgumentException("Không đủ dữ liệu Kanji trong bài học đã chọn (yêu cầu tối thiểu "
                        + questionCount + " chữ Hán, hiện chỉ có " + eligible.size() + " chữ).");
            }

            Collections.shuffle(eligible);
            List<KanjiEntry> selected = eligible.subList(0, questionCount);

            List<TimedQuestionItem> items = new ArrayList<>();
            for (int i = 0; i < selected.size(); i++) {
                KanjiEntry k = selected.get(i);
                List<String> readings = kanjiPracticeService.extractValidReadings(k);
                items.add(TimedQuestionItem.builder()
                        .index(i)
                        .kanjiId(k.getId())
                        .prompt(k.getCharacter())
                        .speechText(k.getCharacter())
                        .reading(String.join(", ", readings))
                        .meaningVi(k.getMeaningVi())
                        .build());
            }
            return items;
        }

        // Các chế độ liên quan đến Vocabulary: JA_VI, VI_JA, CHOICE
        List<Vocabulary> vocabs = (topicId != null && topicId > 0)
                ? vocabularyRepository.findByTopicIdOrderBySourceLineAsc(topicId)
                : vocabularyRepository.findAll();

        List<Vocabulary> eligible = vocabs.stream()
                .filter(v -> v != null && v.getId() != null
                        && v.getJapaneseText() != null && !v.getJapaneseText().isBlank()
                        && v.getMeaningVi() != null && !v.getMeaningVi().isBlank())
                .collect(Collectors.toCollection(ArrayList::new));

        if (eligible.size() < questionCount) {
            throw new IllegalArgumentException("Không đủ dữ liệu câu hỏi trong chủ đề đã chọn (yêu cầu tối thiểu "
                    + questionCount + " câu, hiện chỉ có " + eligible.size() + " câu).");
        }

        Collections.shuffle(eligible);
        List<Vocabulary> selected = eligible.subList(0, questionCount);

        List<TimedQuestionItem> items = new ArrayList<>();

        if (type == TimedPracticeType.CHOICE) {
            // Chuẩn bị kho đáp án nhiễu (kết hợp từ chủ đề và toàn bộ hệ thống nếu cần)
            List<Vocabulary> allVocabs = vocabularyRepository.findAll().stream()
                    .filter(v -> v != null && v.getId() != null
                            && v.getMeaningVi() != null && !v.getMeaningVi().isBlank())
                    .toList();

            if (allVocabs.size() < 4) {
                throw new IllegalArgumentException("Hệ thống chưa đủ từ vựng để tạo câu hỏi trắc nghiệm (cần tối thiểu 4 từ).");
            }

            for (int i = 0; i < selected.size(); i++) {
                Vocabulary target = selected.get(i);
                List<PracticeChoiceOptionDto> options = generateChoiceOptions(target, vocabs, allVocabs);

                items.add(TimedQuestionItem.builder()
                        .index(i)
                        .vocabId(target.getId())
                        .prompt(target.getJapaneseText())
                        .baseJapaneseText(FuriganaHelper.extractBaseText(target.getJapaneseText()))
                        .reading(target.getReading())
                        .meaningVi(target.getMeaningVi())
                        .speechText(SpeechHelper.resolveSpeechText(target.getJapaneseText(), target.getReading()))
                        .hasKanji(target.isHasKanji())
                        .requireReading(target.isHasKanji() && target.getReading() != null && !target.getReading().isBlank())
                        .choices(options)
                        .build());
            }
            return items;
        }

        for (int i = 0; i < selected.size(); i++) {
            Vocabulary v = selected.get(i);
            String prompt = (type == TimedPracticeType.VI_JA) ? v.getMeaningVi() : v.getJapaneseText();

            items.add(TimedQuestionItem.builder()
                    .index(i)
                    .vocabId(v.getId())
                    .prompt(prompt)
                    .baseJapaneseText(FuriganaHelper.extractBaseText(v.getJapaneseText()))
                    .reading(v.getReading())
                    .meaningVi(v.getMeaningVi())
                    .speechText(SpeechHelper.resolveSpeechText(v.getJapaneseText(), v.getReading()))
                    .hasKanji(v.isHasKanji())
                    .requireReading(v.isHasKanji() && v.getReading() != null && !v.getReading().isBlank())
                    .build());
        }

        return items;
    }

    private List<PracticeChoiceOptionDto> generateChoiceOptions(Vocabulary target, List<Vocabulary> topicPool, List<Vocabulary> allPool) {
        List<PracticeChoiceOptionDto> options = new ArrayList<>();

        // Đáp án đúng
        options.add(PracticeChoiceOptionDto.builder()
                .vocabId(target.getId())
                .displayText(target.getMeaningVi())
                .baseJapaneseText(FuriganaHelper.extractBaseText(target.getJapaneseText()))
                .reading(target.getReading())
                .romaji(target.getRomaji())
                .speechText(SpeechHelper.resolveSpeechText(target.getJapaneseText(), target.getReading()))
                .build());

        // Tìm 3 đáp án nhiễu có nghĩa khác biệt
        List<Vocabulary> candidateDistractors = new ArrayList<>(topicPool);
        Collections.shuffle(candidateDistractors);

        List<Vocabulary> chosenDistractors = new ArrayList<>();
        for (Vocabulary cand : candidateDistractors) {
            if (!cand.getId().equals(target.getId())
                    && !cand.getMeaningVi().strip().equalsIgnoreCase(target.getMeaningVi().strip())
                    && chosenDistractors.stream().noneMatch(d -> d.getMeaningVi().strip().equalsIgnoreCase(cand.getMeaningVi().strip()))) {
                chosenDistractors.add(cand);
                if (chosenDistractors.size() == 3) {
                    break;
                }
            }
        }

        if (chosenDistractors.size() < 3) {
            List<Vocabulary> extraCandidates = new ArrayList<>(allPool);
            Collections.shuffle(extraCandidates);
            for (Vocabulary cand : extraCandidates) {
                if (!cand.getId().equals(target.getId())
                        && !cand.getMeaningVi().strip().equalsIgnoreCase(target.getMeaningVi().strip())
                        && chosenDistractors.stream().noneMatch(d -> d.getMeaningVi().strip().equalsIgnoreCase(cand.getMeaningVi().strip()))) {
                    chosenDistractors.add(cand);
                    if (chosenDistractors.size() == 3) {
                        break;
                    }
                }
            }
        }

        for (Vocabulary d : chosenDistractors) {
            options.add(PracticeChoiceOptionDto.builder()
                    .vocabId(d.getId())
                    .displayText(d.getMeaningVi())
                    .baseJapaneseText(FuriganaHelper.extractBaseText(d.getJapaneseText()))
                    .reading(d.getReading())
                    .romaji(d.getRomaji())
                    .speechText(SpeechHelper.resolveSpeechText(d.getJapaneseText(), d.getReading()))
                    .build());
        }

        Collections.shuffle(options);
        return options;
    }

    private String resolveExpectedAnswer(TimedQuestionItem item, TimedPracticeType type) {
        if (item == null) {
            return "";
        }
        return switch (type) {
            case JA_VI -> (item.isRequireReading() ? "Cách đọc: " + item.getReading() + " | " : "")
                    + "Nghĩa: " + item.getMeaningVi();
            case VI_JA -> (item.getBaseJapaneseText() != null ? item.getBaseJapaneseText() : "")
                    + (item.getReading() != null && !item.getReading().isBlank() ? " (" + item.getReading().strip() + ")" : "");
            case CHOICE -> item.getMeaningVi();
            case KANJI_READING -> item.getReading();
        };
    }
}
