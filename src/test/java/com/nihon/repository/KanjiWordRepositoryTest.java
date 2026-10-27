package com.nihon.repository;

import com.nihon.entity.KanjiEntry;
import com.nihon.entity.KanjiWord;
import com.nihon.entity.Topic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm thử cho KanjiWordRepository: thêm, đọc, sắp xếp và kiểm tra vi phạm ràng buộc độc nhất.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class KanjiWordRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private KanjiWordRepository kanjiWordRepository;

    private KanjiEntry kanjiEki;
    private KanjiEntry kanjiGaku;

    @BeforeEach
    void setUp() {
        Topic topic = new Topic();
        topic.setName("Kanji Lesson 1");
        topic.setSortOrder(1);
        topic.setKanjiSection(true);
        topic = em.persistAndFlush(topic);

        kanjiEki = new KanjiEntry();
        kanjiEki.setTopic(topic);
        kanjiEki.setCharacter("駅");
        kanjiEki.setHanViet("DỊCH");
        kanjiEki.setMeaningVi("Ga tàu");
        kanjiEki.setOnyomi("えき");
        kanjiEki.setKunyomi("-");
        kanjiEki.setSourceLine(100);
        kanjiEki = em.persistAndFlush(kanjiEki);

        kanjiGaku = new KanjiEntry();
        kanjiGaku.setTopic(topic);
        kanjiGaku.setCharacter("学");
        kanjiGaku.setHanViet("HỌC");
        kanjiGaku.setMeaningVi("Học tập");
        kanjiGaku.setOnyomi("がく");
        kanjiGaku.setKunyomi("まな・ぶ");
        kanjiGaku.setSourceLine(101);
        kanjiGaku = em.persistAndFlush(kanjiGaku);
    }

    @Test
    @DisplayName("Thêm mới và đọc từ cần nhớ của Kanji thành công")
    void testSaveAndFindKanjiWord() {
        KanjiWord word = new KanjiWord();
        word.setKanji(kanjiEki);
        word.setJapanese("駅前");
        word.setRomaji("ekimae");
        word.setMeaningVi("Trước nhà ga");
        word.setSortOrder(1);

        KanjiWord saved = kanjiWordRepository.saveAndFlush(word);
        em.clear();

        Optional<KanjiWord> found = kanjiWordRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getJapanese()).isEqualTo("駅前");
        assertThat(found.get().getRomaji()).isEqualTo("ekimae");
        assertThat(found.get().getMeaningVi()).isEqualTo("Trước nhà ga");
        assertThat(found.get().getSortOrder()).isEqualTo(1);
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Đọc danh sách từ cần nhớ theo mã Kanji sắp xếp theo sortOrder tăng dần")
    void testFindByKanjiIdOrderBySortOrderAsc() {
        KanjiWord w2 = new KanjiWord();
        w2.setKanji(kanjiEki);
        w2.setJapanese("駅員");
        w2.setRomaji("ekiin");
        w2.setMeaningVi("Nhân viên nhà ga");
        w2.setSortOrder(2);
        em.persist(w2);

        KanjiWord w1 = new KanjiWord();
        w1.setKanji(kanjiEki);
        w1.setJapanese("駅前");
        w1.setRomaji("ekimae");
        w1.setMeaningVi("Trước ga");
        w1.setSortOrder(1);
        em.persist(w1);

        KanjiWord wOther = new KanjiWord();
        wOther.setKanji(kanjiGaku);
        wOther.setJapanese("学校");
        wOther.setRomaji("gakkou");
        wOther.setMeaningVi("Trường học");
        wOther.setSortOrder(1);
        em.persist(wOther);

        em.flush();
        em.clear();

        List<KanjiWord> ekiWords = kanjiWordRepository.findByKanjiIdOrderBySortOrderAsc(kanjiEki.getId());
        assertThat(ekiWords).hasSize(2);
        assertThat(ekiWords.get(0).getJapanese()).isEqualTo("駅前");
        assertThat(ekiWords.get(1).getJapanese()).isEqualTo("駅員");
    }

    @Test
    @DisplayName("Tìm kiếm từ cần nhớ theo cặp (kanjiId, japanese)")
    void testFindByKanjiIdAndJapanese() {
        KanjiWord w = new KanjiWord();
        w.setKanji(kanjiEki);
        w.setJapanese("駅前");
        w.setRomaji("ekimae");
        w.setMeaningVi("Trước ga");
        w.setSortOrder(1);
        em.persistAndFlush(w);
        em.clear();

        Optional<KanjiWord> found = kanjiWordRepository.findByKanjiIdAndJapanese(kanjiEki.getId(), "駅前");
        assertThat(found).isPresent();
        assertThat(found.get().getRomaji()).isEqualTo("ekimae");

        assertThat(kanjiWordRepository.existsByKanjiIdAndJapanese(kanjiEki.getId(), "駅前")).isTrue();
        assertThat(kanjiWordRepository.existsByKanjiIdAndJapanese(kanjiEki.getId(), "駅員")).isFalse();
    }

    @Test
    @DisplayName("Vi phạm ràng buộc độc nhất (kanji_id, japanese) phải ném ngoại lệ DataIntegrityViolationException")
    void testUniqueConstraintViolationOnKanjiAndJapanese() {
        KanjiWord first = new KanjiWord();
        first.setKanji(kanjiEki);
        first.setJapanese("駅前");
        first.setRomaji("ekimae");
        first.setMeaningVi("Trước ga");
        first.setSortOrder(1);
        kanjiWordRepository.saveAndFlush(first);

        KanjiWord duplicate = new KanjiWord();
        duplicate.setKanji(kanjiEki);
        duplicate.setJapanese("駅前"); // Cùng Kanji và cùng từ tiếng Nhật
        duplicate.setRomaji("ekimae");
        duplicate.setMeaningVi("Nghĩa khác");
        duplicate.setSortOrder(2);

        assertThatThrownBy(() -> kanjiWordRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
