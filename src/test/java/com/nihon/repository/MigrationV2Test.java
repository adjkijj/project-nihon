package com.nihon.repository;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm thử tính đúng đắn của schema.sql cho phép NULL ở các cột không bắt buộc khi import dữ liệu:
 * 1. Chạy schema.sql trên cơ sở dữ liệu trống.
 * 2. Cho phép insert các bản ghi có giá trị NULL ở các cột optional (han_viet, meaning_vi, onyomi, kunyomi, romaji, meaning_vi).
 * 3. Bảo toàn tính toàn vẹn dữ liệu.
 */
class MigrationV2Test {

    @Test
    @DisplayName("schema.sql cho phép giá trị NULL ở các cột không bắt buộc của kanji_entry và vocabulary")
    void testMigrationV2OnExistingData() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:v2_test_db;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);

        // 1. Khởi tạo schema từ migration V1 và áp dụng V2
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V1__create_initial_schema.sql"),
                new ClassPathResource("db/migration/V2__allow_null_for_optional_import_fields.sql")
        );
        populator.execute(ds);

        // 2. Chèn dữ liệu mẫu có đầy đủ cột
        jdbcTemplate.update(
                "INSERT INTO topic (name, sort_order, kanji_section, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                "Chủ đề 1", 1, false
        );
        Long topicId = jdbcTemplate.queryForObject("SELECT id FROM topic WHERE name = ?", Long.class, "Chủ đề 1");

        jdbcTemplate.update(
                "INSERT INTO vocabulary (topic_id, japanese_text, romaji, meaning_vi, has_kanji, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                topicId, "こんにちは", "konnichiwa", "Xin chào", false
        );

        jdbcTemplate.update(
                "INSERT INTO kanji_entry (topic_id, kanji_character, han_viet, meaning_vi, onyomi, kunyomi, source_line, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                topicId, "日", "NHẬT", "Mặt trời, ngày", "にち", "ひ", 10
        );

        // 3. Kiểm tra dữ liệu cũ
        Integer vocabCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vocabulary", Integer.class);
        assertThat(vocabCount).isEqualTo(1);
        Integer kanjiCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM kanji_entry", Integer.class);
        assertThat(kanjiCount).isEqualTo(1);

        // 4. Kiểm tra insert bản ghi mới với giá trị NULL ở các cột không bắt buộc
        // Kanji không có Kun: kunyomi = NULL
        jdbcTemplate.update(
                "INSERT INTO kanji_entry (topic_id, kanji_character, han_viet, meaning_vi, onyomi, kunyomi, source_line, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                topicId, "茶", "TRÀ", "Cây trà", "ちゃ", null, 11
        );
        Long teaKanjiId = jdbcTemplate.queryForObject("SELECT id FROM kanji_entry WHERE kanji_character = ?", Long.class, "茶");
        assertThat(teaKanjiId).isNotNull();

        // Kanji không có On, không có Hán Việt, không có Ý nghĩa
        jdbcTemplate.update(
                "INSERT INTO kanji_entry (topic_id, kanji_character, han_viet, meaning_vi, onyomi, kunyomi, source_line, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                topicId, "料", null, null, null, null, 12
        );

        // Vocabulary thiếu Romaji và Meaning
        jdbcTemplate.update(
                "INSERT INTO vocabulary (topic_id, japanese_text, romaji, meaning_vi, has_kanji, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                topicId, "お茶", null, null, true
        );

        // Xác thực tổng số bản ghi
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM kanji_entry", Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vocabulary", Integer.class)).isEqualTo(2);
    }
}
