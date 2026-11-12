package com.nihon.service;

import com.nihon.dto.PracticeRecordDto;
import com.nihon.entity.StudyAnswer;
import com.nihon.entity.StudySession;
import com.nihon.history.HistoryViews.PageResult;
import com.nihon.history.HistoryViews.SessionView;
import com.nihon.history.HistoryViews.Summary;
import com.nihon.history.HistoryViews.WeekBar;
import com.nihon.repository.StudyAnswerRepository;
import com.nihon.repository.StudySessionRepository;
import com.nihon.repository.TopicRepository;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lịch sử học tập bền vững theo người học ẩn danh. Mọi truy vấn đều lọc theo learnerId lấy từ cookie
 * (không bao giờ từ URL/form). Lỗi lưu lịch sử không bao giờ làm hỏng luồng học chính.
 */
@Service
public class StudyHistoryService {

    private static final Logger log = LoggerFactory.getLogger(StudyHistoryService.class);

    public static final int PAGE_SIZE = 20;
    public static final int WEEKS = 8;
    public static final Set<String> MODES = Set.of("ALL", "JA_VI", "VI_JA", "MC_JA_VI", "MC_VI_JA",
            "KANJI_READING", "KANJI_WRITE", "KANJI_CHOICE",
            "TIMED_JA_VI", "TIMED_VI_JA", "TIMED_KANJI_READING", "TIMED_CHOICE");
    public static final Set<String> RANGES = Set.of("7D", "30D", "ALL");
    private static final LocalDateTime EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final int MAX_DURATION_SECONDS = 6 * 3600;
    private static final int MAX_ANSWERS = 500;

    private final StudySessionRepository sessionRepository;
    private final StudyAnswerRepository answerRepository;
    private final TopicRepository topicRepository;
    private final StudyProgressService studyProgressService;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int retentionDays;

    public StudyHistoryService(StudySessionRepository sessionRepository,
                               StudyAnswerRepository answerRepository,
                               TopicRepository topicRepository,
                               StudyProgressService studyProgressService,
                               PlatformTransactionManager txManager,
                               Clock clock,
                               @Value("${app.history.retention-days:180}") int retentionDays) {
        this.sessionRepository = sessionRepository;
        this.answerRepository = answerRepository;
        this.topicRepository = topicRepository;
        this.studyProgressService = studyProgressService;
        this.tx = new TransactionTemplate(txManager);
        // REQUIRES_NEW: có thể được gọi từ giao dịch readOnly của TimedPracticeService
        this.tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
        this.retentionDays = retentionDays;
    }

    // ---------- whitelists ----------

