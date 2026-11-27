package com.nihon.service;

import com.nihon.entity.KanjiEntry;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.repository.KanjiEntryRepository;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VocabularyRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Dịch vụ tạo và chấm bài kiểm tra cuối bài theo chủ đề (F40).
 * Sinh ngẫu nhiên từ 10 đến 20 câu hỏi trắc nghiệm từ kho Từ vựng và Kanji của chủ đề.
 * Kết quả được ghi bền vững vào StudySession với mode = TOPIC_TEST.
 */
@Service
public class TopicTestService {

    public static final String SESSION_ATTR_TOPIC_TEST = "ACTIVE_TOPIC_TEST_SESSION";

    private final TopicRepository topicRepository;
    private final VocabularyRepository vocabularyRepository;
    private final KanjiEntryRepository kanjiEntryRepository;
    private final StudyHistoryService historyService;

    public TopicTestService(TopicRepository topicRepository,
                            VocabularyRepository vocabularyRepository,
                            KanjiEntryRepository kanjiEntryRepository,
                            StudyHistoryService historyService) {
        this.topicRepository = topicRepository;
        this.vocabularyRepository = vocabularyRepository;
        this.kanjiEntryRepository = kanjiEntryRepository;
        this.historyService = historyService;
    }

    /**
     * DTO Một câu hỏi trắc nghiệm trong bài kiểm tra cuối bài.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopicTestQuestion implements Serializable {
        private static final long serialVersionUID = 1L;

        private int index; // 1-based
        private Long vocabId;
        private Long kanjiId;
        private String itemType; // VOCAB hoặc KANJI
        private String prompt;
        private String promptSub;
        private List<String> choices;
        private int correctChoiceIndex; // 0..3
        private String explanation;
    }

    /**
     * DTO Phiên làm bài kiểm tra cuối bài.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopicTestSession implements Serializable {
        private static final long serialVersionUID = 1L;

        private String sessionId;
        private Long topicId;
        private String topicName;
        private Instant startedAt;
        private Instant submittedAt;
        private int durationSeconds;

        @Builder.Default
        private List<TopicTestQuestion> questions = new ArrayList<>();

        @Builder.Default
        private Map<Integer, Integer> userAnswers = new HashMap<>(); // index -> selectedChoiceIndex

        @Builder.Default
        private Map<Integer, Boolean> results = new HashMap<>(); // index -> correct

        private int correctCount;
        private int totalQuestions;
        private double accuracyPercent;
        private boolean completed;
    }

    /**
     * Tạo bài kiểm tra trắc nghiệm 10-20 câu từ từ vựng và Kanji của chủ đề.
     */
    public TopicTestSession createTest(Long topicId) {
        if (topicId == null || topicId <= 0) {
            throw new IllegalArgumentException("Mã chủ đề không hợp lệ.");
        }

        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy chủ đề với ID: " + topicId));

        List<Vocabulary> vocabList = vocabularyRepository.findByTopicIdOrderBySourceLineAsc(topicId);
        List<KanjiEntry> kanjiList = kanjiEntryRepository.findByTopicIdOrderBySourceLineAsc(topicId);

        int totalAvailable = vocabList.size() + kanjiList.size();
        if (totalAvailable < 4) {
            throw new IllegalStateException("Chủ đề '" + topic.getName() + "' cần có ít nhất 4 từ vựng hoặc Kanji để tạo bài kiểm tra.");
        }

        int questionCount = Math.min(20, Math.max(10, totalAvailable));
        if (totalAvailable < 10) {
            questionCount = totalAvailable;
        }

        // Tạo danh sách pool các ứng viên (Vocab và Kanji)
        List<TestCandidate> candidates = new ArrayList<>();
        for (Vocabulary v : vocabList) {
            String meaning = v.getMeaningVi() != null ? v.getMeaningVi() : "";
            candidates.add(new TestCandidate(v.getId(), null, "VOCAB", v.getJapaneseText(), v.getReading(), meaning));
        }
        for (KanjiEntry k : kanjiList) {
            String meaningDisplay = k.getHanViet() != null ? k.getHanViet() : "";
            if (k.getMeaningVi() != null && !k.getMeaningVi().isBlank()) {
                meaningDisplay += (meaningDisplay.isEmpty() ? "" : " - ") + k.getMeaningVi();
            }
            candidates.add(new TestCandidate(null, k.getId(), "KANJI", k.getCharacter(), k.getOnyomi() + " / " + k.getKunyomi(), meaningDisplay));
        }

        Collections.shuffle(candidates);
        List<TestCandidate> selected = candidates.subList(0, Math.min(questionCount, candidates.size()));

        List<TopicTestQuestion> questions = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < selected.size(); i++) {
            TestCandidate current = selected.get(i);
            String correctAnswer = current.meaning();

            // Tìm 3 phương án nhiễu từ các ứng viên khác
            List<String> distractors = new ArrayList<>();
            List<TestCandidate> otherPool = new ArrayList<>(candidates);
            otherPool.remove(current);
            Collections.shuffle(otherPool);

            for (TestCandidate other : otherPool) {
                if (!other.meaning().equalsIgnoreCase(correctAnswer) && !distractors.contains(other.meaning())) {
                    distractors.add(other.meaning());
                    if (distractors.size() >= 3) {
                        break;
                    }
                }
            }

            // Bổ sung phương án mẫu nếu kho chưa đủ 3 nhiễu
            String[] fallbacks = {"Học tập", "Công việc", "Gia đình", "Thời gian", "Du lịch", "Món ăn", "Bạn bè"};
            int fbIdx = 0;
            while (distractors.size() < 3 && fbIdx < fallbacks.length) {
                if (!distractors.contains(fallbacks[fbIdx]) && !correctAnswer.equalsIgnoreCase(fallbacks[fbIdx])) {
                    distractors.add(fallbacks[fbIdx]);
                }
                fbIdx++;
            }

            List<String> choices = new ArrayList<>(distractors);
            int correctIndex = random.nextInt(choices.size() + 1);
            choices.add(correctIndex, correctAnswer);

            questions.add(TopicTestQuestion.builder()
                    .index(i + 1)
                    .vocabId(current.vocabId())
                    .kanjiId(current.kanjiId())
                    .itemType(current.type())
                    .prompt(current.prompt())
                    .promptSub(current.promptSub())
                    .choices(choices)
                    .correctChoiceIndex(correctIndex)
                    .explanation("Đáp án đúng: " + correctAnswer)
                    .build());
        }

