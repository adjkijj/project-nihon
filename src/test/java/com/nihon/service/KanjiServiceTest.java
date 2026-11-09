package com.nihon.service;

import com.nihon.dto.KanjiDetailDto;
import com.nihon.dto.KanjiForm;
import com.nihon.entity.KanjiEntry;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.repository.KanjiEntryRepository;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(KanjiService.class)
class KanjiServiceTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private KanjiEntryRepository kanjiEntryRepository;

    @Autowired
    private VocabularyRepository vocabularyRepository;

    @Autowired
    private KanjiService kanjiService;

    private Topic kanjiTopic;
    private Topic normalVocabTopic;

    @BeforeEach
    void setUp() {
        kanjiTopic = new Topic();
        kanjiTopic.setName("Kanji Bài 5");
        kanjiTopic.setSortOrder(1);
        kanjiTopic.setSourceLine(100);
        kanjiTopic.setKanjiSection(true);
        kanjiTopic = em.persistAndFlush(kanjiTopic);

        normalVocabTopic = new Topic();
        normalVocabTopic.setName("Bài 1 - Từ vựng thường");
        normalVocabTopic.setSortOrder(2);
        normalVocabTopic.setSourceLine(200);
        normalVocabTopic.setKanjiSection(false);
        normalVocabTopic = em.persistAndFlush(normalVocabTopic);
    }

    @Test
    @DisplayName("1. Tạo Kanji hợp lệ: chuẩn hóa văn bản, tính sourceLine = findMaxSourceLine() + 1, lưu đúng và hiện ở danh sách")
    void createKanji_validData_savesAndShowsInList() {
        KanjiForm form1 = new KanjiForm();
        form1.setTopicId(kanjiTopic.getId());
        form1.setCharacter(" 魚\u200B ");
        form1.setHanViet("  NGƯ  ");
        form1.setMeaningVi("  Cá  ");
        form1.setOnyomi(" ギョ ");
        form1.setKunyomi(" さかな ");

        KanjiEntry saved1 = kanjiService.create(form1);
        assertThat(saved1.getId()).isNotNull();
        assertThat(saved1.getCharacter()).isEqualTo("魚");
        assertThat(saved1.getHanViet()).isEqualTo("NGƯ");
        assertThat(saved1.getMeaningVi()).isEqualTo("Cá");
        assertThat(saved1.getOnyomi()).isEqualTo("ギョ");
        assertThat(saved1.getKunyomi()).isEqualTo("さかな");
        assertThat(saved1.getSourceLine()).isEqualTo(1);

        // Tạo Kanji thứ 2 khi maxSourceLine hiện tại là 500 -> sourceLine mới phải là 501
        saved1.setSourceLine(500);
        em.persistAndFlush(saved1);

        KanjiForm form2 = new KanjiForm();
        form2.setTopicId(kanjiTopic.getId());
        form2.setCharacter("店");
        form2.setHanViet("ĐIẾM");
        form2.setMeaningVi("Cửa hàng");
        form2.setOnyomi("テン");
        form2.setKunyomi("みせ");

        KanjiEntry saved2 = kanjiService.create(form2);
        assertThat(saved2.getSourceLine()).isEqualTo(501);

        Page<KanjiDetailDto> page = kanjiService.search(null, null, PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(KanjiDetailDto::getCharacter)
                .containsExactlyInAnyOrder("魚", "店");
    }

    @Test
    @DisplayName("2. character trùng bị chặn với thông báo 'Kanji này đã tồn tại.'; sửa không đổi gì thì lưu được")
    void createAndUpdateKanji_duplicateCharacterBlocked_unchangedSelfAllowed() {
        KanjiForm form1 = new KanjiForm();
        form1.setTopicId(kanjiTopic.getId());
        form1.setCharacter("魚");
        form1.setHanViet("NGƯ");
        form1.setMeaningVi("Cá");
        KanjiEntry k1 = kanjiService.create(form1);

        // Tạo trùng ký tự "魚" -> lỗi
        KanjiForm dupCreate = new KanjiForm();
        dupCreate.setTopicId(kanjiTopic.getId());
        dupCreate.setCharacter("魚");
        dupCreate.setHanViet("NGƯ");
        assertThatThrownBy(() -> kanjiService.create(dupCreate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Kanji này đã tồn tại.");

        // Sửa k1 mà không đổi gì -> lưu thành công
        KanjiForm unchangedForm = kanjiService.toForm(k1);
        KanjiEntry updatedSelf = kanjiService.update(k1.getId(), unchangedForm);
        assertThat(updatedSelf.getId()).isEqualTo(k1.getId());
        assertThat(updatedSelf.getCharacter()).isEqualTo("魚");

        // Tạo thêm k2 ("店") rồi sửa k2 thành "魚" -> bị chặn vì trùng với k1
        KanjiForm form2 = new KanjiForm();
        form2.setTopicId(kanjiTopic.getId());
        form2.setCharacter("店");
        form2.setHanViet("ĐIẾM");
        KanjiEntry k2 = kanjiService.create(form2);

        KanjiForm dupUpdate = kanjiService.toForm(k2);
        dupUpdate.setCharacter("魚");
        assertThatThrownBy(() -> kanjiService.update(k2.getId(), dupUpdate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Kanji này đã tồn tại.");
    }

    @Test
    @DisplayName("3. topicId trỏ tới chủ đề không phải nhánh Kanji (kanjiSection = false) hoặc không tồn tại bị chặn")
    void createAndUpdateKanji_nonKanjiTopicOrNonExistentTopic_throwsIllegalArgumentException() {
        KanjiForm formNonKanjiTopic = new KanjiForm();
        formNonKanjiTopic.setTopicId(normalVocabTopic.getId());
        formNonKanjiTopic.setCharacter("魚");
        formNonKanjiTopic.setHanViet("NGƯ");

        assertThatThrownBy(() -> kanjiService.create(formNonKanjiTopic))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không thuộc nhánh Kanji");

        KanjiForm formMissingTopic = new KanjiForm();
        formMissingTopic.setTopicId(99999L);
        formMissingTopic.setCharacter("魚");
        assertThatThrownBy(() -> kanjiService.create(formMissingTopic))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Chủ đề không tồn tại");

        // getKanjiTopics chỉ trả về chủ đề có kanjiSection = true
        List<Topic> allowedTopics = kanjiService.getKanjiTopics();
        assertThat(allowedTopics).extracting(Topic::getId).containsExactly(kanjiTopic.getId());
    }

    @Test
    @DisplayName("4. Xóa Kanji: bản ghi Kanji bị xóa nhưng từ vựng liên quan trong bảng vocabulary vẫn còn giữ lại")
    void deleteKanji_retainsRelatedVocabularies() {
        KanjiForm form = new KanjiForm();
        form.setTopicId(kanjiTopic.getId());
        form.setCharacter("魚");
        form.setHanViet("NGƯ");
        form.setMeaningVi("Cá");
        KanjiEntry savedKanji = kanjiService.create(form);

        Vocabulary relatedVocab = new Vocabulary();
        relatedVocab.setTopic(kanjiTopic);
        relatedVocab.setJapaneseText("魚（さかな）");
        relatedVocab.setReading("さかな");
        relatedVocab.setRomaji("sakana");
        relatedVocab.setMeaningVi("Con cá");
        relatedVocab.setHasKanji(true);
        relatedVocab = em.persistAndFlush(relatedVocab);

        // Xóa Kanji
        kanjiService.delete(savedKanji.getId());

        assertThat(kanjiEntryRepository.findById(savedKanji.getId())).isEmpty();
        assertThat(vocabularyRepository.findById(relatedVocab.getId())).isPresent();
        assertThat(vocabularyRepository.findById(relatedVocab.getId()).orElseThrow().getJapaneseText())
                .isEqualTo("魚（さかな）");
    }
}