    public static String normalizeMode(String raw) {
        if (raw == null) {
            return "ALL";
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        return MODES.contains(u) ? u : "ALL";
    }

    public static String normalizeRange(String raw) {
        if (raw == null) {
            return "ALL";
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        return RANGES.contains(u) ? u : "ALL";
    }

    /** Trang 1-based; giá trị rác hoặc quá lớn đều an toàn. */
    public static int parsePage(String raw) {
        if (raw == null) {
            return 1;
        }
        try {
            long v = Long.parseLong(raw.trim());
            if (v < 1) {
                return 1;
            }
            return (int) Math.min(v, 1_000_000L);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public static String modeLabel(String mode) {
        return switch (mode) {
            case "JA_VI" -> "Nhật → Việt";
            case "VI_JA" -> "Việt → Nhật";
            case "MC_JA_VI" -> "Trắc nghiệm Nhật → Việt";
            case "MC_VI_JA" -> "Trắc nghiệm Việt → Nhật";
            case "KANJI_READING" -> "Kanji: cách đọc";
            case "KANJI_WRITE" -> "Kanji: viết";
            case "KANJI_CHOICE" -> "Kanji: trắc nghiệm";
            case "TIMED_JA_VI" -> "Tính giờ: Nhật → Việt";
            case "TIMED_VI_JA" -> "Tính giờ: Việt → Nhật";
            case "TIMED_KANJI_READING" -> "Tính giờ: Đọc Kanji";
            case "TIMED_CHOICE" -> "Tính giờ: Trắc nghiệm";
            default -> mode;
        };
    }

    // ---------- writes ----------

    public record AnswerItem(Long vocabId, Long kanjiId, boolean correct) {
    }

    /** Lưu một phiên; không bao giờ ném ngoại lệ. Trả true nếu đã lưu. */
    public boolean saveSessionSafe(String learnerId, String mode, Long topicId, List<AnswerItem> answers,
                                   int durationSeconds) {
        try {
            if (LearnerIdOk(learnerId) == null || answers == null || answers.isEmpty()) {
                return false;
            }
            String safeMode = mode == null ? "" : mode.toUpperCase(Locale.ROOT);
            if (!MODES.contains(safeMode) || "ALL".equals(safeMode)) {
                return false;
            }
            List<AnswerItem> items = answers.size() > MAX_ANSWERS ? answers.subList(0, MAX_ANSWERS) : answers;
            Long safeTopic = (topicId != null && topicRepository.existsById(topicId)) ? topicId : null;
            StudySession s = new StudySession();
            s.setLearnerId(learnerId);
            s.setMode(safeMode);
            s.setTopicId(safeTopic);
            s.setTotalQuestions(items.size());
            s.setCorrectCount((int) items.stream().filter(AnswerItem::correct).count());
            s.setDurationSeconds(Math.max(0, Math.min(durationSeconds, MAX_DURATION_SECONDS)));
            s.setCompletedAt(LocalDateTime.now(clock));
            tx.executeWithoutResult(status -> {
                StudySession saved = sessionRepository.save(s);
                List<StudyAnswer> rows = new ArrayList<>(items.size());
                for (AnswerItem a : items) {
                    StudyAnswer r = new StudyAnswer();
                    r.setSession(saved);
                    r.setVocabId(a.vocabId());
                    r.setKanjiId(a.kanjiId());
                    r.setCorrect(a.correct());
                    rows.add(r);
                }
                answerRepository.saveAll(rows);
            });
            return true;
        } catch (RuntimeException e) {
            log.warn("Could not save study history: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    private static String LearnerIdOk(String id) {
        return com.nihon.history.LearnerContext.normalizeUuid(id);
    }

    /**
     * Lưu bản ghi trong HttpSession (trừ TIMED_*, đã tự lưu khi kết thúc) thành các phiên theo mode
     * rồi xoá chúng khỏi session. Chỉ làm khi có bản ghi. Trả số phiên đã lưu.
     */
    public int endSession(String learnerId, HttpSession session) {
        if (LearnerIdOk(learnerId) == null || session == null) {
            return 0;
        }
        List<PracticeRecordDto> records = new ArrayList<>(studyProgressService.getHistory(session));
        if (records.isEmpty()) {
            return 0;
        }
        Map<String, List<PracticeRecordDto>> byMode = new LinkedHashMap<>();
        for (PracticeRecordDto r : records) {
            if (r.getMode() == null || r.getMode().startsWith("TIMED_")) {
                continue;
            }
            byMode.computeIfAbsent(r.getMode(), k -> new ArrayList<>()).add(r);
        }
        int saved = 0;
        for (Map.Entry<String, List<PracticeRecordDto>> e : byMode.entrySet()) {
            List<PracticeRecordDto> list = e.getValue();
            List<AnswerItem> items = list.stream()
                    .map(r -> new AnswerItem(r.getVocabId(), r.getKanjiId(), r.isCorrect())).toList();
            LocalDateTime min = null;
            LocalDateTime max = null;
            for (PracticeRecordDto r : list) {
                LocalDateTime t = r.getTimestamp();
                if (t == null) {
                    continue;
                }
                if (min == null || t.isBefore(min)) {
                    min = t;
                }
                if (max == null || t.isAfter(max)) {
                    max = t;
                }
            }
            int duration = (min != null) ? (int) Math.min(ChronoUnit.SECONDS.between(min, max), MAX_DURATION_SECONDS) : 0;
            Long topicId = list.get(list.size() - 1).getTopicId();
            if (saveSessionSafe(learnerId, e.getKey(), topicId, items, duration)) {
                saved++;
            }
        }
        studyProgressService.clearProgress(session);
        return saved;
    }

    public int deleteAll(String learnerId) {
        if (LearnerIdOk(learnerId) == null) {
            return 0;
        }
        Integer n = tx.execute(status -> sessionRepository.deleteAllByLearner(learnerId));
        return n == null ? 0 : n;
    }

    // ---------- reads ----------

    private LocalDateTime since(String range, LocalDateTime now) {
        return switch (range) {
            case "7D" -> now.minusDays(7);
            case "30D" -> now.minusDays(30);
            default -> EPOCH;
        };
    }

    public PageResult findPage(String learnerId, String mode, String range, int page) {
        LocalDateTime since = since(range, LocalDateTime.now(clock));
        int idx = Math.max(0, page - 1);
        Page<StudySession> p = sessionRepository.findPage(learnerId, mode, since, PageRequest.of(idx, PAGE_SIZE));
        int totalPages = Math.max(1, p.getTotalPages());
        if (idx >= totalPages) {
            idx = totalPages - 1;
            p = sessionRepository.findPage(learnerId, mode, since, PageRequest.of(idx, PAGE_SIZE));
        }
        List<SessionView> views = p.getContent().stream().map(StudyHistoryService::toView).toList();
        return new PageResult(views, idx + 1, totalPages, p.getTotalElements());
    }

    private static SessionView toView(StudySession s) {
        int acc = s.getTotalQuestions() > 0
                ? (int) Math.round(s.getCorrectCount() * 100.0 / s.getTotalQuestions()) : 0;
        return new SessionView(s.getId(), s.getMode(), modeLabel(s.getMode()), s.getTopicId(),
                s.getTotalQuestions(), s.getCorrectCount(), acc, s.getDurationSeconds(), s.getCompletedAt());
    }

    public Summary summary(String learnerId, String mode, String range) {
        var t = sessionRepository.totals(learnerId, mode, since(range, LocalDateTime.now(clock)));
        long q = t.getQuestions() == null ? 0 : t.getQuestions();
        long c = t.getCorrect() == null ? 0 : t.getCorrect();
        long sec = t.getSeconds() == null ? 0 : t.getSeconds();
        long n = t.getSessions() == null ? 0 : t.getSessions();
        int acc = q > 0 ? (int) Math.round(c * 100.0 / q) : 0;
        return new Summary(n, q, c, acc, sec, sec / 60);
    }

    /** Độ chính xác theo từng tuần trong {@link #WEEKS} tuần gần nhất (cũ → mới), tuần trống = 0. */
    public List<WeekBar> weeklyAccuracy(String learnerId, String mode) {
        LocalDate today = LocalDate.now(clock);
        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate first = thisMonday.minusWeeks(WEEKS - 1L);
        List<StudySession> sessions = sessionRepository.findSince(learnerId, mode, first.atStartOfDay());
        int[] q = new int[WEEKS];
        int[] c = new int[WEEKS];
        for (StudySession s : sessions) {
            LocalDate d = s.getCompletedAt().toLocalDate();
            long w = ChronoUnit.WEEKS.between(first, d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
            if (w >= 0 && w < WEEKS) {
                q[(int) w] += s.getTotalQuestions();
                c[(int) w] += s.getCorrectCount();
            }
        }
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM");
        List<WeekBar> bars = new ArrayList<>(WEEKS);
        for (int i = 0; i < WEEKS; i++) {
            int acc = q[i] > 0 ? (int) Math.round(c[i] * 100.0 / q[i]) : 0;
            bars.add(new WeekBar(first.plusWeeks(i).format(f), acc, q[i]));
        }
        return bars;
    }

    // ---------- retention ----------

    /** Chạy hằng ngày lúc 03:15. */
    @Scheduled(cron = "${app.history.cleanup-cron:0 15 3 * * *}")
    public void scheduledCleanup() {
        try {
            cleanup(LocalDateTime.now(clock));
        } catch (RuntimeException e) {
            log.warn("History cleanup failed: {}", e.getClass().getSimpleName());
        }
    }

    /** Xoá phiên cũ hơn retention-days tính từ {@code now}. Trả số phiên đã xoá. */
    public int cleanup(LocalDateTime now) {
        LocalDateTime cutoff = now.minusDays(retentionDays);
        Integer n = tx.execute(status -> sessionRepository.deleteOlderThan(cutoff));
        return n == null ? 0 : n;
    }
}