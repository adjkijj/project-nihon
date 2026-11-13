package com.nihon.service;

import com.nihon.config.CsrfInterceptor;
import com.nihon.entity.KanjiEntry;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.history.LearnerIdentityFilter;
import com.nihon.repository.KanjiEntryRepository;
import com.nihon.repository.StudySessionRepository;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VocabularyRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TopicTestServiceTest {

    private static final String CSRF_TOKEN = "csrf-topic-test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private VocabularyRepository vocabularyRepository;

    @Autowired
    private KanjiEntryRepository kanjiEntryRepository;

    @Autowired
    private TopicTestService topicTestService;

    @Autowired
    private StudySessionRepository sessionRepository;

    private Topic testTopic;

    @BeforeEach
    void setUp() {
        Topic t = new Topic();
        t.setName("Chủ đề Kiểm tra Test " + System.currentTimeMillis());
        t.setSortOrder(1);
        testTopic = topicRepository.save(t);

        int baseLine = (int) (System.currentTimeMillis() % 1_000_000) + 500_000;
        // Tạo 4 từ vựng
        for (int i = 1; i <= 4; i++) {
            Vocabulary v = new Vocabulary();
            v.setJapaneseText("単語" + i + "_" + baseLine);
            v.setReading("たんご" + i);
            v.setMeaningVi("Từ vựng số " + i);
            v.setTopic(testTopic);
            v.setHasKanji(true);
            v.setSourceLine(baseLine + i);
            vocabularyRepository.save(v);
        }

        // Tạo 2 chữ Kanji
        for (int i = 1; i <= 2; i++) {
            KanjiEntry k = new KanjiEntry();
            k.setCharacter(String.valueOf((char) (0x3400 + (baseLine % 500) + i)));
            k.setHanViet("TỰ " + i);
            k.setMeaningVi("Chữ số " + i);
            k.setTopic(testTopic);
            k.setOnyomi("ジ");
            k.setKunyomi("あざ");
            k.setSourceLine(baseLine + 100 + i);
            kanjiEntryRepository.save(k);
        }
    }

    @Test
    @DisplayName("F40: Tạo bài kiểm tra cuối bài thành công (từ 10 đến 20 câu hoặc toàn bộ nếu ít hơn)")
    void testCreateTopicTestSuccess() {
        TopicTestService.TopicTestSession session = topicTestService.createTest(testTopic.getId());
        assertThat(session).isNotNull();
        assertThat(session.getTopicId()).isEqualTo(testTopic.getId());
        assertThat(session.getQuestions()).hasSize(6); // Có 6 mục (4 vocab + 2 kanji)

        // Mỗi câu hỏi có đúng 4 lựa chọn và 1 đáp án đúng
        for (TopicTestService.TopicTestQuestion q : session.getQuestions()) {
            assertThat(q.getChoices()).hasSize(4);
            assertThat(q.getCorrectChoiceIndex()).isBetween(0, 3);
            assertThat(q.getPrompt()).isNotBlank();
        }
    }

    @Test
    @DisplayName("F40: Chủ đề có ít hơn 4 mục từ vựng/Kanji sẽ bị từ chối")
    void testCreateTopicTestFailsWhenNotEnoughItems() {
        Topic empty = new Topic();
        empty.setName("Chủ đề trống");
        empty.setSortOrder(2);
        final Topic emptyTopic = topicRepository.save(empty);
        assertThatThrownBy(() -> topicTestService.createTest(emptyTopic.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cần có ít nhất 4 từ vựng hoặc Kanji");
    }

    @Test
    @DisplayName("F40: Nộp bài kiểm tra cuối bài: ghi nhận vào StudySession với mode = TOPIC_TEST")
    void testSubmitTopicTestRecordsIntoStudySession() {
        String learnerId = UUID.randomUUID().toString();
        TopicTestService.TopicTestSession testSession = topicTestService.createTest(testTopic.getId());

        Map<Integer, Integer> answers = new HashMap<>();
        // Trả lời đúng câu 1
        answers.put(1, testSession.getQuestions().get(0).getCorrectChoiceIndex());

        topicTestService.submitTest(testSession, answers, learnerId);

        assertThat(testSession.isCompleted()).isTrue();
        assertThat(testSession.getCorrectCount()).isGreaterThanOrEqualTo(1);

        // Kiểm tra trong database study_session có bản ghi mode = TOPIC_TEST
        var sessions = sessionRepository.findAll().stream()
                .filter(s -> learnerId.equals(s.getLearnerId()) && "TOPIC_TEST".equals(s.getMode()))
                .toList();

        assertThat(sessions).isNotEmpty();
        assertThat(sessions.get(0).getTopicId()).isEqualTo(testTopic.getId());
        assertThat(sessions.get(0).getCorrectCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("F40: Luồng Web hoàn chỉnh GET /topics/{id}/test và POST /topics/{id}/test/submit")
    void testWebTopicTestFlow() throws Exception {
        String learnerId = UUID.randomUUID().toString();
        Cookie learnerCookie = new Cookie(LearnerIdentityFilter.COOKIE_NAME, learnerId);
        MockHttpSession httpSession = new MockHttpSession();

        // 1. Mở trang làm bài kiểm tra
        mockMvc.perform(get("/topics/" + testTopic.getId() + "/test")
                        .cookie(learnerCookie)
                        .session(httpSession))
                .andExpect(status().isOk())
                .andExpect(view().name("topics/test"))
                .andExpect(model().attributeExists("testSession", "topicId"))
                .andExpect(content().string(containsString("Kiểm tra cuối bài")));

        // 2. Nộp bài kiểm tra
        mockMvc.perform(post("/topics/" + testTopic.getId() + "/test/submit")
                        .cookie(learnerCookie)
                        .session(httpSession)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("question_1", "0")
                        .param("question_2", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("topics/test"))
                .andExpect(model().attributeExists("testSession"))
                .andExpect(content().string(containsString("Kết quả kiểm tra")));
    }
}

