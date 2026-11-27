-- =============================================================================
-- PROJECT NIHON - COMPLETE CONSOLIDATED DATABASE SCHEMA (V1 -> V15)
-- Hệ thống học tiếng Nhật và quản lý học liệu Project Nihon
-- Hỗ trợ đầy đủ bộ ký tự UTF-8 / UTF8MB4 (Hiragana, Katakana, Kanji, Tiếng Việt có dấu)
-- Script độc lập, an toàn khi chạy lại (Idempotent: CREATE TABLE IF NOT EXISTS)
-- =============================================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------------------------
-- 0. TẠO CƠ SỞ DỮ LIỆU VÀ CHỌN CƠ SỞ DỮ LIỆU
-- -----------------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS nihon_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE nihon_db;

-- -----------------------------------------------------------------------------
-- 1. BẢNG topic: Quản lý danh mục chủ đề và chủ đề con (cấu trúc phân cấp)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS topic (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    parent_id BIGINT NULL,
    sort_order INT NOT NULL,
    source_line INT NULL,
    kanji_section BOOLEAN NOT NULL DEFAULT FALSE,
    import_batch_id BIGINT NULL,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_topic_source_line UNIQUE (source_line),
    CONSTRAINT fk_topic_parent FOREIGN KEY (parent_id) REFERENCES topic (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_topic_parent ON topic (parent_id);
CREATE INDEX idx_topic_sort ON topic (sort_order);
CREATE INDEX idx_topic_import_batch ON topic (import_batch_id);

-- -----------------------------------------------------------------------------
-- 2. BẢNG vocabulary: Quản lý từ vựng tiếng Nhật, romaji, nghĩa tiếng Việt
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS vocabulary (
    id BIGINT NOT NULL AUTO_INCREMENT,
    topic_id BIGINT NOT NULL,
    japanese_text VARCHAR(255) NOT NULL,
    reading VARCHAR(255) NULL,
    romaji VARCHAR(255) NULL,
    meaning_vi VARCHAR(500) NULL,
    has_kanji BOOLEAN NOT NULL DEFAULT FALSE,
    source_line INT NULL,
    import_batch_id BIGINT NULL,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_vocabulary_source_line UNIQUE (source_line),
    CONSTRAINT uk_vocabulary_topic_japanese UNIQUE (topic_id, japanese_text),
    CONSTRAINT fk_vocabulary_topic FOREIGN KEY (topic_id) REFERENCES topic (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_vocabulary_topic ON vocabulary (topic_id);
CREATE INDEX idx_vocabulary_japanese ON vocabulary (japanese_text);
CREATE INDEX idx_vocabulary_import_batch ON vocabulary (import_batch_id);

-- -----------------------------------------------------------------------------
-- 3. BẢNG kanji_entry: Quản lý Hán tự Kanji, âm Hán Việt, Onyomi, Kunyomi
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS kanji_entry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    topic_id BIGINT NOT NULL,
    kanji_character VARCHAR(8) NOT NULL,
    han_viet VARCHAR(255) NULL,
    meaning_vi VARCHAR(255) NULL,
    onyomi VARCHAR(255) NULL,
    kunyomi VARCHAR(255) NULL,
    source_line INT NOT NULL,
    import_batch_id BIGINT NULL,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_kanji_character UNIQUE (kanji_character),
    CONSTRAINT uk_kanji_source_line UNIQUE (source_line),
    CONSTRAINT fk_kanji_topic FOREIGN KEY (topic_id) REFERENCES topic (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_kanji_topic ON kanji_entry (topic_id);
CREATE INDEX idx_kanji_import_batch ON kanji_entry (import_batch_id);

-- -----------------------------------------------------------------------------
-- 4. BẢNG import_audit_log: Ghi nhật ký kiểm toán tải lên tệp Excel và hoàn tác
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS import_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id VARCHAR(100) NOT NULL,
    imported_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    file_name VARCHAR(255) NOT NULL,
    file_hash_sha256 VARCHAR(64) NOT NULL,
    records_added INT NOT NULL DEFAULT 0,
    records_updated INT NOT NULL DEFAULT 0,
    records_skipped INT NOT NULL DEFAULT 0,
    result VARCHAR(50) NOT NULL,
    error_message VARCHAR(1000) NULL,
    undone_at DATETIME NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_import_audit_imported_at ON import_audit_log (imported_at);

-- -----------------------------------------------------------------------------
-- 5. BẢNG study_session: Lịch sử học tập bền vững theo người học ẩn danh (F49)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS study_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    learner_id CHAR(36) NOT NULL,
    mode VARCHAR(40) NOT NULL,
    topic_id BIGINT NULL,
    total_questions INT NOT NULL DEFAULT 0,
    correct_count INT NOT NULL DEFAULT 0,
    duration_seconds INT NOT NULL DEFAULT 0,
    completed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_study_session_topic FOREIGN KEY (topic_id) REFERENCES topic (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_study_session_learner_completed ON study_session (learner_id, completed_at);

-- -----------------------------------------------------------------------------
-- 6. BẢNG study_answer: Chi tiết đúng/sai từng câu trong phiên học tập
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS study_answer (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    vocab_id BIGINT NULL,
    kanji_id BIGINT NULL,
    correct BOOLEAN NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_study_answer_session FOREIGN KEY (session_id) REFERENCES study_session (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 7. BẢNG verb: Quản lý động từ tiếng Nhật, thể Masu, thể từ điển (F13, F16)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS verb (
    id BIGINT NOT NULL AUTO_INCREMENT,
    topic_id BIGINT NULL,
    masu_form VARCHAR(100) NOT NULL,
    dictionary_form VARCHAR(100) NOT NULL,
    kanji_text VARCHAR(100) NULL,
    verb_group INT NOT NULL,
    meaning_vi VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_verb_dict_kanji UNIQUE (dictionary_form, kanji_text),
    CONSTRAINT fk_verb_topic FOREIGN KEY (topic_id) REFERENCES topic (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_verb_dictionary_form ON verb (dictionary_form);
CREATE INDEX idx_verb_topic_id ON verb (topic_id);
CREATE INDEX idx_verb_verb_group ON verb (verb_group);

-- -----------------------------------------------------------------------------
-- 8. BẢNG exam_paper: Quản lý đề thi tiếng Nhật JLPT (F41, F42)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS exam_paper (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(1000) NULL,
    duration_minutes INT NOT NULL,
    category VARCHAR(40) NOT NULL,
    topic_id BIGINT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    shuffle_choices BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_exam_paper_code UNIQUE (code),
    CONSTRAINT fk_exam_paper_topic FOREIGN KEY (topic_id) REFERENCES topic (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_exam_paper_topic_id ON exam_paper (topic_id);
CREATE INDEX idx_exam_paper_category ON exam_paper (category);
CREATE INDEX idx_exam_paper_published ON exam_paper (published);

-- -----------------------------------------------------------------------------
-- 9. BẢNG exam_section: Phần thi trong đề thi (Từ vựng, Ngữ pháp, Đọc hiểu)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS exam_section (
    id BIGINT NOT NULL AUTO_INCREMENT,
    paper_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL DEFAULT 1,
    instructions VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_exam_section_paper FOREIGN KEY (paper_id) REFERENCES exam_paper (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_exam_section_paper_sort ON exam_section (paper_id, sort_order);

-- -----------------------------------------------------------------------------
-- 10. BẢNG exam_question: Câu hỏi thi
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS exam_question (
    id BIGINT NOT NULL AUTO_INCREMENT,
    section_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 1,
    type VARCHAR(30) NOT NULL,
    prompt_ja VARCHAR(1000) NULL,
    prompt_vi VARCHAR(1000) NULL,
    points DECIMAL(5,2) NOT NULL DEFAULT 1.00,
    explanation VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_exam_question_section FOREIGN KEY (section_id) REFERENCES exam_section (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_exam_question_section_sort ON exam_question (section_id, sort_order);

-- -----------------------------------------------------------------------------
-- 11. BẢNG exam_choice: Lựa chọn trắc nghiệm của câu hỏi thi
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS exam_choice (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 1,
    text VARCHAR(500) NOT NULL,
    correct BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT fk_exam_choice_question FOREIGN KEY (question_id) REFERENCES exam_question (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_exam_choice_question_sort ON exam_choice (question_id, sort_order);

-- -----------------------------------------------------------------------------
-- 12. BẢNG exam_answer_key: Đáp án chuẩn, từ khóa hoặc câu trả lời mẫu
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS exam_answer_key (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    answer_text VARCHAR(1000) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_exam_answer_key_question FOREIGN KEY (question_id) REFERENCES exam_question (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_exam_answer_key_question_kind ON exam_answer_key (question_id, kind);

-- -----------------------------------------------------------------------------
-- 13. BẢNG exam_attempt: Lượt làm bài thi của học viên (F40 - F45)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS exam_attempt (
    id VARCHAR(36) NOT NULL,
    paper_id BIGINT NULL,
    learner_id VARCHAR(36) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at TIMESTAMP NULL,
    duration_seconds INT NOT NULL DEFAULT 0,
    total_points DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    max_points DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_exam_attempt_paper FOREIGN KEY (paper_id) REFERENCES exam_paper (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_exam_attempt_learner_status ON exam_attempt (learner_id, status);
CREATE INDEX idx_exam_attempt_paper_id ON exam_attempt (paper_id);
CREATE INDEX idx_exam_attempt_submitted_at ON exam_attempt (submitted_at);

-- -----------------------------------------------------------------------------
-- 14. BẢNG exam_attempt_answer: Chi tiết câu trả lời và điểm số từng câu hỏi
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS exam_attempt_answer (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attempt_id VARCHAR(36) NOT NULL,
    question_id BIGINT NULL,
    answer_value VARCHAR(1000) NULL,
    points_awarded DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    correct BOOLEAN NULL,
    flagged BOOLEAN NOT NULL DEFAULT FALSE,
    self_approved BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT fk_exam_attempt_answer_attempt FOREIGN KEY (attempt_id) REFERENCES exam_attempt (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_exam_attempt_answer_attempt ON exam_attempt_answer (attempt_id);
CREATE INDEX idx_exam_attempt_answer_question ON exam_attempt_answer (question_id);

-- -----------------------------------------------------------------------------
-- 15. BẢNG grammar_topic: Chủ đề ngữ pháp & kiến thức bổ sung (F30 - F32)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS grammar_topic (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    category VARCHAR(40) NOT NULL,
    topic_id BIGINT NULL,
    formula VARCHAR(500) NULL,
    explanation TEXT NULL,
    notes VARCHAR(1000) NULL,
    sort_order INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_grammar_topic_code UNIQUE (code),
    CONSTRAINT fk_grammar_topic_topic FOREIGN KEY (topic_id) REFERENCES topic (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_grammar_topic_category ON grammar_topic (category);
CREATE INDEX idx_grammar_topic_topic_id ON grammar_topic (topic_id);
CREATE INDEX idx_grammar_topic_sort_order ON grammar_topic (sort_order);

-- -----------------------------------------------------------------------------
-- 16. BẢNG grammar_example: Các câu ví dụ của chủ đề ngữ pháp
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS grammar_example (
    id BIGINT NOT NULL AUTO_INCREMENT,
    grammar_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 1,
    japanese VARCHAR(500) NOT NULL,
    reading VARCHAR(500) NULL,
    meaning_vi VARCHAR(500) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_grammar_example_grammar FOREIGN KEY (grammar_id) REFERENCES grammar_topic (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_grammar_example_grammar_sort ON grammar_example (grammar_id, sort_order);

-- -----------------------------------------------------------------------------
-- 17. BẢNG grammar_quiz_question: Câu hỏi luyện tập ngữ pháp
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS grammar_quiz_question (
    id BIGINT NOT NULL AUTO_INCREMENT,
    grammar_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 1,
    prompt VARCHAR(500) NOT NULL,
    explanation VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_grammar_quiz_q_grammar FOREIGN KEY (grammar_id) REFERENCES grammar_topic (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_grammar_quiz_q_grammar_sort ON grammar_quiz_question (grammar_id, sort_order);

-- -----------------------------------------------------------------------------
-- 18. BẢNG grammar_quiz_choice: Lựa chọn trắc nghiệm cho câu hỏi ngữ pháp
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS grammar_quiz_choice (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 1,
    text VARCHAR(300) NOT NULL,
    correct BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT fk_grammar_quiz_choice_q FOREIGN KEY (question_id) REFERENCES grammar_quiz_question (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_grammar_quiz_choice_q_sort ON grammar_quiz_choice (question_id, sort_order);

-- -----------------------------------------------------------------------------
-- 19. BẢNG grammar_progress: Theo dõi tiến độ học ngữ pháp theo người học ẩn danh (F30)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS grammar_progress (
    learner_id VARCHAR(36) NOT NULL,
    grammar_id BIGINT NOT NULL,
    learned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (learner_id, grammar_id),
    CONSTRAINT fk_grammar_progress_grammar FOREIGN KEY (grammar_id) REFERENCES grammar_topic (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_grammar_progress_learner_time ON grammar_progress (learner_id, learned_at);

-- -----------------------------------------------------------------------------
-- 20. BẢNG kanji_word_backup_20261109: Bảng sao lưu lịch sử kanji_word (tương thích V7)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS kanji_word_backup_20261109 (
    id BIGINT NOT NULL AUTO_INCREMENT,
    kanji_id BIGINT NOT NULL,
    japanese VARCHAR(255) NOT NULL,
    romaji VARCHAR(255) NULL,
    meaning_vi VARCHAR(500) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_kanji_word_kanji FOREIGN KEY (kanji_id) REFERENCES kanji_entry (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- HOÀN TẤT KHỞI TẠO CƠ SỞ DỮ LIỆU NIHON_DB (20 BẢNG TOÀN DIỆN)
-- =============================================================================
