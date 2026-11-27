package com.nihon.repository;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm thử tính đúng đắn và khả năng thực thi của file migration schema.sql trên database trống
 * cũng như migration V7 đổi tên bảng kanji_word sang bảng sao lưu kanji_word_backup_20261109.
 */
class MigrationSchemaTest {

    @Test
    @DisplayName("Migration schema.sql chạy thành công trên database trống và hỗ trợ Idempotency khi chạy lại")
    void testMigrationOnBlankDatabase() {
        // Khởi tạo một in-memory database H2 hoàn toàn trống biệt lập
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:blank_test_db;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);

        // 1. Chạy migration schema.sql lần đầu
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
        populator.execute(ds);

        // 2. Xác thực 4 bảng cốt lõi hiện tại đã được tạo thành công
        List<String> tables = jdbcTemplate.query(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'",
                (rs, rowNum) -> rs.getString("TABLE_NAME").toLowerCase()
        );

        assertThat(tables).contains(
                "topic", "vocabulary", "kanji_entry", "import_audit_log",
                "study_session", "study_answer", "verb",
                "exam_paper", "exam_section", "exam_question", "exam_choice", "exam_answer_key",
                "exam_attempt", "exam_attempt_answer",
                "grammar_topic", "grammar_example", "grammar_quiz_question", "grammar_quiz_choice",
                "grammar_progress"
        );
        assertThat(tables).doesNotContain("kanji_word");

        // 3. Thực hiện thêm và đọc dữ liệu thông qua SQL tham số hóa (PreparedStatement)
        jdbcTemplate.update(
                "INSERT INTO topic (name, sort_order, kanji_section, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                "Bài 1", 1, false
        );
        Long topicId = jdbcTemplate.queryForObject("SELECT id FROM topic WHERE name = ?", Long.class, "Bài 1");
        assertThat(topicId).isNotNull();

        jdbcTemplate.update(
                "INSERT INTO vocabulary (topic_id, japanese_text, romaji, meaning_vi, has_kanji, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                topicId, "こんにちは", "konnichiwa", "Xin chào", false
        );
        Integer vocabCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vocabulary WHERE topic_id = ?", Integer.class, topicId);
        assertThat(vocabCount).isEqualTo(1);

        jdbcTemplate.update(
                "INSERT INTO kanji_entry (topic_id, kanji_character, han_viet, meaning_vi, onyomi, kunyomi, source_line, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                topicId, "日", "NHẬT", "Mặt trời, ngày", "にち", "ひ", 10
        );
        Long kanjiId = jdbcTemplate.queryForObject("SELECT id FROM kanji_entry WHERE kanji_character = ?", Long.class, "日");
        assertThat(kanjiId).isNotNull();

        // 3.2. Thêm dữ liệu vào các bảng mới: verb, study_session, exam_paper, grammar_topic
        jdbcTemplate.update(
                "INSERT INTO verb (dictionary_form, masu_form, kanji_text, verb_group, meaning_vi, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                "のむ", "のみます", "飲む", 1, "Uống"
        );
        Integer verbCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM verb WHERE dictionary_form = ?", Integer.class, "のむ");
        assertThat(verbCount).isEqualTo(1);

        jdbcTemplate.update(
                "INSERT INTO study_session (learner_id, mode, topic_id, total_questions, correct_count, duration_seconds, completed_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                "00000000-0000-0000-0000-000000000001", "PRACTICE_JA_VI", topicId, 10, 9, 45
        );
        Integer sessionCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM study_session WHERE learner_id = ?", Integer.class, "00000000-0000-0000-0000-000000000001");
        assertThat(sessionCount).isEqualTo(1);

        jdbcTemplate.update(
                "INSERT INTO exam_paper (code, title, duration_minutes, category, published, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                "EXAM-TEST-01", "Đề thi thử nghiệm", 30, "MOCK", true
        );
        Integer examCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM exam_paper WHERE code = ?", Integer.class, "EXAM-TEST-01");
        assertThat(examCount).isEqualTo(1);

        jdbcTemplate.update(
                "INSERT INTO grammar_topic (code, title, category, formula, explanation, sort_order, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                "GRAM-TEST-01", "Cấu trúc から", "GRAMMAR", "A から B", "Giải thích vì...", 1
        );
        Long grammarId = jdbcTemplate.queryForObject("SELECT id FROM grammar_topic WHERE code = ?", Long.class, "GRAM-TEST-01");
        assertThat(grammarId).isNotNull();

        jdbcTemplate.update(
                "INSERT INTO grammar_progress (learner_id, grammar_id, learned_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
                "00000000-0000-0000-0000-000000000001", grammarId
        );
        Integer progressCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM grammar_progress WHERE learner_id = ?", Integer.class, "00000000-0000-0000-0000-000000000001");
        assertThat(progressCount).isEqualTo(1);

        // 4. Chạy lại migration schema.sql lần 2 trên DB đã có dữ liệu -> Phải thành công không lỗi (Idempotent)
        populator.execute(ds);

        // Dữ liệu cũ vẫn được bảo toàn nguyên vẹn
        Integer vocabCountAfter = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vocabulary", Integer.class);
        assertThat(vocabCountAfter).isEqualTo(1);
        Integer verbCountAfter = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM verb", Integer.class);
        assertThat(verbCountAfter).isEqualTo(1);
        Integer examCountAfter = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM exam_paper", Integer.class);
        assertThat(examCountAfter).isEqualTo(1);
        Integer grammarCountAfter = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM grammar_topic", Integer.class);
        assertThat(grammarCountAfter).isEqualTo(1);
        Integer progressCountAfter = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM grammar_progress", Integer.class);
        assertThat(progressCountAfter).isEqualTo(1);
    }

    @Test
    @DisplayName("Migration V7 đổi tên bảng kanji_word thành kanji_word_backup_20261109 và bảo toàn dữ liệu sao lưu")
    void testMigrationV7RenamesKanjiWordToBackup() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:v7_backup_test_db;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);

        // Giả lập bảng kanji_word lịch sử trước khi chạy V7
        jdbcTemplate.execute(
                "CREATE TABLE kanji_word (" +
                "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                "kanji_id BIGINT NOT NULL, " +
                "japanese VARCHAR(255) NOT NULL, " +
                "romaji VARCHAR(255) NULL, " +
                "meaning_vi VARCHAR(500) NULL, " +
                "sort_order INT NOT NULL DEFAULT 0)"
        );
        jdbcTemplate.update(
                "INSERT INTO kanji_word (kanji_id, japanese, romaji, meaning_vi, sort_order) VALUES (?, ?, ?, ?, ?)",
                1L, "日本（にほん）", "nihon", "Nhật Bản", 1
        );

        // Chạy migration V7
        ResourceDatabasePopulator v7Populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V7__rename_kanji_word_to_backup.sql"));
        v7Populator.execute(ds);

        List<String> tables = jdbcTemplate.query(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'",
                (rs, rowNum) -> rs.getString("TABLE_NAME").toLowerCase()
        );
        assertThat(tables).doesNotContain("kanji_word");
        assertThat(tables).contains("kanji_word_backup_20261109");

        Integer backupCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kanji_word_backup_20261109 WHERE japanese = ?",
                Integer.class, "日本（にほん）"
        );
        assertThat(backupCount).isEqualTo(1);
    }
}
