package com.nihon.repository;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm thử tính đúng đắn và an toàn của migration V2 cho phép NULL ở các cột không bắt buộc:
 * 1. Chạy trên cơ sở dữ liệu đã có bảng và dữ liệu.
 * 2. Cho phép insert các bản ghi có giá trị NULL ở các cột optional (han_viet, meaning_vi, onyomi, kunyomi, romaji).
 * 3. Bảo toàn toàn bộ dữ liệu đã có trước khi chạy migration.
 */
class MigrationV2Test {

    @Test
    @DisplayName("Migration V2 chạy thành công trên database đã có dữ liệu và cho phép giá trị NULL")
    void testMigrationV2OnExistingData() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:v2_test_db;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);

        // 1. Khởi tạo schema ban đầu (V1)
        ResourceDatabasePopulator v1Populator = new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
        v1Populator.execute(ds);

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

        // 3. Chạy migration V2
        ResourceDatabasePopulator v2Populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V2__allow_null_for_optional_import_fields.sql"));
        v2Populator.execute(ds);

        // 4. Kiểm tra dữ liệu cũ vẫn nguyên vẹn
        Integer vocabCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vocabulary", Integer.class);
        assertThat(vocabCount).isEqualTo(1);
        Integer kanjiCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM kanji_entry", Integer.class);
        assertThat(kanjiCount).isEqualTo(1);

        // 5. Kiểm tra insert bản ghi mới với giá trị NULL ở các cột không bắt buộc
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

        // Từ cần nhớ thiếu Romaji và Meaning
        jdbcTemplate.update(
                "INSERT INTO kanji_word (kanji_id, japanese, romaji, meaning_vi, sort_order, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                teaKanjiId, "お茶", null, null, 1
        );

        // Vocabulary thiếu Romaji và Meaning
        jdbcTemplate.update(
                "INSERT INTO vocabulary (topic_id, japanese_text, romaji, meaning_vi, has_kanji, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                topicId, "お茶", null, null, true
        );

        // Xác thực tổng số bản ghi
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM kanji_entry", Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM kanji_word", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vocabulary", Integer.class)).isEqualTo(2);
    }
}
