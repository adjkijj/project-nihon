package com.nihon.service;

import com.nihon.dto.TopicDetailDto;
import com.nihon.dto.TopicForm;
import com.nihon.entity.KanjiEntry;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.repository.KanjiEntryRepository;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VocabularyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(TopicService.class)
class TopicServiceTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private VocabularyRepository vocabularyRepository;

    @Autowired
    private KanjiEntryRepository kanjiEntryRepository;

    @Autowired
    private TopicService topicService;

    @Test
    @DisplayName("1. Tạo topic gốc trùng tên khác hoa thường bị chặn")
    void createRootTopic_duplicateNameIgnoreCase_throwsIllegalArgumentException() {
        TopicForm form1 = new TopicForm();
        form1.setName("  Chào   Hỏi\u200B  ");
        Topic saved1 = topicService.create(form1);
        assertThat(saved1.getName()).isEqualTo("Chào Hỏi");

        TopicForm form2 = new TopicForm();
        form2.setName("chào hỏi");

        assertThatThrownBy(() -> topicService.create(form2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tên chủ đề đã tồn tại trong cùng nhóm.");
    }

    @Test
    @DisplayName("2. Tạo topic con trùng tên khác hoa thường trong cùng cha bị chặn, nhưng ở hai cha khác nhau được phép")
    void createChildTopic_duplicateInSameParentBlocked_sameNameInDifferentParentAllowed() {
        TopicForm parentAForm = new TopicForm();
        parentAForm.setName("Nhóm A");
        Topic parentA = topicService.create(parentAForm);

        TopicForm parentBForm = new TopicForm();
        parentBForm.setName("Nhóm B");
        Topic parentB = topicService.create(parentBForm);

        // Tạo topic con "Bài 1" dưới Nhóm A
        TopicForm childA1 = new TopicForm();
        childA1.setName("Bài 1");
        childA1.setParentId(parentA.getId());
        Topic savedChildA1 = topicService.create(childA1);
        assertThat(savedChildA1.getId()).isNotNull();

        // Tạo tiếp "bài 1" (khác hoa thường) dưới cùng Nhóm A -> bị chặn
        TopicForm childA2Duplicate = new TopicForm();
        childA2Duplicate.setName("  BÀI 1  ");
        childA2Duplicate.setParentId(parentA.getId());
        assertThatThrownBy(() -> topicService.create(childA2Duplicate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tên chủ đề đã tồn tại trong cùng nhóm.");

        // Tạo "Bài 1" dưới Nhóm B (cha khác) -> được phép
        TopicForm childB1 = new TopicForm();
        childB1.setName("Bài 1");
        childB1.setParentId(parentB.getId());
        Topic savedChildB1 = topicService.create(childB1);
        assertThat(savedChildB1.getId()).isNotNull();
        assertThat(savedChildB1.getParent().getId()).isEqualTo(parentB.getId());
    }

    @Test
    @DisplayName("3. Sửa topic giữ nguyên tên của chính nó vẫn lưu thành công, đổi sang tên trùng trong cùng cha bị chặn")
    void updateTopic_sameNameOnSelfSucceeds_duplicateSiblingBlocked() {
        TopicForm f1 = new TopicForm();
        f1.setName("Chủ đề 1");
        Topic t1 = topicService.create(f1);

        TopicForm f2 = new TopicForm();
        f2.setName("Chủ đề 2");
        Topic t2 = topicService.create(f2);

        // Sửa t1 giữ nguyên tên (hoặc chỉ đổi hoa/thường của chính nó) -> thành công
        TopicForm updateSelf = new TopicForm();
        updateSelf.setName("Chủ đề 1");
        updateSelf.setKanjiSection(true);
        Topic updated = topicService.update(t1.getId(), updateSelf);
        assertThat(updated.isKanjiSection()).isTrue();

        // Đổi tên t2 thành "chủ đề 1" (trùng với t1 cùng nhóm gốc) -> bị chặn
        TopicForm updateDuplicate = new TopicForm();
        updateDuplicate.setName("chủ đề 1");
        assertThatThrownBy(() -> topicService.update(t2.getId(), updateDuplicate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tên chủ đề đã tồn tại trong cùng nhóm.");
    }

    @Test
    @DisplayName("4. TopicService.create tính sortOrder = findMaxSortOrder() + 1 thay vì count() + 1")
    void createTopic_usesFindMaxSortOrderPlusOne() {
        Topic existing = new Topic();
        existing.setName("Chủ đề có sẵn");
        existing.setSortOrder(10);
        existing.setKanjiSection(false);
        em.persistAndFlush(existing);

        TopicForm form = new TopicForm();
        form.setName("Chủ đề mới");
        Topic created = topicService.create(form);

        assertThat(created.getSortOrder()).isEqualTo(11);
    }

    @Test
    @DisplayName("5. Chọn chủ đề con làm cha bị chặn (cây chỉ có 2 cấp)")
    void createAndUpdateTopic_selectingChildTopicAsParent_isBlocked() {
        TopicForm rootForm = new TopicForm();
        rootForm.setName("Chủ đề gốc");
        Topic root = topicService.create(rootForm);

        TopicForm childForm = new TopicForm();
        childForm.setName("Chủ đề con cấp 2");
        childForm.setParentId(root.getId());
        Topic child = topicService.create(childForm);

        // Tạo chủ đề cấp 3 có cha là child -> bị chặn
        TopicForm grandChildForm = new TopicForm();
        grandChildForm.setName("Chủ đề cấp 3");
        grandChildForm.setParentId(child.getId());

        assertThatThrownBy(() -> topicService.create(grandChildForm))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Chủ đề cha phải là chủ đề gốc.");

        // Sửa một chủ đề gốc khác để trỏ parentId vào child -> cũng bị chặn
        TopicForm otherRootForm = new TopicForm();
        otherRootForm.setName("Chủ đề gốc 2");
        Topic otherRoot = topicService.create(otherRootForm);

        TopicForm updateToGrandChild = topicService.toForm(otherRoot);
        updateToGrandChild.setParentId(child.getId());

        assertThatThrownBy(() -> topicService.update(otherRoot.getId(), updateToGrandChild))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Chủ đề cha phải là chủ đề gốc.");
    }

    @Test
    @DisplayName("6. Chủ đề đang có chủ đề con chuyển thành con của chủ đề khác bị chặn")
    void updateTopic_topicWithChildrenCannotBecomeChild() {
        TopicForm rootAForm = new TopicForm();
        rootAForm.setName("Gốc A");
        Topic rootA = topicService.create(rootAForm);

        TopicForm rootBForm = new TopicForm();
        rootBForm.setName("Gốc B");
        Topic rootB = topicService.create(rootBForm);

        TopicForm childAForm = new TopicForm();
        childAForm.setName("Con của A");
        childAForm.setParentId(rootA.getId());
        topicService.create(childAForm);

        // Chuyển rootA (đang có con) thành con của rootB -> bị chặn
        TopicForm updateA = topicService.toForm(rootA);
        updateA.setParentId(rootB.getId());

        assertThatThrownBy(() -> topicService.update(rootA.getId(), updateA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Chủ đề đang có chủ đề con nên không thể chuyển thành chủ đề con.");
    }

    @Test
    @DisplayName("7. Đổi kanjiSection của chủ đề gốc cập nhật kanjiSection của mọi chủ đề con trong cùng transaction")
    void updateRootTopic_changingKanjiSection_updatesAllChildren() {
        TopicForm rootForm = new TopicForm();
        rootForm.setName("Nhóm Kanji");
        rootForm.setKanjiSection(false);
        Topic root = topicService.create(rootForm);

        TopicForm child1Form = new TopicForm();
        child1Form.setName("Bài 1");
        child1Form.setParentId(root.getId());
        child1Form.setKanjiSection(true); // Dù gửi true, con vẫn phải lấy theo cha (false)
        Topic child1 = topicService.create(child1Form);
        assertThat(child1.isKanjiSection()).isFalse();

        TopicForm child2Form = new TopicForm();
        child2Form.setName("Bài 2");
        child2Form.setParentId(root.getId());
        Topic child2 = topicService.create(child2Form);
        assertThat(child2.isKanjiSection()).isFalse();

        // Đổi kanjiSection của root thành true -> cả child1 và child2 đều lên true
        TopicForm updateRoot = topicService.toForm(root);
        updateRoot.setKanjiSection(true);
        topicService.update(root.getId(), updateRoot);

        assertThat(topicRepository.findById(child1.getId()).orElseThrow().isKanjiSection()).isTrue();
        assertThat(topicRepository.findById(child2.getId()).orElseThrow().isKanjiSection()).isTrue();
    }

    @Test
    @DisplayName("8. Move UP, DOWN đúng thứ tự trong cùng cấp; ở đầu và cuối danh sách không lỗi; direction sai giá trị báo lỗi")
    void moveTopic_upAndDownSwapsOrder_atBoundaryDoesNotError_invalidDirectionThrows() {
        TopicForm f1 = new TopicForm();
        f1.setName("Bài 1");
        Topic t1 = topicService.create(f1);

        TopicForm f2 = new TopicForm();
        f2.setName("Bài 2");
        Topic t2 = topicService.create(f2);

        TopicForm f3 = new TopicForm();
        f3.setName("Bài 3");
        Topic t3 = topicService.create(f3);

        // t1 đang ở đầu danh sách -> move UP trả về false và không lỗi, thứ tự giữ nguyên
        boolean movedAtTop = topicService.move(t1.getId(), "UP");
        assertThat(movedAtTop).isFalse();
        assertThat(topicService.findRootTopics()).extracting(Topic::getId)
                .containsExactly(t1.getId(), t2.getId(), t3.getId());

        // t3 đang ở cuối danh sách -> move DOWN trả về false và không lỗi
        boolean movedAtBottom = topicService.move(t3.getId(), "DOWN");
        assertThat(movedAtBottom).isFalse();

        // Move t2 UP -> thứ tự thành [t2, t1, t3]
        boolean movedUp = topicService.move(t2.getId(), "UP");
        assertThat(movedUp).isTrue();
        assertThat(topicService.findRootTopics()).extracting(Topic::getId)
                .containsExactly(t2.getId(), t1.getId(), t3.getId());

        // Move t1 DOWN -> thứ tự thành [t2, t3, t1]
        boolean movedDown = topicService.move(t1.getId(), "DOWN");
        assertThat(movedDown).isTrue();
        assertThat(topicService.findRootTopics()).extracting(Topic::getId)
                .containsExactly(t2.getId(), t3.getId(), t1.getId());

        // Direction sai giá trị -> ném IllegalArgumentException
        assertThatThrownBy(() -> topicService.move(t1.getId(), "LEFT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UP hoặc DOWN");
    }

    @Test
    @DisplayName("9. moveContent: đích khác loại bị từ chối; nguồn và đích giống nhau bị từ chối; từ trùng ở đích được gộp và sau đó xóa được nguồn")
    void moveContent_validatesTargetAndMergesDuplicatesThenAllowsDelete() {
        TopicForm sourceForm = new TopicForm();
        sourceForm.setName("Chủ đề nguồn");
        sourceForm.setKanjiSection(false);
        Topic source = topicService.create(sourceForm);

        TopicForm targetForm = new TopicForm();
        targetForm.setName("Chủ đề đích cùng loại");
        targetForm.setKanjiSection(false);
        Topic target = topicService.create(targetForm);

        TopicForm kanjiTopicForm = new TopicForm();
        kanjiTopicForm.setName("Chủ đề Kanji khác loại");
        kanjiTopicForm.setKanjiSection(true);
        Topic kanjiTopic = topicService.create(kanjiTopicForm);

        // 1. Nguồn và đích giống nhau -> bị từ chối
        assertThatThrownBy(() -> topicService.moveContent(source.getId(), source.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("khác chủ đề nguồn");

        // 2. Đích khác loại kanjiSection -> bị từ chối
        assertThatThrownBy(() -> topicService.moveContent(source.getId(), kanjiTopic.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cùng loại");

        // 3. Thêm 2 từ vựng vào source ("水" và "火"), trong đó "水" đã có sẵn ở target
        Vocabulary vSrcDuplicate = new Vocabulary();
        vSrcDuplicate.setTopic(source);
        vSrcDuplicate.setJapaneseText("水（みず）");
        vSrcDuplicate.setReading("みず");
        vSrcDuplicate.setRomaji("mizu");
        vSrcDuplicate.setMeaningVi("Nước");
        vSrcDuplicate.setHasKanji(true);
        em.persistAndFlush(vSrcDuplicate);

        Vocabulary vSrcUnique = new Vocabulary();
        vSrcUnique.setTopic(source);
        vSrcUnique.setJapaneseText("火（ひ）");
        vSrcUnique.setReading("ひ");
        vSrcUnique.setRomaji("hi");
        vSrcUnique.setMeaningVi("Lửa");
        vSrcUnique.setHasKanji(true);
        em.persistAndFlush(vSrcUnique);

        Vocabulary vTargetExisting = new Vocabulary();
        vTargetExisting.setTopic(target);
        vTargetExisting.setJapaneseText("水（みず）");
        vTargetExisting.setReading("みず");
        vTargetExisting.setRomaji("mizu");
        vTargetExisting.setMeaningVi("Nước (ở đích)");
        vTargetExisting.setHasKanji(true);
        em.persistAndFlush(vTargetExisting);

        assertThat(topicService.canDelete(source.getId())).isFalse();
        assertThat(topicService.getCannotDeleteReason(source.getId())).contains("2 từ vựng");

        // Thực hiện moveContent từ source sang target
        TopicService.TopicMoveContentResult result = topicService.moveContent(source.getId(), target.getId());
        assertThat(result.movedVocabCount()).isEqualTo(1);
        assertThat(result.mergedVocabCount()).isEqualTo(1);
        assertThat(result.movedKanjiCount()).isZero();

        // Kiểm tra: ở target có 2 từ ("水" giữ nguyên của đích và "火" chuyển sang), ở source còn 0 từ
        assertThat(vocabularyRepository.countByTopicId(source.getId())).isZero();
        assertThat(vocabularyRepository.countByTopicId(target.getId())).isEqualTo(2);

        // Sau khi chuyển xong, chủ đề nguồn được phép xóa
        assertThat(topicService.canDelete(source.getId())).isTrue();
        topicService.delete(source.getId());
        assertThat(topicRepository.findById(source.getId())).isEmpty();
    }

    @Test
    @DisplayName("10. findDetailById trả về đầy đủ thông tin chi tiết chủ đề và trả về empty khi id không tồn tại")
    void findDetailById_returnsDetailOrEmptyWhenNotFound() {
        TopicForm rootForm = new TopicForm();
        rootForm.setName("Chủ đề Kanji Gốc");
        rootForm.setKanjiSection(true);
        Topic root = topicService.create(rootForm);

        TopicForm childForm = new TopicForm();
        childForm.setName("Kanji Bài 1");
        childForm.setParentId(root.getId());
        Topic child = topicService.create(childForm);

        KanjiEntry k = new KanjiEntry();
        k.setTopic(root);
        k.setCharacter("日");
        k.setHanViet("NHẬT");
        k.setMeaningVi("Mặt trời");
        k.setSourceLine(1);
        em.persistAndFlush(k);

        TopicDetailDto detail = topicService.findDetailById(root.getId()).orElseThrow();
        assertThat(detail.getName()).isEqualTo("Chủ đề Kanji Gốc");
        assertThat(detail.isKanjiSection()).isTrue();
        assertThat(detail.getKanjiCount()).isEqualTo(1);
        assertThat(detail.getChildren()).extracting(Topic::getId).containsExactly(child.getId());
        assertThat(detail.isCanDelete()).isFalse();

        assertThat(topicService.findDetailById(999999L)).isEmpty();
        assertThat(topicService.findDetailById(0L)).isEmpty();
    }
}