        return TopicTestSession.builder()
                .sessionId(UUID.randomUUID().toString())
                .topicId(topic.getId())
                .topicName(topic.getName())
                .startedAt(Instant.now())
                .questions(questions)
                .totalQuestions(questions.size())
                .completed(false)
                .build();
    }

    /**
     * Chấm bài kiểm tra cuối bài và ghi vào StudyHistoryService (bảng study_session, mode = TOPIC_TEST).
     */
    public TopicTestSession submitTest(TopicTestSession testSession, Map<Integer, Integer> userAnswers, String learnerId) {
        if (testSession == null) {
            throw new IllegalArgumentException("Phiên kiểm tra không tồn tại.");
        }

        Instant now = Instant.now();
        testSession.setSubmittedAt(now);
        int durationSeconds = (int) Math.max(1, Duration.between(testSession.getStartedAt(), now).toSeconds());
        testSession.setDurationSeconds(durationSeconds);

        if (userAnswers == null) {
            userAnswers = new HashMap<>();
        }
        testSession.setUserAnswers(userAnswers);

        int correctCount = 0;
        List<StudyHistoryService.AnswerItem> answerItems = new ArrayList<>();

        for (TopicTestQuestion q : testSession.getQuestions()) {
            Integer chosen = userAnswers.get(q.getIndex());
            boolean isCorrect = (chosen != null && chosen == q.getCorrectChoiceIndex());

            testSession.getResults().put(q.getIndex(), isCorrect);
            if (isCorrect) {
                correctCount++;
            }

            answerItems.add(new StudyHistoryService.AnswerItem(q.getVocabId(), q.getKanjiId(), isCorrect));
        }

        testSession.setCorrectCount(correctCount);
        double accuracy = testSession.getTotalQuestions() > 0
                ? Math.round((correctCount * 100.0 / testSession.getTotalQuestions()) * 10.0) / 10.0
                : 0.0;
        testSession.setAccuracyPercent(accuracy);
        testSession.setCompleted(true);

        // Lưu bền vững vào CSDL qua StudyHistoryService với mode = TOPIC_TEST
        if (learnerId != null && !learnerId.isBlank()) {
            historyService.saveSessionSafe(learnerId, "TOPIC_TEST", testSession.getTopicId(), answerItems, durationSeconds);
        }

        return testSession;
    }

    private record TestCandidate(Long vocabId, Long kanjiId, String type, String prompt, String promptSub, String meaning) {
    }
}

