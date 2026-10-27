package com.nihon.service;

import com.nihon.dto.VocabularyForm;
import com.nihon.dto.VocabularyRowDto;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VocabularyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(VocabularyService.class)
class VocabularyServiceTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private VocabularyRepository vocabularyRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private VocabularyService vocabularyService;

    private Topic topicA;
    private Topic topicB;
    private Topic topicC;

    @BeforeEach
    void setUp() {
        topicA = new Topic();
        topicA.setName("Chủ đề A");
        topicA.setSortOrder(1);
        topicA.setSourceLine(10);
        topicA.setKanjiSection(false);
        topicA = em.persistAndFlush(topicA);

        topicB = new Topic();
        topicB.setName("Chủ đề B");
        topicB.setSortOrder(2);
        topicB.setSourceLine(20);
        topicB.setKanjiSection(false);
        topicB = em.persistAndFlush(topicB);

        topicC = new Topic();
        topicC.setName("Chủ đề C");
        topicC.setSortOrder(3);
        topicC.setSourceLine(30);
        topicC.setKanjiSection(false);
        topicC = em.persistAndFlush(topicC);
    }

    private Vocabulary createVocab(Topic topic, String jp, String reading, String romaji, String meaningVi, int line) {
        Vocabulary v = new Vocabulary();
        v.setTopic(topic);
        v.setJapaneseText(jp);
        v.setReading(reading);
        v.setRomaji(romaji);
        v.setMeaningVi(meaningVi);
        v.setHasKanji(true);
        v.setSourceLine(line);
        return em.persistAndFlush(v);
    }

    @Test
    @DisplayName("1. Một từ xuất hiện trong 2 chủ đề chỉ hiển thị một dòng duy nhất")
    void wordInTwoTopics_displaysSingleRow() {
        // Từ "新聞（しんぶん）" xuất hiện ở cả Chủ đề A và Chủ đề B
        createVocab(topicA, "新聞（しんぶん）", "しんぶん", "shinbun", "Báo", 101);
        createVocab(topicB, "新聞（しんぶん）", "しんぶん", "shinbun", "Báo", 201);

        Page<VocabularyRowDto> page = vocabularyService.search(null, null, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getJapaneseText()).isEqualTo("新聞（しんぶん）");
    }

    @Test
    @DisplayName("2. Cột Chủ đề hiển thị đủ cả 2 chủ đề tương ứng")
    void wordInTwoTopics_showsBothTopics() {
        createVocab(topicA, "新聞（しんぶん）", "しんぶん", "shinbun", "Báo", 101);
        createVocab(topicB, "新聞（しんぶん）", "しんぶん", "shinbun", "Báo", 201);

        Page<VocabularyRowDto> page = vocabularyService.search(null, null, PageRequest.of(0, 20));

        VocabularyRowDto row = page.getContent().get(0);
        assertThat(row.getTopics())
                .extracting(Topic::getName)
                .containsExactly("Chủ đề A", "Chủ đề B");
        assertThat(row.getTopicNamesDisplay()).isEqualTo("Chủ đề A, Chủ đề B");
    }

    @Test
    @DisplayName("3. Một từ xuất hiện trong 3 chủ đề vẫn chỉ hiển thị một dòng và không mất chủ đề")
    void wordInThreeTopics_displaysSingleRowWithAllThreeTopics() {
        createVocab(topicA, "本（ほん）", "ほん", "hon", "Sách", 102);
        createVocab(topicB, "本（ほん）", "ほん", "hon", "Sách", 202);
        createVocab(topicC, "本（ほん）", "ほん", "hon", "Sách", 302);

        Page<VocabularyRowDto> page = vocabularyService.search(null, null, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        VocabularyRowDto row = page.getContent().get(0);
        assertThat(row.getTopics())
                .extracting(Topic::getName)
                .containsExactly("Chủ đề A", "Chủ đề B", "Chủ đề C");
        assertThat(row.getTopicNamesDisplay()).isEqualTo("Chủ đề A, Chủ đề B, Chủ đề C");
    }

    @Test
    @DisplayName("4. Hai từ khác nhau nhưng cùng nghĩa tiếng Việt vẫn hiển thị thành hai dòng riêng biệt")
    void differentWordsWithSameMeaning_displayAsSeparateRows() {
        // "私（わたし）" và "僕（ぼく）" đều có nghĩa "Tôi"
        createVocab(topicA, "私（わたし）", "わたし", "watashi", "Tôi", 103);
        createVocab(topicA, "僕（ぼく）", "ぼく", "boku", "Tôi", 104);

        Page<VocabularyRowDto> page = vocabularyService.search(null, null, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent())
                .extracting(VocabularyRowDto::getJapaneseText)
                .containsExactlyInAnyOrder("私（わたし）", "僕（ぼく）");
    }

    @Test
    @DisplayName("5. Tìm kiếm theo tiếng Nhật, cách đọc và nghĩa tiếng Việt vẫn hoạt động chính xác")
    void searchByJapaneseReadingAndMeaning_worksCorrectly() {
        createVocab(topicA, "新聞（しんぶん）", "しんぶん", "shinbun", "Báo", 101);
        createVocab(topicB, "車（くるま）", "くるま", "kuruma", "Xe hơi", 203);

        // Tìm theo tiếng Nhật
        Page<VocabularyRowDto> searchJp = vocabularyService.search(null, "新聞", PageRequest.of(0, 20));
        assertThat(searchJp.getTotalElements()).isEqualTo(1);
        assertThat(searchJp.getContent().get(0).getJapaneseText()).isEqualTo("新聞（しんぶん）");

        // Tìm theo cách đọc
        Page<VocabularyRowDto> searchReading = vocabularyService.search(null, "くるま", PageRequest.of(0, 20));
        assertThat(searchReading.getTotalElements()).isEqualTo(1);
        assertThat(searchReading.getContent().get(0).getJapaneseText()).isEqualTo("車（くるま）");

        // Tìm theo nghĩa tiếng Việt
        Page<VocabularyRowDto> searchMeaning = vocabularyService.search(null, "Báo", PageRequest.of(0, 20));
        assertThat(searchMeaning.getTotalElements()).isEqualTo(1);
        assertThat(searchMeaning.getContent().get(0).getMeaningVi()).isEqualTo("Báo");
    }

    @Test
    @DisplayName("6. Lọc theo một chủ đề chỉ hiển thị những từ thuộc chủ đề đó, nhưng vẫn liệt kê tất cả chủ đề của từng từ")
    void filterByTopic_showsOnlyWordsInThatTopic_butListsAllTopicsOfEachWord() {
        // Từ 1: xuất hiện ở Chủ đề A và Chủ đề B
        createVocab(topicA, "新聞（しんぶん）", "しんぶん", "shinbun", "Báo", 101);
        createVocab(topicB, "新聞（しんぶん）", "しんぶん", "shinbun", "Báo", 201);

        // Từ 2: chỉ xuất hiện ở Chủ đề B
        createVocab(topicB, "車（くるま）", "くるま", "kuruma", "Xe hơi", 204);

        // Lọc theo Chủ đề A: Chỉ hiển thị "新聞（しんぶん）", nhưng danh sách chủ đề vẫn có cả A và B!
        Page<VocabularyRowDto> filteredA = vocabularyService.search(topicA.getId(), null, PageRequest.of(0, 20));
        assertThat(filteredA.getTotalElements()).isEqualTo(1);
        VocabularyRowDto rowA = filteredA.getContent().get(0);
        assertThat(rowA.getJapaneseText()).isEqualTo("新聞（しんぶん）");
        assertThat(rowA.getTopics())
                .extracting(Topic::getName)
                .containsExactly("Chủ đề A", "Chủ đề B");

        // Lọc theo Chủ đề B: Hiển thị cả 2 từ
        Page<VocabularyRowDto> filteredB = vocabularyService.search(topicB.getId(), null, PageRequest.of(0, 20));
        assertThat(filteredB.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("7. Nút Sửa và Xóa hoạt động chính xác với đúng ID")
    void editAndUpdateAndDelete_workAccurately() {
        Vocabulary vA = createVocab(topicA, "新聞（しんぶん）", "しんぶん", "shinbun", "Báo", 101);
        Vocabulary vB = createVocab(topicB, "新聞（しんぶん）", "しんぶん", "shinbun", "Báo", 201);

        // Kiểm tra findById
        assertThat(vocabularyService.findById(vA.getId())).isPresent();

        // Cập nhật từ vựng
        VocabularyForm form = new VocabularyForm();
        form.setId(vA.getId());
        form.setTopicId(topicA.getId());
        form.setJapaneseText("新聞（しんぶん）");
        form.setReading("しんぶん");
        form.setRomaji("shinbun");
        form.setMeaningVi("Tờ báo");
        vocabularyService.update(vA.getId(), form);

        Vocabulary updated = vocabularyRepository.findById(vA.getId()).orElseThrow();
        assertThat(updated.getMeaningVi()).isEqualTo("Tờ báo");

        // Xóa một bản ghi (vA): vB ở topicB vẫn còn nguyên vẹn
        vocabularyService.delete(vA.getId());
        assertThat(vocabularyRepository.findById(vA.getId())).isEmpty();
        assertThat(vocabularyRepository.findById(vB.getId())).isPresent();
    }

    @Test
    @DisplayName("8. Không làm thay đổi dữ liệu gốc trong database khi tìm kiếm/hiển thị")
    void originalDataInDatabaseIsNotAltered() {
        createVocab(topicA, "新聞（しんぶん）", "しんぶん", "shinbun", "Báo", 101);
        createVocab(topicB, "新聞（しんぶん）", "しんぶん", "shinbun", "Báo", 201);

        // Khi tìm kiếm và hiển thị, database vẫn giữ nguyên 2 bản ghi độc lập
        long countBefore = vocabularyRepository.count();
        assertThat(countBefore).isEqualTo(2);

        Page<VocabularyRowDto> page = vocabularyService.search(null, null, PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(1);

        long countAfter = vocabularyRepository.count();
        assertThat(countAfter).isEqualTo(2);
    }
}
