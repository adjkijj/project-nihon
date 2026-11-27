package com.nihon.controller;

import com.nihon.dto.topic.TopicHubDto;
import com.nihon.entity.ExamCategory;
import com.nihon.entity.ExamPaper;
import com.nihon.entity.GrammarCategory;
import com.nihon.entity.GrammarExample;
import com.nihon.entity.GrammarTopic;
import com.nihon.entity.KanjiEntry;
import com.nihon.entity.Topic;
import com.nihon.entity.Verb;
import com.nihon.entity.Vocabulary;
import com.nihon.repository.ExamPaperRepository;
import com.nihon.repository.GrammarExampleRepository;
import com.nihon.repository.GrammarTopicRepository;
import com.nihon.repository.KanjiEntryRepository;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VerbRepository;
import com.nihon.repository.VocabularyRepository;
import com.nihon.service.TopicHubService;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Kiểm thử tích hợp cho Trang Cẩm nang Bài học All-in-One (F12) và TopicHubService.
 * Bao gồm:
 * 1. Validate topicId (<= 0) trả về 404 NOT_FOUND.
 * 2. Topic không tồn tại trong CSDL trả về 404 NOT_FOUND.
 * 3. Hiển thị trang Hub thành công với đầy đủ dữ liệu và liên kết flashcard, quiz, đề thi.
 * 4. Đếm số câu truy vấn SQL (Hibernate Statistics), cam kết tối ưu <= 8 truy vấn.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TopicHubIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TopicHubService topicHubService;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private VocabularyRepository vocabularyRepository;

    @Autowired
    private KanjiEntryRepository kanjiEntryRepository;

    @Autowired
    private GrammarTopicRepository grammarTopicRepository;

    @Autowired
    private GrammarExampleRepository grammarExampleRepository;

    @Autowired
    private VerbRepository verbRepository;

    @Autowired
    private ExamPaperRepository examPaperRepository;

    @Autowired
    private EntityManager entityManager;

    private Topic parentTopic;
    private Topic testTopic;

    @BeforeEach
    void setUp() {
        // Tạo Topic cha
        parentTopic = new Topic();
        parentTopic.setName("Chủ đề gốc " + UUID.randomUUID().toString().substring(0, 8));
        parentTopic.setKanjiSection(false);
        parentTopic.setSortOrder(1);
        parentTopic = topicRepository.save(parentTopic);

        // Tạo Topic bài học test thuộc topic cha
        testTopic = new Topic();
        testTopic.setName("Bài học All-in-One " + UUID.randomUUID().toString().substring(0, 8));
        testTopic.setParent(parentTopic);
        testTopic.setKanjiSection(false);
        testTopic.setSortOrder(2);
        testTopic = topicRepository.save(testTopic);

        // Tạo từ vựng
        Vocabulary vocab1 = new Vocabulary();
        vocab1.setTopic(testTopic);
        vocab1.setJapaneseText("食べる");
        vocab1.setReading("たべる");
        vocab1.setRomaji("taberu");
        vocab1.setMeaningVi("Ăn");
        vocab1.setHasKanji(true);
        vocab1.setSourceLine(999988);
        vocabularyRepository.save(vocab1);

        // Tạo Kanji (chữ độc nhất không trùng seed data)
        KanjiEntry kanji1 = new KanjiEntry();
        kanji1.setTopic(testTopic);
        kanji1.setCharacter("猫");
        kanji1.setHanViet("MIÊU");
        kanji1.setOnyomi("ビョウ");
        kanji1.setKunyomi("ねこ");
        kanji1.setMeaningVi("Con mèo");
        kanji1.setSourceLine(999988);
        kanjiEntryRepository.save(kanji1);

        // Tạo Ngữ pháp kèm ví dụ
        GrammarTopic grammar1 = new GrammarTopic();
        grammar1.setTopic(testTopic);
        grammar1.setCode("G-HUB-" + UUID.randomUUID().toString().substring(0, 8));
        grammar1.setTitle("Mẫu câu ～ています");
        grammar1.setFormula("V-て + います");
        grammar1.setExplanation("Diễn tả hành động đang tiếp diễn.");
        grammar1.setCategory(GrammarCategory.GRAMMAR);
        grammar1.setSortOrder(1);
        grammar1 = grammarTopicRepository.save(grammar1);

        GrammarExample example1 = new GrammarExample();
        example1.setGrammarTopic(grammar1);
        example1.setJapanese("ご飯を食べています。");
        example1.setReading("ごはんをたべています。");
        example1.setMeaningVi("Tôi đang ăn cơm.");
        example1.setSortOrder(1);
        grammarExampleRepository.save(example1);
        grammar1.getExamples().add(example1);

        // Tạo Động từ
        Verb verb1 = new Verb();
        verb1.setTopic(testTopic);
        verb1.setDictionaryForm("あそぶ");
        verb1.setMasuForm("あそびます");
        verb1.setKanjiText("遊ぶ");
        verb1.setVerbGroup(1);
        verb1.setMeaningVi("Chơi");
        verbRepository.save(verb1);

        // Tạo Đề thi liên quan (published = true)
        ExamPaper exam1 = new ExamPaper();
        exam1.setTopic(testTopic);
        exam1.setCode("EXAM-HUB-" + UUID.randomUUID().toString().substring(0, 6));
        exam1.setTitle("Đề thi ôn tập chủ đề Hub");
        exam1.setCategory(ExamCategory.REVIEW);
        exam1.setDurationMinutes(30);
        exam1.setPublished(true);
        examPaperRepository.save(exam1);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("Validate topicId <= 0: /topics/0/hub và /topics/-1/hub phải trả về HTTP 404")
    void testValidateTopicId_negativeOrZero_returns404() throws Exception {
        mockMvc.perform(get("/topics/0/hub"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/topics/-1/hub"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/lessons/0"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/lessons/-99"))
                .andExpect(status().isNotFound());

        assertThatThrownBy(() -> topicHubService.getTopicHubData(0L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");

        assertThatThrownBy(() -> topicHubService.getTopicHubData(-5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
    }

    @Test
    @DisplayName("Topic không tồn tại trong CSDL: Phải trả về HTTP 404")
    void testTopicNotFound_returns404() throws Exception {
        long nonExistentId = 999_999_999L;

        mockMvc.perform(get("/topics/" + nonExistentId + "/hub"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/lessons/" + nonExistentId))
                .andExpect(status().isNotFound());

        assertThatThrownBy(() -> topicHubService.getTopicHubData(nonExistentId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
    }

    @Test
    @DisplayName("Truy cập trang Hub thành công: Trả về HTTP 200, view topics/hub, chứa đầy đủ học liệu và liên kết")
    void testTopicHubSuccess_rendersCorrectViewAndContent() throws Exception {
        mockMvc.perform(get("/topics/" + testTopic.getId() + "/hub"))
                .andExpect(status().isOk())
                .andExpect(view().name("topics/hub"))
                .andExpect(model().attributeExists("hub", "topic", "vocabularyList", "kanjiList", "grammarList", "verbList", "examList"))
                .andExpect(content().string(containsString("🌟 Cẩm nang All-in-One (F12)")))
                .andExpect(content().string(containsString("食べる")))
                .andExpect(content().string(containsString("猫")))
                .andExpect(content().string(containsString("MIÊU")))
                .andExpect(content().string(containsString("Mẫu câu ～ています")))
                .andExpect(content().string(containsString("ご飯を食べています。")))
                .andExpect(content().string(containsString("あそぶ")))
                .andExpect(content().string(containsString("あそびます")))
                .andExpect(content().string(containsString("あそんで")))
                .andExpect(content().string(containsString("あそんだ")))
                .andExpect(content().string(containsString("あそばない")))
                .andExpect(content().string(containsString("/flashcards?topicId=" + testTopic.getId())))
                .andExpect(content().string(containsString("/practice/choice?topicId=" + testTopic.getId())))
                .andExpect(content().string(containsString("/practice?topicId=" + testTopic.getId())))
                .andExpect(content().string(containsString("/practice/vi-ja?topicId=" + testTopic.getId())))
                .andExpect(content().string(containsString("/practice/timed?topicId=" + testTopic.getId())))
                .andExpect(content().string(containsString("/verbs/practice?topicId=" + testTopic.getId())))
                .andExpect(content().string(containsString("/grammar/arena?topicId=" + testTopic.getId())))
                .andExpect(content().string(containsString("/topics/" + testTopic.getId() + "/test")));

        // Alias đường dẫn /lessons/{topicId}
        mockMvc.perform(get("/lessons/" + testTopic.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("topics/hub"));
    }

    @Test
    @DisplayName("Tối ưu hóa hiệu năng truy vấn SQL: Không vượt quá 8 truy vấn cho toàn bộ trang Hub")
    void testSqlQueriesCount_isAtMost8Queries() {
        SessionFactory sessionFactory = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        // Nạp toàn bộ dữ liệu trang Hub
        TopicHubDto hub = topicHubService.getTopicHubData(testTopic.getId());

        long queryCount = statistics.getPrepareStatementCount();

        // Kiểm tra dữ liệu nạp đầy đủ
        assertThat(hub).isNotNull();
        assertThat(hub.getTopic().getId()).isEqualTo(testTopic.getId());
        assertThat(hub.getVocabularyList()).hasSize(1);
        assertThat(hub.getKanjiList()).hasSize(1);
        assertThat(hub.getGrammarList()).hasSize(1);
        assertThat(hub.getGrammarList().get(0).getExamples()).hasSize(1);
        assertThat(hub.getVerbList()).hasSize(1);
        assertThat(hub.getExamList()).hasSize(1);

        // Số câu truy vấn thực hiện phải <= 8 (thực tế đúng 6 câu truy vấn JOIN FETCH tối ưu)
        assertThat(queryCount)
                .as("Số lượng truy vấn SQL để nạp toàn bộ trang Hub phải <= 8")
                .isLessThanOrEqualTo(8L);

        // Xác nhận số lượng cụ thể
        assertThat(queryCount).isEqualTo(6L);
    }
}
