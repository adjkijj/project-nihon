package com.nihon.repository;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm thử tính đúng đắn và an toàn của migration V4 mở rộng cột han_viet, onyomi, kunyomi lên 255 ký tự:
 * 1. Chạy trên database đã có dữ liệu cũ (tạo từ schema ban đầu VARCHAR(100) + V2).
 * 2. Bảo toàn nguyên vẹn toàn bộ dữ liệu cũ.
 * 3. Cho phép lưu Hán Việt, Onyomi, Kunyomi dài 200..255 ký tự.
 * 4. Chạy lại V4 nhiều lần không gây lỗi (idempotent).
 */
class MigrationV4Test {

    @Test
    @DisplayName("Migration V4 chạy thành công trên database có dữ liệu cũ, cho phép Hán Việt 200-255 ký tự và chạy lại an toàn")
    void testMigrationV4OnExistingDatabaseWithData() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:v4_test_db;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);

        // 1. Giả lập database cũ trước V4: khởi tạo schema từ V1 và V2 (khi han_viet, onyomi, kunyomi còn là VARCHAR(100))
        ResourceDatabasePopulator v1Populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V1__create_initial_schema.sql"),
                new ClassPathResource("db/migration/V2__allow_null_for_optional_import_fields.sql")
        );
        v1Populator.execute(ds);

        // 2. Chèn dữ liệu sẵn có trước khi chạy V4
        jdbcTemplate.update(
                "INSERT INTO topic (name, sort_order, kanji_section, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                "Kanji Bài 1", 1, true
        );
        Long topicId = jdbcTemplate.queryForObject("SELECT id FROM topic WHERE name = ?", Long.class, "Kanji Bài 1");

        jdbcTemplate.update(
                "INSERT INTO kanji_entry (topic_id, kanji_character, han_viet, meaning_vi, onyomi, kunyomi, source_line, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                topicId, "日", "NHẬT", "Mặt trời, ngày", "にち", "ひ", 1
        );

        // 3. Chạy migration V4 nâng cột han_viet, onyomi, kunyomi lên VARCHAR(255)
        ResourceDatabasePopulator v4Populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V4__expand_kanji_entry_column_lengths.sql"));
        v4Populator.execute(ds);

        // 4. Kiểm tra dữ liệu cũ được bảo toàn nguyên vẹn
        String oldHanViet = jdbcTemplate.queryForObject(
                "SELECT han_viet FROM kanji_entry WHERE kanji_character = ?", String.class, "日");
        assertThat(oldHanViet).isEqualTo("NHẬT");

        // 5. Chèn bản ghi có Hán Việt 200 ký tự, Onyomi 255 ký tự, Kunyomi 255 ký tự -> Thành công
        String hanViet200 = "H".repeat(200);
        String onyomi255 = "お".repeat(255);
        String kunyomi255 = "く".repeat(255);
        String meaning255 = "M".repeat(255);

        jdbcTemplate.update(
                "INSERT INTO kanji_entry (topic_id, kanji_character, han_viet, meaning_vi, onyomi, kunyomi, source_line, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                topicId, "道", hanViet200, meaning255, onyomi255, kunyomi255, 2
        );

        String savedHanViet = jdbcTemplate.queryForObject(
                "SELECT han_viet FROM kanji_entry WHERE kanji_character = ?", String.class, "道");
        assertThat(savedHanViet).hasSize(200).isEqualTo(hanViet200);

        // 6. Chạy lại V4 lần 2 (Idempotent) -> Thành công và không mất dữ liệu
        v4Populator.execute(ds);
        Integer countAfterReRun = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM kanji_entry", Integer.class);
        assertThat(countAfterReRun).isEqualTo(2);
    }
}

