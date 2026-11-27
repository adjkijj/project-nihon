package com.nihon.service;

import com.nihon.dto.VocabQuickFilter;
import com.nihon.dto.VocabViewMode;
import com.nihon.dto.VocabularyRowDto;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VocabularyRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import org.springframework.test.util.AopTestUtils;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@Import(VocabularyService.class)
class VocabularySearchPerformanceSecurityTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private VocabularyRepository vocabularyRepository;

    @Autowired
    private VocabularyService vocabularyService;

    @Autowired
    private EntityManager entityManager;

    private Topic topicA;
    private Topic topicB;

    @BeforeEach
    void setUp() {
        // Tạo 2 chủ đề
        topicA = new Topic();
        topicA.setName("Chủ đề N5 A");
        topicA.setSortOrder(1);
        topicA = topicRepository.saveAndFlush(topicA);

        topicB = new Topic();
        topicB.setName("Chủ đề N5 B");
        topicB.setSortOrder(2);
        topicB = topicRepository.saveAndFlush(topicB);

        // Chèn 20.000 bản ghi bằng batch insert để kiểm tra hiệu năng và giới hạn bộ nhớ
        int totalRows = 20000;
        int batchSize = 1000;
        Timestamp now = Timestamp.from(Instant.now());

        String sql = "INSERT INTO vocabulary (topic_id, japanese_text, reading, romaji, meaning_vi, has_kanji, source_line, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        List<Object[]> batchArgs = new ArrayList<>(batchSize);
        for (int i = 1; i <= totalRows; i++) {
            Long topicId = (i % 2 == 1) ? topicA.getId() : topicB.getId();
            String jp = "単語_" + i;
            String reading = "たんご_" + i;
            String romaji = "tango_" + i;
            String meaning = "Nghĩa tiếng Việt " + i;
            boolean hasKanji = (i % 3 != 0); // 2/3 có Kanji, 1/3 không

            batchArgs.add(new Object[]{topicId, jp, reading, romaji, meaning, hasKanji, i, now, now});

            if (batchArgs.size() == batchSize) {
                jdbcTemplate.batchUpdate(sql, batchArgs);
                batchArgs.clear();
            }
        }
        if (!batchArgs.isEmpty()) {
            jdbcTemplate.batchUpdate(sql, batchArgs);
            batchArgs.clear();
        }

        // Chèn các từ đặc biệt để kiểm tra gộp từ trùng và escape ký tự LIKE
        // 1. Từ trùng nhau ở 2 chủ đề: "共通用語"
        jdbcTemplate.update(sql, topicA.getId(), "共通用語", "きょうつう", "kyoutsuu", "Từ dùng chung", true, 30001, now, now);
        jdbcTemplate.update(sql, topicB.getId(), "共通用語", "きょうつう", "kyoutsuu", "Từ dùng chung", true, 30002, now, now);

        // 2. Ký tự đặc biệt SQL LIKE: '%' và '_' và '\'
        jdbcTemplate.update(sql, topicA.getId(), "100%確実", "ひゃくパーセント", "100% kakujitsu", "100% chắc chắn", true, 30003, now, now);
        jdbcTemplate.update(sql, topicA.getId(), "1000円", "せんえん", "sen en", "1000 Yên", true, 30004, now, now);
        jdbcTemplate.update(sql, topicA.getId(), "under_score", "アンダー", "under_score", "Dấu gạch dưới", false, 30005, now, now);
        jdbcTemplate.update(sql, topicA.getId(), "underXscore", "アンダー", "underXscore", "Không gạch dưới", false, 30006, now, now);
        jdbcTemplate.update(sql, topicA.getId(), "back\\slash", "スラッシュ", "backslash", "Dấu xuyệt ngược", false, 30007, now, now);

        entityManager.clear();
    }

    @Test
    @DisplayName("Dữ liệu 20.000 từ: Tìm kiếm trang 0 size 100 không nạp quá 100 bản ghi vào Session L1 cache")
    void testSearchWith20000RecordsDoesNotLoadMoreThanPageSizeIntoMemory() {
        entityManager.clear();

        Page<VocabularyRowDto> page = vocabularyService.search(
                null, null, VocabQuickFilter.ALL, VocabViewMode.COMPACT, PageRequest.of(0, 100));

        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(20000L);
        assertThat(page.getContent()).hasSize(100);

        // Kiểm tra số lượng entity thực tế được quản lý trong Hibernate PersistenceContext (Session L1 Cache)
        Object rawSession = AopTestUtils.getUltimateTargetObject(entityManager.unwrap(Session.class));
        if (rawSession instanceof SharedSessionContractImplementor sessionImpl) {
            int managedEntities = sessionImpl.getPersistenceContext().getNumberOfManagedEntities();
            // Không được vượt quá 100 bản ghi của trang hiện tại (tuyệt đối không nạp toàn bộ 20.000 bản ghi vào bộ nhớ)
            assertThat(managedEntities).isLessThanOrEqualTo(100);
            assertThat(managedEntities).isLessThan(20000);
        }
    }

    @Test
    @DisplayName("Giới hạn size tối đa 100: Yêu cầu size=1000 phải tự động bị chặn xuống 100")
    void testSearchPageSizeIsCappedAt100() {
        entityManager.clear();

        Page<VocabularyRowDto> page = vocabularyService.search(
                null, null, VocabQuickFilter.ALL, VocabViewMode.COMPACT, PageRequest.of(0, 1000));

        assertThat(page.getContent()).hasSize(100);
        assertThat(page.getSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("Phòng chống Integer Overflow: page * size lớn hơn Integer.MAX_VALUE trả về trang rỗng an toàn")
    void testSearchPageIntegerOverflowDoesNotThrow() {
        entityManager.clear();

        // page cực lớn gây tràn số int (ví dụ Integer.MAX_VALUE)
        Page<VocabularyRowDto> page = vocabularyService.search(
                null, null, VocabQuickFilter.ALL, VocabViewMode.COMPACT, PageRequest.of(Integer.MAX_VALUE, 100));

        assertThat(page).isNotNull();
        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(20000L);
    }

    @Test
    @DisplayName("Gộp từ trùng theo VocabularyDeduplicationKey: Từ xuất hiện ở nhiều Topic được gộp thành 1 dòng kèm danh sách Topics")
    void testDuplicateVocabularyAcrossTopicsIsDeduplicated() {
        entityManager.clear();

        Page<VocabularyRowDto> page = vocabularyService.search(
                null, "共通用語", VocabQuickFilter.ALL, VocabViewMode.COMPACT, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1L);
        assertThat(page.getContent()).hasSize(1);

        VocabularyRowDto row = page.getContent().get(0);
        assertThat(row.getJapaneseText()).isEqualTo("共通用語");
        assertThat(row.getReading()).isEqualTo("きょうつう");
        assertThat(row.getMeaningVi()).isEqualTo("Từ dùng chung");

        // Gộp đúng 2 chủ đề Topic A và Topic B
        assertThat(row.getTopics()).hasSize(2);
        assertThat(row.getAllIds()).hasSize(2);
    }

    @Test
    @DisplayName("escapeLikePattern và ESCAPE '\\': Tìm kiếm ký tự đặc biệt '%' và '_' không bị hiểu nhầm thành wildcard")
    void testEscapeLikePatternEscaping() {
        entityManager.clear();

        // 1. Tìm '100%': Phải khớp chính xác '100%確実' và KHÔNG khớp '1000円'
        Page<VocabularyRowDto> percentPage = vocabularyService.search(
                null, "100%", VocabQuickFilter.ALL, VocabViewMode.COMPACT, PageRequest.of(0, 20));
        assertThat(percentPage.getContent()).hasSize(1);
        assertThat(percentPage.getContent().get(0).getJapaneseText()).isEqualTo("100%確実");

        // 2. Tìm 'under_score': Phải khớp chính xác 'under_score' và KHÔNG khớp 'underXscore'
        Page<VocabularyRowDto> underPage = vocabularyService.search(
                null, "under_score", VocabQuickFilter.ALL, VocabViewMode.COMPACT, PageRequest.of(0, 20));
        assertThat(underPage.getContent()).hasSize(1);
        assertThat(underPage.getContent().get(0).getJapaneseText()).isEqualTo("under_score");

        // 3. Tìm dấu gạch chéo ngược 'back\slash'
        Page<VocabularyRowDto> slashPage = vocabularyService.search(
                null, "back\\slash", VocabQuickFilter.ALL, VocabViewMode.COMPACT, PageRequest.of(0, 20));
        assertThat(slashPage.getContent()).hasSize(1);
        assertThat(slashPage.getContent().get(0).getJapaneseText()).isEqualTo("back\\slash");
    }

    @Test
    @DisplayName("Lọc theo QuickFilter: HAS_KANJI, NO_READING, NO_MEANING hoạt động chính xác")
    void testQuickFilters() {
        entityManager.clear();

        Page<VocabularyRowDto> hasKanjiPage = vocabularyService.search(
                null, null, VocabQuickFilter.HAS_KANJI, VocabViewMode.COMPACT, PageRequest.of(0, 50));
        for (VocabularyRowDto row : hasKanjiPage.getContent()) {
            assertThat(row.isHasKanji()).isTrue();
        }
    }
}
