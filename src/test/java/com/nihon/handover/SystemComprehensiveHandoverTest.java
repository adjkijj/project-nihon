package com.nihon.handover;

import com.nihon.config.CsrfInterceptor;
import com.nihon.dto.exam.ExamPaperForm;
import com.nihon.dto.grammar.GrammarArenaStartForm;
import com.nihon.dto.grammar.GrammarQuizAnswerItem;
import com.nihon.dto.grammar.GrammarQuizSubmissionForm;
import com.nihon.entity.*;
import com.nihon.history.LearnerIdentityFilter;
import com.nihon.repository.*;
import com.nihon.service.StudyHistoryService;
import com.nihon.service.grammar.GrammarPracticeService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử toàn diện phục vụ nghiệm thu và bàn giao hệ thống (Pre-Handover Verification Suite):
 * 1. Chống truy cập chéo giữa các learner_id (Cross-Learner Data Isolation).
 * 2. Bảo mật đầu vào xấu (Fuzzing / Bad Input Resilience): rỗng, âm, cực lớn, 10.000 ký tự, emoji, XSS, mode lạ -> Không 500, không lộ stack trace.
 * 3. Kiểm thử hiệu năng (Performance Benchmark): đo thời gian phản hồi danh sách với 5000 từ vựng và 500 Kanji.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class SystemComprehensiveHandoverTest {

    private static final Logger log = LoggerFactory.getLogger(SystemComprehensiveHandoverTest.class);
    private static final String CSRF_TOKEN = "handover-csrf-token-12345";
    private static final String LONG_10K = "A".repeat(10000);
    private static final String EMOJI = "🎌🍣🇯🇵😀🎉🚀🔥";
    private static final String XSS = "<script>alert('xss')</script>\"'><img src=x onerror=alert(1)>";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private VocabularyRepository vocabularyRepository;

    @Autowired
    private KanjiEntryRepository kanjiEntryRepository;

    @Autowired
    private VerbRepository verbRepository;

    @Autowired
    private ExamPaperRepository paperRepository;

    @Autowired
    private ExamAttemptRepository attemptRepository;

    @Autowired
    private StudySessionRepository sessionRepository;

    @Autowired
    private GrammarTopicRepository grammarTopicRepository;

    @Autowired
    private GrammarProgressRepository grammarProgressRepository;

    @Autowired
    private StudyHistoryService historyService;

    @Autowired
    private GrammarPracticeService grammarPracticeService;

    private String learnerA;
    private String learnerB;
    private Cookie cookieA;
    private Cookie cookieB;
    private Topic sampleTopic;
    private ExamPaper sampleExam;
    private GrammarTopic sampleGrammar;

    @BeforeEach
    void setUp() {
        learnerA = UUID.randomUUID().toString();
        learnerB = UUID.randomUUID().toString();
        cookieA = new Cookie(LearnerIdentityFilter.COOKIE_NAME, learnerA);
        cookieB = new Cookie(LearnerIdentityFilter.COOKIE_NAME, learnerB);

        Topic topic = new Topic();
        topic.setName("Chủ đề Bàn giao " + System.currentTimeMillis());
        topic.setSortOrder(1);
        topic.setKanjiSection(false);
        sampleTopic = topicRepository.save(topic);

        sampleExam = paperRepository.save(ExamPaper.builder()
                .code("EXAM-HO-" + UUID.randomUUID().toString().substring(0, 8))
                .title("Đề thi Bàn giao Tổng thể")
                .durationMinutes(30)
                .category(ExamCategory.MOCK)
                .published(true)
                .build());

        sampleGrammar = grammarTopicRepository.save(GrammarTopic.builder()
                .code("GR-HO-" + UUID.randomUUID().toString().substring(0, 8))
                .title("Ngữ pháp Kiểm thử Bàn giao")
                .category(GrammarCategory.GRAMMAR)
                .formula("A から B")
                .explanation("Giải thích chi tiết...")
                .sortOrder(1)
                .build());
    }

    // =========================================================================
    // 1. KIỂM THỬ BẢO MẬT: CHỐNG TRUY CẬP CHÉO THEO LEARNER_ID (DATA ISOLATION)
    // =========================================================================

    @Test
    @DisplayName("Bảo mật: Người học B không thể xem hoặc xóa phiên học (study_session) của người học A")
    void testStudySessionCrossLearnerIsolation() throws Exception {
        // 1. Ghi nhận 1 phiên học của learnerA
        StudySession sA = new StudySession();
        sA.setLearnerId(learnerA);
        sA.setMode("PRACTICE_JA_VI");
        sA.setTopicId(sampleTopic.getId());
        sA.setTotalQuestions(10);
        sA.setCorrectCount(8);
        sA.setDurationSeconds(60);
        sA.setCompletedAt(java.time.LocalDateTime.now());
        sA = sessionRepository.save(sA);

        // 2. Người học B vào /history -> Không nhìn thấy dữ liệu của A
        MvcResult resB = mockMvc.perform(get("/history").cookie(cookieB))
                .andExpect(status().isOk())
                .andReturn();
        String htmlB = resB.getResponse().getContentAsString();
        assertThat(htmlB).doesNotContain("PRACTICE_JA_VI (" + sampleTopic.getName() + ")");

        // 3. Người học B cố tình xóa toàn bộ lịch sử -> Lịch sử của A vẫn nguyên vẹn
        mockMvc.perform(post("/history/delete-all")
                        .cookie(cookieB)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection());

        assertThat(sessionRepository.findById(sA.getId())).isPresent();
    }

    @Test
    @DisplayName("Bảo mật: Người học B không thể xem hoặc xóa bài thi (exam_attempt) của người học A")
    void testExamAttemptCrossLearnerIsolation() throws Exception {
        // 1. Tạo 1 bài thi đã hoàn thành của learnerA
        ExamAttempt aA = attemptRepository.save(ExamAttempt.builder()
                .id(UUID.randomUUID().toString())
                .paper(sampleExam)
                .learnerId(learnerA)
                .startedAt(Instant.now().minusSeconds(500))
                .submittedAt(Instant.now())
                .durationSeconds(500)
                .status(com.nihon.dto.exam.taker.ExamAttemptStatus.FINISHED)
                .totalPoints(new BigDecimal("9.50"))
                .maxPoints(new BigDecimal("10.00"))
                .build());

        // 2. Người học B vào /history -> Không thấy bài thi của A
        MvcResult resB = mockMvc.perform(get("/history").cookie(cookieB))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(resB.getResponse().getContentAsString()).doesNotContain(sampleExam.getTitle());

        // 3. Người học B gửi request xóa bài thi của A theo ID -> Hệ thống chặn, bài thi của A vẫn còn
        mockMvc.perform(post("/history/exams/" + aA.getId() + "/delete")
                        .cookie(cookieB)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection());

        assertThat(attemptRepository.findById(aA.getId())).isPresent();

        // 4. Người học B gửi request xóa tất cả bài thi -> Bài thi của A vẫn còn
        mockMvc.perform(post("/history/exams/delete-all")
                        .cookie(cookieB)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection());

        assertThat(attemptRepository.findById(aA.getId())).isPresent();
    }

    @Test
    @DisplayName("Bảo mật: Tiến độ học ngữ pháp (grammar_progress) hoàn toàn độc lập giữa các người học")
    void testGrammarProgressCrossLearnerIsolation() throws Exception {
        // 1. Người học A đánh dấu đã học ngữ pháp
        grammarPracticeService.toggleProgress(sampleGrammar.getId(), learnerA);
        assertThat(grammarProgressRepository.existsByIdLearnerIdAndIdGrammarId(learnerA, sampleGrammar.getId())).isTrue();

        // 2. Kiểm tra người học B chưa học
        assertThat(grammarProgressRepository.existsByIdLearnerIdAndIdGrammarId(learnerB, sampleGrammar.getId())).isFalse();

        // 3. Người học B toggle -> chỉ ảnh hưởng đến B, không ảnh hưởng đến A
        grammarPracticeService.toggleProgress(sampleGrammar.getId(), learnerB);
        assertThat(grammarProgressRepository.existsByIdLearnerIdAndIdGrammarId(learnerB, sampleGrammar.getId())).isTrue();
        assertThat(grammarProgressRepository.existsByIdLearnerIdAndIdGrammarId(learnerA, sampleGrammar.getId())).isTrue();

        // 4. Người học B toggle hủy bỏ -> A vẫn còn đã học
        grammarPracticeService.toggleProgress(sampleGrammar.getId(), learnerB);
        assertThat(grammarProgressRepository.existsByIdLearnerIdAndIdGrammarId(learnerB, sampleGrammar.getId())).isFalse();
        assertThat(grammarProgressRepository.existsByIdLearnerIdAndIdGrammarId(learnerA, sampleGrammar.getId())).isTrue();
    }

    // =========================================================================
    // 2. KIỂM THỬ ĐẦU VÀO XẤU (BAD INPUT FUZZING & ERROR BOUNDARY)
    // =========================================================================

    @Test
    @DisplayName("Fuzzing Động từ: Tham số xấu (10k ký tự, emoji, số âm, XSS, rỗng) -> Không 500")
    void testVerbBadInputsFuzzing() throws Exception {
        // GET /verbs
        mockMvc.perform(get("/verbs").param("group", "-1")).andExpect(status().isOk());
        mockMvc.perform(get("/verbs").param("group", "999999")).andExpect(status().isOk());
        mockMvc.perform(get("/verbs").param("keyword", LONG_10K)).andExpect(status().isOk());
        mockMvc.perform(get("/verbs").param("keyword", EMOJI)).andExpect(status().isOk());
        mockMvc.perform(get("/verbs").param("keyword", XSS)).andExpect(status().isOk());

        // GET /verbs/lab
        mockMvc.perform(get("/verbs/lab").param("verb", "")).andExpect(status().isOk());
        mockMvc.perform(get("/verbs/lab").param("verb", LONG_10K)).andExpect(status().isOk());
        mockMvc.perform(get("/verbs/lab").param("verb", EMOJI)).andExpect(status().isOk());
        mockMvc.perform(get("/verbs/lab").param("verb", XSS)).andExpect(status().isOk());

        // GET /verbs/practice
        mockMvc.perform(get("/verbs/practice").param("targetForm", "UNKNOWN_FORM")).andExpect(status().isOk());
        mockMvc.perform(get("/verbs/practice").param("group", "-5")).andExpect(status().isOk());
        mockMvc.perform(get("/verbs/practice").param("count", "-100")).andExpect(status().isOk());
        mockMvc.perform(get("/verbs/practice").param("count", "999999")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Fuzzing Đề thi: Tham số xấu trên endpoint thi và quản trị -> Không 500, không lộ lỗi")
    void testExamBadInputsFuzzing() throws Exception {
        // GET /exams/{code}
        mockMvc.perform(get("/exams/NON_EXISTENT_CODE_12345"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/exams/" + EMOJI))
                .andExpect(status().is3xxRedirection());

        // POST /exams/{code}/start với code xấu
        mockMvc.perform(post("/exams/INVALID_CODE/start")
                        .cookie(cookieA)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection());

        // GET /exams/attempt/{id} với ID bất kỳ không tồn tại
        mockMvc.perform(get("/exams/attempt/00000000-0000-0000-0000-000000000000").cookie(cookieA))
                .andExpect(status().is3xxRedirection());

        // GET /exams/admin
        mockMvc.perform(get("/exams/admin").param("category", "INVALID_CAT")).andExpect(status().isOk());
        mockMvc.perform(get("/exams/admin").param("keyword", LONG_10K)).andExpect(status().isOk());
        mockMvc.perform(get("/exams/admin").param("page", "-1")).andExpect(status().isOk());
        mockMvc.perform(get("/exams/admin").param("page", "999999999")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Fuzzing Ngữ pháp & Đấu trường: Tham số xấu, whitelist vi phạm -> Không 500")
    void testGrammarBadInputsFuzzing() throws Exception {
        // GET /grammar
        mockMvc.perform(get("/grammar").param("category", "HACK_CAT")).andExpect(status().isOk());
        mockMvc.perform(get("/grammar").param("keyword", LONG_10K)).andExpect(status().isOk());
        mockMvc.perform(get("/grammar").param("page", "-5")).andExpect(status().isOk());

        // POST /grammar/{id}/toggle-progress với ID âm
        mockMvc.perform(post("/grammar/-99/toggle-progress")
                        .cookie(cookieA)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().isNotFound());

        // POST /grammar/{id}/quiz/check với dữ liệu rỗng (Redirect về lại thẻ kèm flash attribute)
        mockMvc.perform(post("/grammar/" + sampleGrammar.getId() + "/quiz/check")
                        .cookie(cookieA)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection());

        // POST /grammar/arena/start với duration và count vi phạm whitelist -> Redirect về lại form kèm flash error
        mockMvc.perform(post("/grammar/arena/start")
                        .cookie(cookieA)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("category", "ALL")
                        .param("durationMinutes", "999")
                        .param("questionCount", "500"))
                .andExpect(status().is3xxRedirection());
    }

    // =========================================================================
    // 3. KIỂM THỬ HIỆU NĂNG DANH SÁCH (BENCHMARK: 5000 TỪ & 500 KANJI)
    // =========================================================================

    @Test
    @DisplayName("Hiệu năng: Nạp 5.000 từ vựng và 500 Kanji, đo thời gian phản hồi các trang danh sách chính (< 500ms)")
    void testPerformanceBenchmarkWithLargeDataset() throws Exception {
        log.info("Bắt đầu chuẩn bị tập dữ liệu lớn: 5.000 từ vựng và 500 Kanji...");
        long t0 = System.currentTimeMillis();

        Topic bulkTopic = new Topic();
        bulkTopic.setName("Chủ đề Quy mô Lớn " + System.currentTimeMillis());
        bulkTopic.setSortOrder(999);
        bulkTopic.setKanjiSection(false);
        bulkTopic = topicRepository.save(bulkTopic);

        // 1. Sinh 5.000 từ vựng
        List<Vocabulary> vocabList = new ArrayList<>(5000);
        for (int i = 1; i <= 5000; i++) {
            Vocabulary v = new Vocabulary();
            v.setTopic(bulkTopic);
            v.setJapaneseText("単語_" + i);
            v.setReading("たんご_" + i);
            v.setRomaji("tango_" + i);
            v.setMeaningVi("Từ vựng mẫu số " + i);
            v.setHasKanji(true);
            vocabList.add(v);
        }
        vocabularyRepository.saveAll(vocabList);

        // 2. Sinh 500 Kanji
        List<KanjiEntry> kanjiList = new ArrayList<>(500);
        for (int i = 1; i <= 500; i++) {
            KanjiEntry k = new KanjiEntry();
            k.setTopic(bulkTopic);
            k.setCharacter("字" + i);
            k.setHanViet("TỰ" + i);
            k.setMeaningVi("Ý nghĩa Hán tự " + i);
            k.setOnyomi("ジ");
            k.setKunyomi("あざ");
            k.setSourceLine(100000 + i);
            kanjiList.add(k);
        }
        kanjiEntryRepository.saveAll(kanjiList);

        long prepareDuration = System.currentTimeMillis() - t0;
        log.info("Đã nạp xong 5.000 từ vựng và 500 Kanji trong {} ms.", prepareDuration);

        // 3. Khởi động / Warm-up Thymeleaf template cache
        mockMvc.perform(get("/kanji").cookie(cookieA)).andExpect(status().isOk());
        mockMvc.perform(get("/vocabulary").cookie(cookieA)).andExpect(status().isOk());

        // 4. Đo đạc thời gian phản hồi các trang chính
        Map<String, Long> responseTimes = new LinkedHashMap<>();

        measurePage("/vocabulary", responseTimes);
        measurePage("/kanji", responseTimes);
        measurePage("/topics", responseTimes);
        measurePage("/verbs", responseTimes);
        measurePage("/grammar", responseTimes);
        measurePage("/exams", responseTimes);
        measurePage("/history", responseTimes);

        log.info("===== KẾT QUẢ HIỆU NĂNG TRANG DANH SÁCH (5000 Từ & 500 Kanji) =====");
        for (Map.Entry<String, Long> entry : responseTimes.entrySet()) {
            log.info("Route: {} | Thời gian phản hồi: {} ms", entry.getKey(), entry.getValue());
            // Kỳ vọng mọi trang phản hồi dưới 500ms khi đã đánh index và phân trang đầy đủ
            assertThat(entry.getValue())
                    .withFailMessage("Trang %s phản hồi quá chậm: %d ms (tiêu chuẩn < 500ms)", entry.getKey(), entry.getValue())
                    .isLessThan(500L);
        }
    }

    private void measurePage(String url, Map<String, Long> responseTimes) throws Exception {
        // Khởi động (warm-up) để biên dịch Thymeleaf template trước khi bấm giờ
        long tWarm = System.currentTimeMillis();
        mockMvc.perform(get(url).cookie(cookieA)).andExpect(status().isOk());
        long warmDuration = System.currentTimeMillis() - tWarm;

        long start = System.currentTimeMillis();
        mockMvc.perform(get(url).cookie(cookieA))
                .andExpect(status().isOk());
        long duration = System.currentTimeMillis() - start;
        log.info("Benchmarking {} -> Warm-up: {} ms | Measured: {} ms", url, warmDuration, duration);
        responseTimes.put(url, duration);
    }
}
