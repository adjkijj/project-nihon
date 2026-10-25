package com.nihon.repository;

import com.nihon.entity.KanjiEntry;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class EntityMappingTest {

    @Autowired private TestEntityManager em;
    @Autowired private TopicRepository topicRepository;
    @Autowired private VocabularyRepository vocabularyRepository;
    @Autowired private KanjiEntryRepository kanjiEntryRepository;

    private Topic topic(String name, Topic parent, int line, boolean kanji) {
        Topic t = new Topic();
        t.setName(name);
        t.setParent(parent);
        t.setSortOrder(line);
        t.setSourceLine(line);
        t.setKanjiSection(kanji);
        return em.persistAndFlush(t);
    }

    private Vocabulary vocab(Topic t, String jp, String reading, int line) {
        Vocabulary v = new Vocabulary();
        v.setTopic(t);
        v.setJapaneseText(jp);
        v.setReading(reading);
        v.setRomaji("watashi");
        v.setMeaningVi("Tôi");
        v.setHasKanji(true);
        v.setSourceLine(line);
        return v;
    }

    @Test
    void persistsAndQueriesAllEntities() {
        Topic root = topic("2. Bản thân, quốc gia, ngôn ngữ và nghề nghiệp", null, 19, false);
        Topic kanjiRoot = topic("13. Kanji", null, 1000, true);
        Topic lesson = topic("Kanji Lesson 5", kanjiRoot, 1002, true);

        vocabularyRepository.saveAndFlush(vocab(root, "私（わたし）", "わたし", 23));
        Vocabulary noReading = vocab(root, "1歳", null, 62);
        vocabularyRepository.saveAndFlush(noReading);

        KanjiEntry k = new KanjiEntry();
        k.setTopic(lesson);
        k.setCharacter("駅");
        k.setHanViet("DỊCH");
        k.setMeaningVi("Ga tàu");
        k.setOnyomi("えき");
        k.setKunyomi(null);
        k.setSourceLine(1733);
        kanjiEntryRepository.saveAndFlush(k);
        em.clear();

        assertThat(topicRepository.findByParentIsNullOrderBySortOrderAsc()).hasSize(2);
        assertThat(topicRepository.findByParentIdOrderBySortOrderAsc(kanjiRoot.getId())).hasSize(1);
        assertThat(vocabularyRepository.findByTopicIdOrderBySourceLineAsc(root.getId()))
                .extracting(Vocabulary::getJapaneseText)
                .containsExactly("私（わたし）", "1歳");
        assertThat(vocabularyRepository.findBySourceLine(62)).get()
                .extracting(Vocabulary::getReading).isNull();
        assertThat(kanjiEntryRepository.findByCharacter("駅")).isPresent();
    }

    @Test
    void duplicateSourceLineIsRejected() {
        Topic root = topic("1. Chào hỏi", null, 5, false);
        vocabularyRepository.saveAndFlush(vocab(root, "私（わたし）", "わたし", 23));
        assertThatThrownBy(() -> vocabularyRepository.saveAndFlush(vocab(root, "私（わたし）", "わたし", 23)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void deletingTopicWithVocabularyIsBlockedAndVocabularyKept() {
        Topic root = topic("1. Chào hỏi", null, 5, false);
        vocabularyRepository.saveAndFlush(vocab(root, "私（わたし）", "わたし", 23));
        Long topicId = root.getId();
        em.clear();

        assertThatThrownBy(() -> {
            topicRepository.deleteById(topicId);
            topicRepository.flush();
        }).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
