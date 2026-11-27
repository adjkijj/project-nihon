package com.nihon.service;

import com.nihon.dto.verb.VerbBulkResultItem;
import com.nihon.dto.verb.VerbDetailDto;
import com.nihon.dto.verb.VerbForm;
import com.nihon.entity.Topic;
import com.nihon.entity.Verb;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VerbRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(VerbService.class)
class VerbServiceTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private VerbRepository verbRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private VerbService verbService;

    private Topic testTopic1;
    private Topic testTopic2;

    @BeforeEach
    void setUp() {
        verbRepository.deleteAll();

        testTopic1 = new Topic();
        testTopic1.setName("Chủ đề 1 - Ăn uống " + System.nanoTime());
        testTopic1.setSortOrder(1);
        testTopic1 = topicRepository.save(testTopic1);

        testTopic2 = new Topic();
        testTopic2.setName("Chủ đề 2 - Di chuyển " + System.nanoTime());
        testTopic2.setSortOrder(2);
        testTopic2 = topicRepository.save(testTopic2);
    }

    @Nested
    @DisplayName("1. CRUD Methods")
    class CrudTests {

        @Test
        @DisplayName("create - thành công với đầy đủ kanji và topic")
        void testCreateWithKanjiAndTopic() {
            VerbForm form = VerbForm.builder()
                    .dictionaryForm("たべる")
                    .masuForm("たべます")
                    .kanjiText("食べる")
                    .verbGroup(2)
                    .meaningVi("Ăn cơm")
                    .topicId(testTopic1.getId())
                    .build();

            Verb created = verbService.create(form);
            assertThat(created.getId()).isNotNull();
            assertThat(created.getDictionaryForm()).isEqualTo("たべる");
            assertThat(created.getMasuForm()).isEqualTo("たべます");
            assertThat(created.getKanjiText()).isEqualTo("食べる");
            assertThat(created.getVerbGroup()).isEqualTo(2);
            assertThat(created.getMeaningVi()).isEqualTo("Ăn cơm");
            assertThat(created.getTopic()).isNotNull();
            assertThat(created.getTopic().getId()).isEqualTo(testTopic1.getId());
        }

        @Test
        @DisplayName("create - thành công không kanji và không topic (thuần kana)")
        void testCreateWithoutKanjiAndTopic() {
            VerbForm form = VerbForm.builder()
                    .dictionaryForm("する")
                    .masuForm("します")
                    .kanjiText("   ")
                    .verbGroup(3)
                    .meaningVi("Làm việc")
                    .topicId(null)
                    .build();

            Verb created = verbService.create(form);
            assertThat(created.getId()).isNotNull();
            assertThat(created.getKanjiText()).isNull();
            assertThat(created.getTopic()).isNull();
            assertThat(created.getVerbGroup()).isEqualTo(3);
        }

        @Test
        @DisplayName("findById & getById - tìm thấy verb đã lưu")
        void testFindByIdAndGetById() {
            Verb verb = verbRepository.save(Verb.builder()
                    .dictionaryForm("のむ")
                    .masuForm("のみます")
                    .kanjiText("飲む")
                    .verbGroup(1)
                    .meaningVi("Uống nước")
                    .topic(testTopic1)
                    .build());

            Optional<Verb> opt = verbService.findById(verb.getId());
            assertThat(opt).isPresent();
            assertThat(opt.get().getDictionaryForm()).isEqualTo("のむ");

            Verb found = verbService.getById(verb.getId());
            assertThat(found).isNotNull();
            assertThat(found.getKanjiText()).isEqualTo("飲む");
        }

        @Test
        @DisplayName("findById với null trả về Optional.empty")
        void testFindByIdNull() {
            assertThat(verbService.findById(null)).isEmpty();
        }

        @Test
        @DisplayName("getById với ID không tồn tại ném NoSuchElementException")
        void testGetByIdNotFound() {
            assertThatThrownBy(() -> verbService.getById(999999L))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("999999");
        }

        @Test
        @DisplayName("getDetailById - tính toán chính xác 5 thể chia")
        void testGetDetailById() {
            Verb verb = verbRepository.save(Verb.builder()
                    .dictionaryForm("かく")
                    .masuForm("かきます")
                    .kanjiText("書く")
                    .verbGroup(1)
                    .meaningVi("Viết thư")
                    .build());

            VerbDetailDto detail = verbService.getDetailById(verb.getId());
            assertThat(detail.getId()).isEqualTo(verb.getId());
            assertThat(detail.getDictionaryForm()).isEqualTo("かく");
            assertThat(detail.getKanjiText()).isEqualTo("書く");
            assertThat(detail.getTeForm()).isEqualTo("かいて");
            assertThat(detail.getTaForm()).isEqualTo("かいた");
            assertThat(detail.getNaiForm()).isEqualTo("かかない");
            assertThat(detail.getKanjiMasuForm()).isEqualTo("書きます");
            assertThat(detail.getKanjiTeForm()).isEqualTo("書いて");
            assertThat(detail.getKanjiTaForm()).isEqualTo("書いた");
            assertThat(detail.getKanjiNaiForm()).isEqualTo("書かない");
        }

        @Test
        @DisplayName("update - cập nhật thành công các trường thông tin")
        void testUpdateSuccess() {
            Verb verb = verbRepository.save(Verb.builder()
                    .dictionaryForm("いく")
                    .masuForm("いきます")
                    .kanjiText("行く")
                    .verbGroup(1)
                    .meaningVi("Đi bộ")
                    .topic(testTopic1)
                    .build());

            VerbForm updateForm = VerbForm.builder()
                    .dictionaryForm("いく")
                    .masuForm("いきます")
                    .kanjiText("行く")
                    .verbGroup(1)
                    .meaningVi("Đi đến trường")
                    .topicId(testTopic2.getId())
                    .build();

            Verb updated = verbService.update(verb.getId(), updateForm);
            assertThat(updated.getMeaningVi()).isEqualTo("Đi đến trường");
            assertThat(updated.getTopic().getId()).isEqualTo(testTopic2.getId());
        }

        @Test
        @DisplayName("update - ném NoSuchElementException khi ID không tồn tại")
        void testUpdateNotFound() {
            VerbForm updateForm = VerbForm.builder()
                    .dictionaryForm("いく")
                    .masuForm("いきます")
                    .verbGroup(1)
                    .meaningVi("Đi")
                    .build();

            assertThatThrownBy(() -> verbService.update(999999L, updateForm))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("delete - xóa thành công")
        void testDeleteSuccess() {
            Verb verb = verbRepository.save(Verb.builder()
                    .dictionaryForm("まつ")
                    .masuForm("まちます")
                    .verbGroup(1)
                    .meaningVi("Chờ đợi")
                    .build());

            verbService.delete(verb.getId());
            assertThat(verbRepository.findById(verb.getId())).isEmpty();
        }

        @Test
        @DisplayName("delete - ném NoSuchElementException khi ID không tồn tại")
        void testDeleteNotFound() {
            assertThatThrownBy(() -> verbService.delete(999999L))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("search - phân trang và lọc theo nhóm, chủ đề, từ khóa")
        void testSearchAndFilters() {
            Verb v1 = verbRepository.save(Verb.builder()
                    .dictionaryForm("たべる")
                    .masuForm("たべます")
                    .kanjiText("食べる")
                    .verbGroup(2)
                    .meaningVi("Ăn hoa quả")
                    .topic(testTopic1)
                    .build());

            Verb v2 = verbRepository.save(Verb.builder()
                    .dictionaryForm("のむ")
                    .masuForm("のみます")
                    .kanjiText("飲む")
                    .verbGroup(1)
                    .meaningVi("Uống trà")
                    .topic(testTopic1)
                    .build());

            Verb v3 = verbRepository.save(Verb.builder()
                    .dictionaryForm("いく")
                    .masuForm("いきます")
                    .kanjiText("行く")
                    .verbGroup(1)
                    .meaningVi("Đi du lịch")
                    .topic(testTopic2)
                    .build());

            // Tìm kiếm tất cả
            Page<Verb> all = verbService.search(null, null, null, PageRequest.of(0, 10));
            assertThat(all.getTotalElements()).isEqualTo(3);

            // Lọc theo nhóm 1
            Page<Verb> group1 = verbService.search(1, null, null, PageRequest.of(0, 10));
            assertThat(group1.getTotalElements()).isEqualTo(2);

            // Lọc theo nhóm 2
            Page<Verb> group2 = verbService.search(2, null, null, PageRequest.of(0, 10));
            assertThat(group2.getTotalElements()).isEqualTo(1);
            assertThat(group2.getContent().get(0).getDictionaryForm()).isEqualTo("たべる");

            // Lọc theo topic
            Page<Verb> topic1Page = verbService.search(null, testTopic1.getId(), null, PageRequest.of(0, 10));
            assertThat(topic1Page.getTotalElements()).isEqualTo(2);

            // Lọc theo từ khóa kanji
            Page<Verb> kwKanji = verbService.search(null, null, "飲", PageRequest.of(0, 10));
            assertThat(kwKanji.getTotalElements()).isEqualTo(1);
            assertThat(kwKanji.getContent().get(0).getDictionaryForm()).isEqualTo("のむ");

            // Lọc theo từ khóa nghĩa tiếng Việt
            Page<Verb> kwMeaning = verbService.search(null, null, "du lịch", PageRequest.of(0, 10));
            assertThat(kwMeaning.getTotalElements()).isEqualTo(1);
            assertThat(kwMeaning.getContent().get(0).getDictionaryForm()).isEqualTo("いく");
        }
    }

    @Nested
    @DisplayName("2. Consistency Check Validation")
    class ConsistencyCheckTests {

        @Test
        @DisplayName("Nhóm 1 nhưng thể ます của Nhóm 2 (e.g. のむ + のめます) bị từ chối")
        void testMismatchedGroup1Masu() {
            VerbForm form = VerbForm.builder()
                    .dictionaryForm("のむ")
                    .masuForm("のめます")
                    .verbGroup(1)
                    .meaningVi("Uống")
                    .build();

            assertThatThrownBy(() -> verbService.create(form))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(VerbService.CONSISTENCY_ERROR_MESSAGE);
        }

        @Test
        @DisplayName("Nhóm 1 nhưng chọn nhóm 2 bị từ chối")
        void testMismatchedVerbGroupSelection() {
            VerbForm form = VerbForm.builder()
                    .dictionaryForm("のむ")
                    .masuForm("のみます")
                    .verbGroup(2) // sai nhóm
                    .meaningVi("Uống")
                    .build();

            assertThatThrownBy(() -> verbService.create(form))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(VerbService.CONSISTENCY_ERROR_MESSAGE);
        }

        @Test
        @DisplayName("Nhóm 2 nhưng đuôi không phải る (e.g. かく) bị từ chối")
        void testGroup2MustEndWithRu() {
            VerbForm form = VerbForm.builder()
                    .dictionaryForm("かく")
                    .masuForm("かきます")
                    .verbGroup(2)
                    .meaningVi("Viết")
                    .build();

            assertThatThrownBy(() -> verbService.create(form))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(VerbService.CONSISTENCY_ERROR_MESSAGE);
        }

        @Test
        @DisplayName("Nhóm 3 nhưng không phải する hoặc くる bị từ chối")
        void testGroup3MustBeSuruOrKuru() {
            VerbForm form = VerbForm.builder()
                    .dictionaryForm("たべる")
                    .masuForm("たべます")
                    .verbGroup(3) // sai nhóm
                    .meaningVi("Ăn")
                    .build();

            assertThatThrownBy(() -> verbService.create(form))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(VerbService.CONSISTENCY_ERROR_MESSAGE);
        }

        @Test
        @DisplayName("Thể ます không kết thúc bằng ます bị từ chối")
        void testMasuMustEndWithMasu() {
            VerbForm form = VerbForm.builder()
                    .dictionaryForm("たべる")
                    .masuForm("たべ")
                    .verbGroup(2)
                    .meaningVi("Ăn")
                    .build();

            assertThatThrownBy(() -> verbService.create(form))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(VerbService.CONSISTENCY_ERROR_MESSAGE);
        }

        @Test
        @DisplayName("Khi cập nhật, không khớp tính nhất quán cũng bị từ chối")
        void testUpdateConsistencyCheck() {
            Verb verb = verbRepository.save(Verb.builder()
                    .dictionaryForm("まつ")
                    .masuForm("まちます")
                    .verbGroup(1)
                    .meaningVi("Đợi")
                    .build());

            VerbForm invalidUpdate = VerbForm.builder()
                    .dictionaryForm("まつ")
                    .masuForm("またます") // sai thể masu
                    .verbGroup(1)
                    .meaningVi("Đợi")
                    .build();

            assertThatThrownBy(() -> verbService.update(verb.getId(), invalidUpdate))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(VerbService.CONSISTENCY_ERROR_MESSAGE);
        }
    }

    @Nested
    @DisplayName("3. Unique Constraint Validation")
    class UniqueConstraintTests {

        @Test
        @DisplayName("Tạo động từ trùng cặp (dictionaryForm, kanjiText) bị từ chối")
        void testDuplicateDictAndKanjiRejected() {
            VerbForm form1 = VerbForm.builder()
                    .dictionaryForm("かう")
                    .masuForm("かいます")
                    .kanjiText("買う")
                    .verbGroup(1)
                    .meaningVi("Mua sắm")
                    .build();
            verbService.create(form1);

            VerbForm duplicate = VerbForm.builder()
                    .dictionaryForm("かう")
                    .masuForm("かいます")
                    .kanjiText("買う")
                    .verbGroup(1)
                    .meaningVi("Mua đồ mới")
                    .build();

            assertThatThrownBy(() -> verbService.create(duplicate))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("đã tồn tại trong hệ thống");
        }

        @Test
        @DisplayName("Tạo động từ trùng dictionaryForm thuần kana (kanjiText = null) bị từ chối")
        void testDuplicatePureKanaRejected() {
            VerbForm form1 = VerbForm.builder()
                    .dictionaryForm("する")
                    .masuForm("します")
                    .verbGroup(3)
                    .meaningVi("Làm")
                    .build();
            verbService.create(form1);

            VerbForm duplicate = VerbForm.builder()
                    .dictionaryForm("する")
                    .masuForm("します")
                    .verbGroup(3)
                    .meaningVi("Thực hiện")
                    .build();

            assertThatThrownBy(() -> verbService.create(duplicate))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("đã tồn tại trong hệ thống");
        }

        @Test
        @DisplayName("Cùng dictionaryForm nhưng khác kanjiText được chấp nhận (e.g. 観る vs 見る)")
        void testSameDictionaryFormDifferentKanjiAccepted() {
            VerbForm form1 = VerbForm.builder()
                    .dictionaryForm("みる")
                    .masuForm("みます")
                    .kanjiText("見る")
                    .verbGroup(2)
                    .meaningVi("Nhìn, xem")
                    .build();
            Verb v1 = verbService.create(form1);
            assertThat(v1.getId()).isNotNull();

            VerbForm form2 = VerbForm.builder()
                    .dictionaryForm("みる")
                    .masuForm("みます")
                    .kanjiText("観る")
                    .verbGroup(2)
                    .meaningVi("Thưởng thức phim ảnh")
                    .build();
            Verb v2 = verbService.create(form2);
            assertThat(v2.getId()).isNotNull();
        }

        @Test
        @DisplayName("Cập nhật động từ A thành trùng (dict, kanji) với động từ B bị từ chối")
        void testUpdateCollidingWithAnotherVerbRejected() {
            Verb v1 = verbService.create(VerbForm.builder()
                    .dictionaryForm("いく")
                    .masuForm("いきます")
                    .kanjiText("行く")
                    .verbGroup(1)
                    .meaningVi("Đi")
                    .build());

            Verb v2 = verbService.create(VerbForm.builder()
                    .dictionaryForm("くる")
                    .masuForm("きます")
                    .kanjiText("来る")
                    .verbGroup(3)
                    .meaningVi("Đến")
                    .build());

            // Thử sửa v2 thành trùng với v1
            VerbForm collidingUpdate = VerbForm.builder()
                    .dictionaryForm("いく")
                    .masuForm("いきます")
                    .kanjiText("行く")
                    .verbGroup(1)
                    .meaningVi("Đi cập nhật")
                    .build();

            assertThatThrownBy(() -> verbService.update(v2.getId(), collidingUpdate))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("đã tồn tại trong hệ thống");
        }
    }

    @Nested
    @DisplayName("4. Bulk Add Validation & Processing")
    class BulkAddTests {

        @Test
        @DisplayName("Dữ liệu rỗng ném IllegalArgumentException")
        void testEmptyTextThrows() {
            assertThatThrownBy(() -> verbService.bulkAdd("", null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> verbService.bulkAdd("   \n  \n", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Dữ liệu vượt quá 100 dòng bị từ chối")
        void testOver100LinesThrows() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 101; i++) {
                sb.append("たべます,Ăn ").append(i).append("\n");
            }

            assertThatThrownBy(() -> verbService.bulkAdd(sb.toString(), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("100");
        }

        @Test
        @DisplayName("Nhập hàng loạt thành công: tự suy luận nhóm và thể từ điển")
        void testBulkAddSuccess() {
            String text = "たべます,Ăn\n" +
                    "べんきょうします,Học bài\n" +
                    "おしえます,Dạy học";

            List<VerbBulkResultItem> results = verbService.bulkAdd(text, testTopic1.getId());
            assertThat(results).hasSize(3);

            assertThat(results.get(0).isSuccess()).isTrue();
            assertThat(results.get(0).getVerb().getDictionaryForm()).isEqualTo("たべる");
            assertThat(results.get(0).getVerb().getVerbGroup()).isEqualTo(2);

            assertThat(results.get(1).isSuccess()).isTrue();
            assertThat(results.get(1).getVerb().getDictionaryForm()).isEqualTo("べんきょうする");
            assertThat(results.get(1).getVerb().getVerbGroup()).isEqualTo(3);

            assertThat(results.get(2).isSuccess()).isTrue();
            assertThat(results.get(2).getVerb().getDictionaryForm()).isEqualTo("おしえる");
            assertThat(results.get(2).getVerb().getVerbGroup()).isEqualTo(2);

            assertThat(verbRepository.count()).isEqualTo(3);
        }

        @Test
        @DisplayName("Xử lý lỗi từng dòng: sai định dạng, âm i mơ hồ, từ trùng")
        void testBulkAddLineErrorHandling() {
            // Thêm trước động từ để kiểm tra trùng
            verbService.create(VerbForm.builder()
                    .dictionaryForm("たべる")
                    .masuForm("たべます")
                    .verbGroup(2)
                    .meaningVi("Ăn")
                    .build());

            String text = "dòng_sai_định_dạng\n" +
                    "たべます,Ăn lại\n" +                 // trùng đã có
                    "のみます,Uống nước\n" +             // âm i -> mơ hồ giữa nhóm 1 và nhóm 2
                    "あけます,Mở cửa";                   // hợp lệ nhóm 2

            List<VerbBulkResultItem> results = verbService.bulkAdd(text, null);
            assertThat(results).hasSize(4);

            // Dòng 1: sai định dạng
            assertThat(results.get(0).isSuccess()).isFalse();
            assertThat(results.get(0).getMessage()).contains("Định dạng không hợp lệ");

            // Dòng 2: đã tồn tại
            assertThat(results.get(1).isSuccess()).isFalse();
            assertThat(results.get(1).getMessage()).contains("đã tồn tại trong hệ thống");

            // Dòng 3: mơ hồ hàng i
            assertThat(results.get(2).isSuccess()).isFalse();
            assertThat(results.get(2).getMessage()).contains("Không thể tự động xác định nhóm động từ");

            // Dòng 4: thành công
            assertThat(results.get(3).isSuccess()).isTrue();
            assertThat(results.get(3).getVerb().getDictionaryForm()).isEqualTo("あける");
            assertThat(results.get(3).getVerb().getVerbGroup()).isEqualTo(2);

            // Tổng số trong DB là 2 (1 từ trước + 1 từ dòng 4)
            assertThat(verbRepository.count()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("5. Sample Data Loading")
    class SampleDataTests {

        @Test
        @DisplayName("Nạp dữ liệu mẫu khi DB trống: đúng 5 động từ chuẩn")
        void testLoadSampleDataWhenEmpty() {
            assertThat(verbRepository.count()).isEqualTo(0);

            List<Verb> samples = verbService.loadSampleData();
            assertThat(samples).hasSize(5);
            assertThat(verbRepository.count()).isEqualTo(5);

            List<String> dictForms = samples.stream().map(Verb::getDictionaryForm).toList();
            assertThat(dictForms).containsExactlyInAnyOrder(
                    "のむ", "たべる", "いく", "くる", "べんきょうする"
            );

            List<String> kanjiTexts = samples.stream().map(Verb::getKanjiText).toList();
            assertThat(kanjiTexts).containsExactlyInAnyOrder(
                    "飲む", "食べる", "行く", "来る", "勉強する"
            );
        }

        @Test
        @DisplayName("Nạp dữ liệu mẫu khi DB đã có dữ liệu: bị từ chối bằng IllegalStateException")
        void testLoadSampleDataWhenNotEmptyRejected() {
            verbService.create(VerbForm.builder()
                    .dictionaryForm("ねる")
                    .masuForm("ねます")
                    .verbGroup(2)
                    .meaningVi("Ngủ")
                    .build());

            assertThatThrownBy(() -> verbService.loadSampleData())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Chỉ có thể nạp dữ liệu mẫu khi danh sách động từ đang trống");
        }
    }
}
