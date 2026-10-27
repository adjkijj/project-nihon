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
 * Kiểm thử tính đúng đắn và khả năng thực thi của file migration schema.sql trên database trống.
 * Đảm bảo:
 * 1. Chạy thành công trên database chưa có bảng nào.
 * 2. Tạo đủ 4 bảng: topic, vocabulary, kanji_entry, kanji_word.
 * 3. Chạy lại lần 2 không gây lỗi (tính Idempotency).
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

        // 2. Xác thực 4 bảng đã được tạo thành công
        List<String> tables = jdbcTemplate.query(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'",
                (rs, rowNum) -> rs.getString("TABLE_NAME").toLowerCase()
        );

        assertThat(tables).contains("topic", "vocabulary", "kanji_entry", "kanji_word");

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

        jdbcTemplate.update(
                "INSERT INTO kanji_word (kanji_id, japanese, romaji, meaning_vi, sort_order, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                kanjiId, "日本", "nihon", "Nhật Bản", 1
        );
        Integer wordCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM kanji_word WHERE kanji_id = ?", Integer.class, kanjiId);
        assertThat(wordCount).isEqualTo(1);

        // 4. Chạy lại migration schema.sql lần 2 trên DB đã có dữ liệu -> Phải thành công không lỗi (Idempotent)
        populator.execute(ds);

        // Dữ liệu cũ vẫn được bảo toàn nguyên vẹn
        Integer vocabCountAfter = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vocabulary", Integer.class);
        assertThat(vocabCountAfter).isEqualTo(1);
    }
}
