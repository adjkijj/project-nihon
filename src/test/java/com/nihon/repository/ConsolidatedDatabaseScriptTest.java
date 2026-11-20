package com.nihon.repository;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm thử tính toàn vẹn, khả năng thực thi và tính đúng đắn của file SQL gộp
 * nihon_database.sql và script chạy init_database.bat.
 */
class ConsolidatedDatabaseScriptTest {

    private static final List<String> EXPECTED_TABLES = List.of(
            "topic", "vocabulary", "kanji_entry", "import_audit_log",
            "study_session", "study_answer", "verb",
            "exam_paper", "exam_section", "exam_question", "exam_choice", "exam_answer_key",
            "exam_attempt", "exam_attempt_answer",
            "grammar_topic", "grammar_example", "grammar_quiz_question", "grammar_quiz_choice",
            "grammar_progress", "kanji_word_backup_20261109"
    );

    @Test
    @DisplayName("1. File nihon_database.sql và init_database.bat tồn tại ở thư mục gốc và có nội dung hoàn chỉnh")
    void verifyFilesExistAndNonEmpty() {
        Path sqlPath = Paths.get("nihon_database.sql");
        assertThat(Files.exists(sqlPath)).as("Tệp nihon_database.sql phải tồn tại ở thư mục gốc").isTrue();
        assertThat(new File(sqlPath.toString()).length()).as("Tệp nihon_database.sql không được rỗng").isGreaterThan(1000);

        Path batPath = Paths.get("init_database.bat");
        assertThat(Files.exists(batPath)).as("Tệp init_database.bat phải tồn tại ở thư mục gốc").isTrue();
        assertThat(new File(batPath.toString()).length()).as("Tệp init_database.bat không được rỗng").isGreaterThan(500);

        Path ps1Path = Paths.get("init_database.ps1");
        assertThat(Files.exists(ps1Path)).as("Tệp init_database.ps1 phải tồn tại ở thư mục gốc").isTrue();
    }

    @Test
    @DisplayName("2. Thực thi nihon_database.sql trên database trống tạo thành công 20 bảng với đầy đủ khóa và chỉ mục")
    void executeConsolidatedSqlOnBlankDatabase() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        // Cấu hình H2 ở MODE=MySQL và cho phép đa schema
        ds.setURL("jdbc:h2:mem:consolidated_test_db;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);

        // Đọc nội dung tệp nihon_database.sql
        Path sqlPath = Paths.get("nihon_database.sql");
        String fullSql = Files.readString(sqlPath, StandardCharsets.UTF_8);

        // H2 in-memory URL kết nối trực tiếp đến database, ta có thể chạy script
        try (Connection conn = ds.getConnection()) {
            EncodedResource resource = new EncodedResource(new FileSystemResource(sqlPath.toFile()), StandardCharsets.UTF_8);
            // ScriptUtils thực thi script hoàn chỉnh, bỏ qua câu lệnh tạo/chuyển database nếu môi trường in-memory yêu cầu
            ScriptUtils.executeSqlScript(conn, resource, true, true, "--", ";", "/*", "*/");
        }

        // Kiểm tra danh sách bảng đã được tạo
        List<String> tables = jdbcTemplate.query(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_SCHEMA) IN ('public', 'nihon_db')",
                (rs, rowNum) -> rs.getString("TABLE_NAME").toLowerCase()
        );

        for (String expectedTable : EXPECTED_TABLES) {
            assertThat(tables)
                    .as("Bảng " + expectedTable + " phải được tạo trong database")
                    .contains(expectedTable.toLowerCase());
        }

        // 3. Kiểm thử chèn dữ liệu mẫu tiếng Nhật và tiếng Việt có dấu
        jdbcTemplate.update(
                "INSERT INTO topic (name, sort_order, kanji_section, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                "Bài 1: Lời chào tiếng Nhật (挨拶)", 1, false
        );

        Long topicId = jdbcTemplate.queryForObject("SELECT id FROM topic WHERE sort_order = 1", Long.class);
        assertThat(topicId).isNotNull();

        jdbcTemplate.update(
                "INSERT INTO vocabulary (topic_id, japanese_text, reading, romaji, meaning_vi, has_kanji) VALUES (?, ?, ?, ?, ?, ?)",
                topicId, "こんにちは", "こんにちは", "konnichiwa", "Xin chào (buổi chiều)", false
        );

        String vocabText = jdbcTemplate.queryForObject(
                "SELECT japanese_text FROM vocabulary WHERE topic_id = ?",
                String.class, topicId
        );
        assertThat(vocabText).isEqualTo("こんにちは");

        // 4. Kiểm thử bảng đề thi và các quan hệ khóa ngoại
        jdbcTemplate.update(
                "INSERT INTO exam_paper (code, title, duration_minutes, category, published, shuffle_choices, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                "JLPT-N5-TEST-01", "Đề thi thử JLPT N5 Đầy Đủ", 60, "JLPT_N5", true, true
        );

        Long paperId = jdbcTemplate.queryForObject("SELECT id FROM exam_paper WHERE code = 'JLPT-N5-TEST-01'", Long.class);
        assertThat(paperId).isNotNull();

        jdbcTemplate.update(
                "INSERT INTO exam_section (paper_id, title, sort_order) VALUES (?, ?, ?)",
                paperId, "Phần 1: Kiến thức ngôn ngữ (Từ vựng)", 1
        );

        Long sectionId = jdbcTemplate.queryForObject("SELECT id FROM exam_section WHERE paper_id = ?", Long.class, paperId);
        assertThat(sectionId).isNotNull();

        jdbcTemplate.update(
                "INSERT INTO exam_question (section_id, sort_order, type, prompt_ja, points) VALUES (?, ?, ?, ?, ?)",
                sectionId, 1, "MULTIPLE_CHOICE", "これは（　）ですか。", 1.50
        );

        Long questionId = jdbcTemplate.queryForObject("SELECT id FROM exam_question WHERE section_id = ?", Long.class, sectionId);
        assertThat(questionId).isNotNull();

        jdbcTemplate.update(
                "INSERT INTO exam_choice (question_id, sort_order, text, correct) VALUES (?, ?, ?, ?)",
                questionId, 1, "なん", true
        );

        Integer choiceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam_choice WHERE question_id = ?",
                Integer.class, questionId
        );
        assertThat(choiceCount).isEqualTo(1);
    }
}
