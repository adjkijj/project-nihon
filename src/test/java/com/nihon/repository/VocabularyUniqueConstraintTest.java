package com.nihon.repository;

import com.nihon.entity.KanjiEntry;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm thử ràng buộc độc nhất chống trùng khi import:
 * - vocabulary theo (topic_id, japanese)
 * - kanji theo (kanji)
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class VocabularyUniqueConstraintTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private VocabularyRepository vocabularyRepository;

    @Autowired
    private KanjiEntryRepository kanjiEntryRepository;

    private Topic topic1;
    private Topic topic2;

    @BeforeEach
    void setUp() {
        topic1 = new Topic();
        topic1.setName("Chủ đề 1");
        topic1.setSortOrder(1);
        topic1 = em.persistAndFlush(topic1);

        topic2 = new Topic();
        topic2.setName("Chủ đề 2");
        topic2.setSortOrder(2);
        topic2 = em.persistAndFlush(topic2);
    }

    @Test
    @DisplayName("Thêm và đọc từ vựng thành công")
    void testSaveAndFindVocabulary() {
        Vocabulary v = new Vocabulary();
        v.setTopic(topic1);
        v.setJapaneseText("私（わたし）");
        v.setReading("わたし");
        v.setRomaji("watashi");
        v.setMeaningVi("Tôi");
        v.setHasKanji(true);
        v.setSourceLine(10);

        Vocabulary saved = vocabularyRepository.saveAndFlush(v);
        em.clear();

        var found = vocabularyRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getJapaneseText()).isEqualTo("私（わたし）");
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getUpdatedAt()).isNotNull();

        var byKey = vocabularyRepository.findByTopicIdAndJapaneseText(topic1.getId(), "私（わたし）");
        assertThat(byKey).isPresent();
        assertThat(vocabularyRepository.existsByTopicIdAndJapaneseText(topic1.getId(), "私（わたし）")).isTrue();
    }

    @Test
    @DisplayName("Trùng từ vựng (topic_id, japanese) trong cùng chủ đề phải ném ngoại lệ DataIntegrityViolationException")
    void testDuplicateVocabularyInSameTopicThrowsException() {
        Vocabulary v1 = new Vocabulary();
        v1.setTopic(topic1);
        v1.setJapaneseText("私（わたし）");
        v1.setRomaji("watashi");
        v1.setMeaningVi("Tôi");
        vocabularyRepository.saveAndFlush(v1);

        Vocabulary v2 = new Vocabulary();
        v2.setTopic(topic1);
        v2.setJapaneseText("私（わたし）"); // Cùng topic1 và cùng japaneseText
        v2.setRomaji("watashi");
        v2.setMeaningVi("Nghĩa khác");

        assertThatThrownBy(() -> vocabularyRepository.saveAndFlush(v2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Cùng từ vựng nhưng ở hai chủ đề khác nhau thì hợp lệ")
    void testSameJapaneseInDifferentTopicsIsAllowed() {
        Vocabulary v1 = new Vocabulary();
        v1.setTopic(topic1);
        v1.setJapaneseText("猫");
        v1.setRomaji("neko");
        v1.setMeaningVi("Con mèo");
        vocabularyRepository.saveAndFlush(v1);

        Vocabulary v2 = new Vocabulary();
        v2.setTopic(topic2);
        v2.setJapaneseText("猫"); // Cùng từ tiếng Nhật nhưng khác chủ đề topic2
        v2.setRomaji("neko");
        v2.setMeaningVi("Con mèo");

        Vocabulary saved2 = vocabularyRepository.saveAndFlush(v2);
        assertThat(saved2.getId()).isNotNull();
    }

    @Test
    @DisplayName("Trùng ký tự Kanji phải ném ngoại lệ DataIntegrityViolationException")
    void testDuplicateKanjiCharacterThrowsException() {
        KanjiEntry k1 = new KanjiEntry();
        k1.setTopic(topic1);
        k1.setCharacter("日");
        k1.setHanViet("NHẬT");
        k1.setMeaningVi("Mặt trời, ngày");
        k1.setSourceLine(200);
        kanjiEntryRepository.saveAndFlush(k1);

        KanjiEntry k2 = new KanjiEntry();
        k2.setTopic(topic2);
        k2.setCharacter("日"); // Ký tự Kanji đã tồn tại
        k2.setHanViet("NHẬT");
        k2.setMeaningVi("Mặt trời");
        k2.setSourceLine(201);

        assertThatThrownBy(() -> kanjiEntryRepository.saveAndFlush(k2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
