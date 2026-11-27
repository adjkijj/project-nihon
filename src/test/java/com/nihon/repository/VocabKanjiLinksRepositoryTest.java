package com.nihon.repository;

import com.nihon.entity.KanjiEntry;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.service.KanjiExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class VocabKanjiLinksRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private VocabularyRepository vocabularyRepository;

    @Autowired
    private KanjiEntryRepository kanjiEntryRepository;

    private Topic topic;

    @BeforeEach
    void setUp() {
        topic = new Topic();
        topic.setName("Chủ đề Test");
        topic.setSortOrder(1);
        topic.setSourceLine(10);
        topic.setKanjiSection(false);
        topic = em.persistAndFlush(topic);
    }

    private Vocabulary createVocab(String japaneseText, String reading, String meaningVi) {
        Vocabulary v = new Vocabulary();
        v.setTopic(topic);
        v.setJapaneseText(japaneseText);
        v.setReading(reading);
        v.setRomaji("test");
        v.setMeaningVi(meaningVi);
        v.setHasKanji(true);
        return em.persistAndFlush(v);
    }

    private int kanjiLine = 100;

    private KanjiEntry createKanji(String character, String hanViet, String meaningVi) {
        KanjiEntry k = new KanjiEntry();
        k.setTopic(topic);
        k.setCharacter(character);
        k.setHanViet(hanViet);
        k.setMeaningVi(meaningVi);
        k.setOnyomi("オン");
        k.setKunyomi("くん");
        k.setSourceLine(kanjiLine++);
        return em.persistAndFlush(k);
    }

    @Test
    @DisplayName("F58: KanjiEntryRepository.findByCharacterIn tìm kiếm đúng theo danh sách ký tự IN")
    void testFindByCharacterIn() {
        createKanji("日", "NHẬT", "Mặt trời, ngày");
        createKanji("本", "BẢN", "Gốc, sách");
        createKanji("語", "NGỮ", "Ngôn ngữ");

        List<KanjiEntry> found = kanjiEntryRepository.findByCharacterIn(List.of("日", "語", "無"));
        assertThat(found).hasSize(2);
        assertThat(found).extracting(KanjiEntry::getCharacter).containsExactlyInAnyOrder("日", "語");
    }

    @Test
    @DisplayName("F58: VocabularyRepository truy vấn từ chứa Kanji kèm ESCAPE '\\' không bị wildcard injection")
    void testFindWordsContainingKanji_withEscape() {
        createVocab("日本（にほん）", "にほん", "Nhật Bản");
        createVocab("日本語（にほんご）", "にほんご", "Tiếng Nhật");
        createVocab("100%（ひゃくパーセント）", "ひゃくパーセント", "Một trăm phần trăm");
        createVocab("A_B", "エービー", "A gạch dưới B");

        // Tìm chữ "本"
        String escapedHon = KanjiExtractor.escapeLikePattern("本");
        List<Vocabulary> foundHon = vocabularyRepository.findAllByJapaneseTextContainingKanji(escapedHon);
        assertThat(foundHon).hasSize(2);

        // Escape "%": chỉ tìm đúng từ chứa ký tự '%' thực tế, không match toàn bộ bảng
        String escapedPercent = KanjiExtractor.escapeLikePattern("%");
        List<Vocabulary> foundPercent = vocabularyRepository.findAllByJapaneseTextContainingKanji(escapedPercent);
        assertThat(foundPercent).hasSize(1);
        assertThat(foundPercent.get(0).getJapaneseText()).isEqualTo("100%（ひゃくパーセント）");

        // Escape "_": chỉ tìm đúng từ chứa ký tự '_' thực tế, không match ký tự đơn bất kỳ
        String escapedUnderscore = KanjiExtractor.escapeLikePattern("_");
        List<Vocabulary> foundUnderscore = vocabularyRepository.findAllByJapaneseTextContainingKanji(escapedUnderscore);
        assertThat(foundUnderscore).hasSize(1);
        assertThat(foundUnderscore.get(0).getJapaneseText()).isEqualTo("A_B");
    }

    @Test
    @DisplayName("F58: VocabularyRepository truy vấn phân trang với Pageable giới hạn tối đa 50 mỗi trang")
    void testFindByJapaneseTextContainingKanji_pagination() {
        for (int i = 0; i < 25; i++) {
            createVocab("魚" + i, "さかな" + i, "Cá " + i);
        }

        String escaped = KanjiExtractor.escapeLikePattern("魚");
        Page<Vocabulary> page1 = vocabularyRepository.findByJapaneseTextContainingKanji(escaped, PageRequest.of(0, 10));
        assertThat(page1.getTotalElements()).isEqualTo(25);
        assertThat(page1.getContent()).hasSize(10);

        Page<Vocabulary> page3 = vocabularyRepository.findByJapaneseTextContainingKanji(escaped, PageRequest.of(2, 10));
        assertThat(page3.getContent()).hasSize(5);
    }
}

