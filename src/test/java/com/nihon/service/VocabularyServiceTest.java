package com.nihon.service;

import com.nihon.dto.VocabularyDetailDto;
import com.nihon.dto.VocabularyForm;
import com.nihon.dto.VocabularyRowDto;
import com.nihon.entity.KanjiEntry;
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

import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    @DisplayName("9. Phân trang với page=Integer.MAX_VALUE không bị tràn số (IndexOutOfBoundsException) và trả danh sách rỗng")
    void searchWithMaxIntPage_doesNotOverflowAndReturnsEmptyPage() {
        createVocab(topicA, "新聞（しんぶん）", "しんぶん", "shinbun", "Báo", 101);

        Page<VocabularyRowDto> page = vocabularyService.search(null, null, PageRequest.of(Integer.MAX_VALUE, 20));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("10. Chuẩn hóa keyword: strip khoảng trắng, giới hạn tối đa 50 ký tự, chuỗi rỗng coi như null")
    void sanitizeKeyword_stripsAndTruncatesTo50Chars() {
        assertThat(vocabularyService.sanitizeKeyword(null)).isNull();
        assertThat(vocabularyService.sanitizeKeyword("")).isNull();
        assertThat(vocabularyService.sanitizeKeyword("   \t\n  ")).isNull();
        assertThat(vocabularyService.sanitizeKeyword("  しんぶん  ")).isEqualTo("しんぶん");

        String longKeyword = "  " + "a".repeat(500) + "  ";
        String sanitized = vocabularyService.sanitizeKeyword(longKeyword);
        assertThat(sanitized).hasSize(50);
        assertThat(sanitized).isEqualTo("a".repeat(50));
    }

    @Test
    @DisplayName("11. findById với id <= 0 trả về Optional.empty()")
    void findById_withZeroOrNegativeId_returnsEmpty() {
        assertThat(vocabularyService.findById(null)).isEmpty();
        assertThat(vocabularyService.findById(0L)).isEmpty();
        assertThat(vocabularyService.findById(-5L)).isEmpty();
    }

    @Test
    @DisplayName("12. Tạo từ trùng trong cùng chủ đề bị chặn kèm thông báo; cùng từ ở hai chủ đề khác nhau được phép")
    void createVocabulary_duplicateInSameTopicBlocked_sameWordInDifferentTopicAllowed() {
        VocabularyForm formA1 = new VocabularyForm();
        formA1.setTopicId(topicA.getId());
        formA1.setJapaneseText("  水（みず）\u200B ");
        formA1.setReading(" みず ");
        formA1.setRomaji(" mizu ");
        formA1.setMeaningVi(" Nước ");
        Vocabulary savedA1 = vocabularyService.create(formA1);
        assertThat(savedA1.getJapaneseText()).isEqualTo("水（みず）");
        assertThat(savedA1.getReading()).isEqualTo("みず");
        assertThat(savedA1.getRomaji()).isEqualTo("mizu");
        assertThat(savedA1.getMeaningVi()).isEqualTo("Nước");

        // Tạo lại cùng từ trong cùng Chủ đề A -> bị chặn
        VocabularyForm formA2Duplicate = new VocabularyForm();
        formA2Duplicate.setTopicId(topicA.getId());
        formA2Duplicate.setJapaneseText("水（みず）");
        formA2Duplicate.setReading("みず");
        assertThatThrownBy(() -> vocabularyService.create(formA2Duplicate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Từ này đã tồn tại trong chủ đề đã chọn.");

        // Tạo cùng từ "水（みず）" trong Chủ đề B -> được phép
        VocabularyForm formB1 = new VocabularyForm();
        formB1.setTopicId(topicB.getId());
        formB1.setJapaneseText("水（みず）");
        formB1.setReading("みず");
        Vocabulary savedB1 = vocabularyService.create(formB1);
        assertThat(savedB1.getId()).isNotNull();
        assertThat(savedB1.getTopic().getId()).isEqualTo(topicB.getId());
    }

    @Test
    @DisplayName("13. Sửa từ mà không đổi gì vẫn lưu thành công, không tự báo trùng với chính nó; đổi sang từ trùng trong cùng chủ đề bị chặn")
    void updateVocabulary_unchangedSelfSucceeds_duplicateInSameTopicBlocked() {
        Vocabulary v1 = createVocab(topicA, "先生（せんせい）", "せんせい", "sensei", "Giáo viên", 110);
        Vocabulary v2 = createVocab(topicA, "学生（がくせい）", "がくせい", "gakusei", "Học sinh", 111);

        // Sửa v1 mà không đổi gì -> lưu thành công
        VocabularyForm unchangedForm = vocabularyService.toForm(v1);
        Vocabulary updatedSelf = vocabularyService.update(v1.getId(), unchangedForm);
        assertThat(updatedSelf.getId()).isEqualTo(v1.getId());
        assertThat(updatedSelf.getJapaneseText()).isEqualTo("先生（せんせい）");

        // Đổi japaneseText của v2 thành "先生（せんせい）" trong cùng topicA -> bị chặn
        VocabularyForm duplicateForm = vocabularyService.toForm(v2);
        duplicateForm.setJapaneseText("先生（せんせい）");
        assertThatThrownBy(() -> vocabularyService.update(v2.getId(), duplicateForm))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Từ này đã tồn tại trong chủ đề đã chọn.");
    }

    @Test
    @DisplayName("14. Từ có Kanji nhưng chưa có cách đọc vẫn cho lưu thành công")
    void createVocabulary_kanjiWithoutReading_savesSuccessfully() {
        VocabularyForm form = new VocabularyForm();
        form.setTopicId(topicA.getId());
        form.setJapaneseText("日本");
        form.setReading("   ");
        form.setRomaji(null);
        form.setMeaningVi("Nhật Bản");

        Vocabulary saved = vocabularyService.create(form);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.isHasKanji()).isTrue();
        assertThat(saved.getReading()).isNull();
    }

    @Test
    @DisplayName("15. Từ có 3 bản ghi ở 3 chủ đề: lọc theo chủ đề A rồi xóa (deleteInTopic) thì còn 2 bản ghi")
    void deleteInTopic_wordInThreeTopics_filteredByTopicA_leavesTwoRecords() {
        createVocab(topicA, "店（みせ）", "みせ", "mise", "Cửa hàng", 101);
        Vocabulary vB = createVocab(topicB, "店（みせ）", "みせ", "mise", "Cửa hàng", 201);
        Vocabulary vC = createVocab(topicC, "店（みせ）", "みせ", "mise", "Cửa hàng", 301);

        // Lọc theo Chủ đề A -> lấy id đại diện của Chủ đề A
        Page<VocabularyRowDto> filteredA = vocabularyService.search(topicA.getId(), null, PageRequest.of(0, 20));
        assertThat(filteredA.getTotalElements()).isEqualTo(1);
        Long repIdInTopicA = filteredA.getContent().get(0).getId();

        int deletedCount = vocabularyService.deleteInTopic(repIdInTopicA);

        assertThat(deletedCount).isEqualTo(1);
        assertThat(vocabularyRepository.count()).isEqualTo(2);
        assertThat(vocabularyRepository.findById(repIdInTopicA)).isEmpty();
        assertThat(vocabularyRepository.findById(vB.getId())).isPresent();
        assertThat(vocabularyRepository.findById(vC.getId())).isPresent();
    }

    @Test
    @DisplayName("16. Từ có 3 bản ghi ở 3 chủ đề: không lọc rồi xóa (deleteGroup) thì còn 0 bản ghi")
    void deleteGroup_wordInThreeTopics_notFiltered_leavesZeroRecords() {
        createVocab(topicA, "店（みせ）", "みせ", "mise", "Cửa hàng", 101);
        createVocab(topicB, "店（みせ）", "みせ", "mise", "Cửa hàng", 201);
        createVocab(topicC, "店（みせ）", "みせ", "mise", "Cửa hàng", 301);

        Page<VocabularyRowDto> unfiltered = vocabularyService.search(null, null, PageRequest.of(0, 20));
        assertThat(unfiltered.getTotalElements()).isEqualTo(1);
        VocabularyRowDto row = unfiltered.getContent().get(0);
        assertThat(row.getAllIds()).hasSize(3);

        int deletedCount = vocabularyService.deleteGroup(row.getId(), row.getAllIds());

        assertThat(deletedCount).isEqualTo(3);
        assertThat(vocabularyRepository.count()).isZero();
    }

    @Test
    @DisplayName("17. Sửa nghĩa khi không lọc (updateGroup): cả 3 bản ghi đổi nghĩa và vẫn hiện thành một hàng")
    void updateGroup_editMeaningWhenNotFiltered_updatesAllThreeAndDisplaysAsSingleRow() {
        Vocabulary vA = createVocab(topicA, "店（みせ）", "みせ", "mise", "Cửa hàng", 101);
        Vocabulary vB = createVocab(topicB, "店（みせ）", "みせ", "mise", "Cửa hàng", 201);
        Vocabulary vC = createVocab(topicC, "店（みせ）", "みせ", "mise", "Cửa hàng", 301);

        VocabularyForm form = vocabularyService.toForm(vA);
        form.setScope("ALL");
        form.setMeaningVi("Tiệm, cửa tiệm");

        List<Vocabulary> updated = vocabularyService.updateGroup(vA.getId(), form);

        assertThat(updated).hasSize(3);
        assertThat(vocabularyRepository.findById(vA.getId()).orElseThrow().getMeaningVi()).isEqualTo("Tiệm, cửa tiệm");
        assertThat(vocabularyRepository.findById(vB.getId()).orElseThrow().getMeaningVi()).isEqualTo("Tiệm, cửa tiệm");
        assertThat(vocabularyRepository.findById(vC.getId()).orElseThrow().getMeaningVi()).isEqualTo("Tiệm, cửa tiệm");

        // Khi tìm kiếm lại vẫn gom thành 1 hàng duy nhất với đủ 3 chủ đề
        Page<VocabularyRowDto> page = vocabularyService.search(null, null, PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(1);
        VocabularyRowDto row = page.getContent().get(0);
        assertThat(row.getMeaningVi()).isEqualTo("Tiệm, cửa tiệm");
        assertThat(row.getTopics()).extracting(Topic::getName)
                .containsExactly("Chủ đề A", "Chủ đề B", "Chủ đề C");
    }

    @Test
    @DisplayName("18. Gửi allIds chứa ID của từ không liên quan: ID đó bị bỏ qua và không bị xóa hay sửa")
    void deleteGroupAndUpdateGroup_withUnrelatedIdInAllIds_ignoresUnrelatedId() {
        Vocabulary vA = createVocab(topicA, "店（みせ）", "みせ", "mise", "Cửa hàng", 101);
        Vocabulary vB = createVocab(topicB, "店（みせ）", "みせ", "mise", "Cửa hàng", 201);
        Vocabulary unrelated = createVocab(topicC, "車（くるま）", "くるま", "kuruma", "Xe hơi", 301);

        // 1. Thử sửa nhóm kèm ID không liên quan trong allIds -> từ không liên quan giữ nguyên
        VocabularyForm form = vocabularyService.toForm(vA);
        form.setMeaningVi("Quán, cửa hàng");
        form.setAllIds(List.of(vA.getId(), vB.getId(), unrelated.getId(), 999999L));

        List<Vocabulary> updated = vocabularyService.updateGroup(vA.getId(), form);
        assertThat(updated).hasSize(2);
        assertThat(vocabularyRepository.findById(unrelated.getId()).orElseThrow().getMeaningVi()).isEqualTo("Xe hơi");

        // 2. Thử xóa nhóm kèm ID không liên quan trong allIds -> từ không liên quan không bị xóa
        int deletedCount = vocabularyService.deleteGroup(
                vA.getId(), List.of(vA.getId(), vB.getId(), unrelated.getId(), 999999L));

        assertThat(deletedCount).isEqualTo(2);
        assertThat(vocabularyRepository.findById(vA.getId())).isEmpty();
        assertThat(vocabularyRepository.findById(vB.getId())).isEmpty();
        assertThat(vocabularyRepository.findById(unrelated.getId())).isPresent();
    }

    @Test
    @DisplayName("19. Một bản ghi trong nhóm vi phạm ràng buộc trùng khi sửa: dừng toàn bộ, không bản ghi nào trong nhóm bị đổi")
    void updateGroup_whenOneMemberViolatesDuplicateConstraint_noRecordIsChanged() {
        Vocabulary vA = createVocab(topicA, "店（みせ）", "みせ", "mise", "Cửa hàng", 101);
        Vocabulary vB = createVocab(topicB, "店（みせ）", "みせ", "mise", "Cửa hàng", 201);
        Vocabulary vC = createVocab(topicC, "店（みせ）", "みせ", "mise", "Cửa hàng", 301);

        // Chủ đề C đã có sẵn từ "駅（えき）"
        createVocab(topicC, "駅（えき）", "えき", "eki", "Nhà ga", 302);

        // Sửa nhóm "店（みせ）" thành "駅（えき）" -> vA (topicA) và vB (topicB) hợp lệ nhưng vC (topicC) bị trùng!
        VocabularyForm form = vocabularyService.toForm(vA);
        form.setJapaneseText("駅（えき）");
        form.setReading("えき");
        form.setRomaji("eki");
        form.setMeaningVi("Nhà ga");

        assertThatThrownBy(() -> vocabularyService.updateGroup(vA.getId(), form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Từ này đã tồn tại trong chủ đề đã chọn.");

        em.clear();

        // Không bản ghi nào trong nhóm bị đổi
        Vocabulary unchangedA = vocabularyRepository.findById(vA.getId()).orElseThrow();
        Vocabulary unchangedB = vocabularyRepository.findById(vB.getId()).orElseThrow();
        Vocabulary unchangedC = vocabularyRepository.findById(vC.getId()).orElseThrow();

        assertThat(unchangedA.getJapaneseText()).isEqualTo("店（みせ）");
        assertThat(unchangedA.getMeaningVi()).isEqualTo("Cửa hàng");
        assertThat(unchangedB.getJapaneseText()).isEqualTo("店（みせ）");
        assertThat(unchangedB.getMeaningVi()).isEqualTo("Cửa hàng");
        assertThat(unchangedC.getJapaneseText()).isEqualTo("店（みせ）");
        assertThat(unchangedC.getMeaningVi()).isEqualTo("Cửa hàng");
    }

    @Test
    @DisplayName("20. Chi tiết từ thuộc 2 chủ đề hiện cả 2 chủ đề và liên kết Kanji")
    void findDetailById_wordBelongingToTwoTopics_returnsBothTopicsAndExtractedKanjis() {
        Vocabulary vA = createVocab(topicA, "先生（せんせい）", "せんせい", "sensei", "Giáo viên", 101);
        Vocabulary vB = createVocab(topicB, "先生（せんせい）", "せんせい", "sensei", "Giáo viên", 201);

        KanjiEntry kSen = new KanjiEntry();
        kSen.setTopic(topicA);
        kSen.setCharacter("先");
        kSen.setHanViet("TIÊN");
        kSen.setMeaningVi("Trước");
        kSen.setSourceLine(500);
        kSen = em.persistAndFlush(kSen);

        VocabularyDetailDto detail = vocabularyService.findDetailById(vA.getId()).orElseThrow();

        assertThat(detail.getJapaneseText()).isEqualTo("先生（せんせい）");
        assertThat(detail.getTopics()).extracting(Topic::getName)
                .containsExactly("Chủ đề A", "Chủ đề B");
        assertThat(detail.getAllIds()).containsExactlyInAnyOrder(vA.getId(), vB.getId());
        assertThat(detail.getKanjis()).hasSize(2);
        assertThat(detail.getKanjis().get(0).getCharacter()).isEqualTo("先");
        assertThat(detail.getKanjis().get(0).getKanjiId()).isEqualTo(kSen.getId());
        assertThat(detail.getKanjis().get(1).getCharacter()).isEqualTo("生");
        assertThat(detail.getKanjis().get(1).getKanjiId()).isNull();
    }

    @Test
    @DisplayName("21. Xóa hàng loạt 3 từ thành công; 201 ID bị từ chối")
    void bulkDelete_threeWordsSucceeds_and201IdsRejected() {
        Vocabulary v1 = createVocab(topicA, "一（いち）", "いち", "ichi", "Một", 101);
        Vocabulary v2 = createVocab(topicA, "二（に）", "に", "ni", "Hai", 102);
        Vocabulary v3 = createVocab(topicB, "三（さん）", "さん", "san", "Ba", 201);

        VocabularyService.BulkDeleteResult result = vocabularyService.bulkDelete(
                List.of(v1.getId(), v2.getId(), v3.getId()));

        assertThat(result.deletedCount()).isEqualTo(3);
        assertThat(result.skippedCount()).isZero();
        assertThat(vocabularyRepository.findById(v1.getId())).isEmpty();
        assertThat(vocabularyRepository.findById(v2.getId())).isEmpty();
        assertThat(vocabularyRepository.findById(v3.getId())).isEmpty();

        List<Long> ids201 = LongStream.rangeClosed(1, 201).boxed().toList();
        assertThatThrownBy(() -> vocabularyService.bulkDelete(ids201))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("200");
    }

    @Test
    @DisplayName("22. Chuyển hàng loạt có từ trùng ở chủ đề đích được gộp đúng")
    void bulkMove_withDuplicateWordInTargetTopic_mergesByDeletingSourceAndReportsCounts() {
        Vocabulary vDupInA = createVocab(topicA, "店（みせ）", "みせ", "mise", "Cửa hàng", 101);
        Vocabulary vUniqueInA = createVocab(topicA, "車（くるま）", "くるま", "kuruma", "Xe hơi", 102);
        Vocabulary existingInB = createVocab(topicB, "店（みせ）", "みせ", "mise", "Cửa hàng", 201);

        VocabularyService.BulkMoveResult result = vocabularyService.bulkMove(
                List.of(vDupInA.getId(), vUniqueInA.getId()), topicB.getId());

        assertThat(result.movedCount()).isEqualTo(1);
        assertThat(result.mergedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isZero();

        // Bản ghi nguồn bị trùng được xóa (gộp vào bản ghi ở đích)
        assertThat(vocabularyRepository.findById(vDupInA.getId())).isEmpty();
        assertThat(vocabularyRepository.findById(existingInB.getId())).isPresent();

        // Bản ghi không trùng được chuyển sang chủ đề B
        Vocabulary moved = vocabularyRepository.findById(vUniqueInA.getId()).orElseThrow();
        assertThat(moved.getTopic().getId()).isEqualTo(topicB.getId());
    }

    @Test
    @DisplayName("23. Danh sách ID có số không hợp lệ hoặc không tồn tại được bỏ qua, không lỗi")
    void bulkOperations_withInvalidAndNonExistentIds_skipsWithoutError() {
        Vocabulary v1 = createVocab(topicA, "水（みず）", "みず", "mizu", "Nước", 101);

        VocabularyService.BulkMoveResult moveResult = vocabularyService.bulkMove(
                Arrays.asList(v1.getId(), -5L, 0L, 999999L, null), topicB.getId());

        assertThat(moveResult.movedCount()).isEqualTo(1);
        assertThat(moveResult.mergedCount()).isZero();
        assertThat(moveResult.skippedCount()).isEqualTo(4);
        assertThat(vocabularyRepository.findById(v1.getId()).orElseThrow().getTopic().getId()).isEqualTo(topicB.getId());

        VocabularyService.BulkDeleteResult deleteResult = vocabularyService.bulkDelete(
                Arrays.asList(v1.getId(), -10L, 888888L));

        assertThat(deleteResult.deletedCount()).isEqualTo(1);
        assertThat(deleteResult.skippedCount()).isEqualTo(2);
        assertThat(vocabularyRepository.findById(v1.getId())).isEmpty();
    }

    @Test
    @DisplayName("24. F58: findWordsContainingKanji gộp từ trùng qua nhiều chủ đề và phân trang an toàn")
    void findWordsContainingKanji_deduplicatesAcrossTopics_andPaginatesSafely() {
        // v1 và v2 là cùng một từ "先生（せんせい）" ở 2 chủ đề khác nhau
        createVocab(topicA, "先生（せんせい）", "せんせい", "sensei", "Giáo viên", 101);
        createVocab(topicB, "先生（せんせい）", "せんせい", "sensei", "Giáo viên", 201);

        // v3 là từ khác "先輩（せんぱい）" chỉ ở chủ đề A
        createVocab(topicA, "先輩（せんぱい）", "せんぱい", "senpai", "Tiền bối", 102);

        // v4 là từ không chứa chữ "先": "水（みず）"
        createVocab(topicC, "水（みず）", "みず", "mizu", "Nước", 301);

        Page<VocabularyRowDto> page = vocabularyService.findWordsContainingKanji("先", 0, 20);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(2);

        VocabularyRowDto first = page.getContent().stream()
                .filter(r -> "先生（せんせい）".equals(r.getJapaneseText()))
                .findFirst().orElseThrow();
        assertThat(first.getTopics()).extracting(Topic::getName)
                .containsExactly("Chủ đề A", "Chủ đề B");

        // Test phân trang biên hợp lệ
        Page<VocabularyRowDto> emptyPage = vocabularyService.findWordsContainingKanji("先", 100, 20);
        assertThat(emptyPage.getContent()).isEmpty();
        assertThat(emptyPage.getTotalElements()).isEqualTo(2);

        // Test ký tự rỗng/null
        Page<VocabularyRowDto> blankPage = vocabularyService.findWordsContainingKanji("", 0, 20);
        assertThat(blankPage.getContent()).isEmpty();
    }

    @Test
    @DisplayName("25. F21: search với VocabQuickFilter lọc chính xác (HAS_KANJI, NO_READING, NO_MEANING)")
    void search_withQuickFilters_filtersCorrectly() {
        // Từ 1: có Kanji, có reading, có meaning
        Vocabulary v1 = new Vocabulary();
        v1.setTopic(topicA);
        v1.setJapaneseText("私（わたし）");
        v1.setReading("わたし");
        v1.setRomaji("watashi");
        v1.setMeaningVi("Tôi");
        v1.setHasKanji(true);
        v1.setSourceLine(1);
        em.persistAndFlush(v1);

        // Từ 2: không có Kanji (chỉ kana), có reading, có meaning
        Vocabulary v2 = new Vocabulary();
        v2.setTopic(topicA);
        v2.setJapaneseText("ここ");
        v2.setReading("ここ");
        v2.setRomaji("koko");
        v2.setMeaningVi("Ở đây");
        v2.setHasKanji(false);
        v2.setSourceLine(2);
        em.persistAndFlush(v2);

        // Từ 3: có Kanji, thiếu reading
        Vocabulary v3 = new Vocabulary();
        v3.setTopic(topicA);
        v3.setJapaneseText("東京");
        v3.setReading(null);
        v3.setRomaji("toukyou");
        v3.setMeaningVi("Tokyo");
        v3.setHasKanji(true);
        v3.setSourceLine(3);
        em.persistAndFlush(v3);

        // Từ 4: có Kanji, thiếu meaning
        Vocabulary v4 = new Vocabulary();
        v4.setTopic(topicA);
        v4.setJapaneseText("京都（きょうと）");
        v4.setReading("きょうと");
        v4.setRomaji("kyouto");
        v4.setMeaningVi(null);
        v4.setHasKanji(true);
        v4.setSourceLine(4);
        em.persistAndFlush(v4);

        // Test filter ALL
        Page<VocabularyRowDto> allPage = vocabularyService.search(topicA.getId(), null, com.nihon.dto.VocabQuickFilter.ALL, com.nihon.dto.VocabViewMode.COMPACT, PageRequest.of(0, 20));
        assertThat(allPage.getTotalElements()).isEqualTo(4);

        // Test filter HAS_KANJI
        Page<VocabularyRowDto> hasKanjiPage = vocabularyService.search(topicA.getId(), null, com.nihon.dto.VocabQuickFilter.HAS_KANJI, com.nihon.dto.VocabViewMode.COMPACT, PageRequest.of(0, 20));
        assertThat(hasKanjiPage.getTotalElements()).isEqualTo(3);
        assertThat(hasKanjiPage.getContent()).allMatch(VocabularyRowDto::isHasKanji);

        // Test filter NO_READING
        Page<VocabularyRowDto> noReadingPage = vocabularyService.search(topicA.getId(), null, com.nihon.dto.VocabQuickFilter.NO_READING, com.nihon.dto.VocabViewMode.COMPACT, PageRequest.of(0, 20));
        assertThat(noReadingPage.getTotalElements()).isEqualTo(1);
        assertThat(noReadingPage.getContent().get(0).getJapaneseText()).isEqualTo("東京");

        // Test filter NO_MEANING
        Page<VocabularyRowDto> noMeaningPage = vocabularyService.search(topicA.getId(), null, com.nihon.dto.VocabQuickFilter.NO_MEANING, com.nihon.dto.VocabViewMode.COMPACT, PageRequest.of(0, 20));
        assertThat(noMeaningPage.getTotalElements()).isEqualTo(1);
        assertThat(noMeaningPage.getContent().get(0).getJapaneseText()).isEqualTo("京都（きょうと）");
    }

    @Test
    @DisplayName("26. F21: search với viewMode=FULL nạp đầy đủ thông tin Kanji cấu thành và âm Hán Việt")
    void search_withViewModeFull_populatesConstituentKanji() {
        // Tạo KanjiEntry trong bảng kanji_entry
        KanjiEntry kanjiFish = new KanjiEntry();
        kanjiFish.setTopic(topicA);
        kanjiFish.setCharacter("魚");
        kanjiFish.setHanViet("NGƯ");
        kanjiFish.setOnyomi("ギョ");
        kanjiFish.setKunyomi("さかな");
        kanjiFish.setMeaningVi("Cá");
        kanjiFish.setSourceLine(10);
        em.persistAndFlush(kanjiFish);

        createVocab(topicA, "魚（さかな）", "さかな", "sakana", "Con cá", 101);

        // Khi viewMode = COMPACT, constituentKanji rỗng
        Page<VocabularyRowDto> compactPage = vocabularyService.search(topicA.getId(), "魚", com.nihon.dto.VocabQuickFilter.ALL, com.nihon.dto.VocabViewMode.COMPACT, PageRequest.of(0, 20));
        assertThat(compactPage.getContent().get(0).getConstituentKanji()).isEmpty();

        // Khi viewMode = FULL, constituentKanji được nạp thông tin âm Hán Việt từ kanji_entry
        Page<VocabularyRowDto> fullPage = vocabularyService.search(topicA.getId(), "魚", com.nihon.dto.VocabQuickFilter.ALL, com.nihon.dto.VocabViewMode.FULL, PageRequest.of(0, 20));
        assertThat(fullPage.getContent().get(0).getConstituentKanji()).isNotEmpty();

        com.nihon.dto.ConstituentKanjiDto kDto = fullPage.getContent().get(0).getConstituentKanji().get(0);
        assertThat(kDto.getCharacter()).isEqualTo("魚");
        assertThat(kDto.getHanViet()).isEqualTo("NGƯ");
        assertThat(kDto.isExistsInKanjiEntry()).isTrue();
        assertThat(fullPage.getContent().get(0).getConstituentHanVietDisplay()).isEqualTo("NGƯ");
    }
}
