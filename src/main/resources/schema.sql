-- =============================================================================
-- Migration DDL: Khởi tạo schema cho project-nihon (Idempotent: an toàn khi chạy lại)
-- Hỗ trợ đầy đủ bộ ký tự UTF-8 / Unicode (tiếng Nhật Furigana, Kanji và tiếng Việt có dấu)
-- =============================================================================

-- 1. Bảng topic: Quản lý chủ đề và chủ đề con
CREATE TABLE IF NOT EXISTS topic (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    parent_id BIGINT NULL,
    sort_order INT NOT NULL,
    source_line INT NULL,
    kanji_section BOOLEAN NOT NULL DEFAULT FALSE,
    import_batch_id BIGINT NULL,
    created_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_topic_source_line UNIQUE (source_line),
    CONSTRAINT fk_topic_parent FOREIGN KEY (parent_id) REFERENCES topic (id) ON DELETE RESTRICT
);

-- 2. Bảng vocabulary: Quản lý từ vựng tiếng Nhật
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
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_vocabulary_source_line UNIQUE (source_line),
    CONSTRAINT uk_vocabulary_topic_japanese UNIQUE (topic_id, japanese_text),
    CONSTRAINT fk_vocabulary_topic FOREIGN KEY (topic_id) REFERENCES topic (id) ON DELETE RESTRICT
);

-- 3. Bảng kanji_entry: Quản lý Hán tự Kanji
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
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_kanji_character UNIQUE (kanji_character),
    CONSTRAINT uk_kanji_source_line UNIQUE (source_line),
    CONSTRAINT fk_kanji_topic FOREIGN KEY (topic_id) REFERENCES topic (id) ON DELETE RESTRICT
);

-- 4. Bảng import_audit_log: Ghi nhật ký kiểm toán cho mỗi lần import tệp Excel
CREATE TABLE IF NOT EXISTS import_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id VARCHAR(100) NOT NULL,
    imported_at TIMESTAMP NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_hash_sha256 VARCHAR(64) NOT NULL,
    records_added INT NOT NULL DEFAULT 0,
    records_updated INT NOT NULL DEFAULT 0,
    records_skipped INT NOT NULL DEFAULT 0,
    result VARCHAR(50) NOT NULL,
    error_message VARCHAR(1000) NULL,
    undone_at TIMESTAMP NULL,
    PRIMARY KEY (id)
);

-- Chỉ mục hỗ trợ truy vấn hiệu năng cao
CREATE INDEX IF NOT EXISTS idx_topic_parent ON topic (parent_id);
CREATE INDEX IF NOT EXISTS idx_topic_sort ON topic (sort_order);
CREATE INDEX IF NOT EXISTS idx_topic_import_batch ON topic (import_batch_id);
CREATE INDEX IF NOT EXISTS idx_vocabulary_topic ON vocabulary (topic_id);
CREATE INDEX IF NOT EXISTS idx_vocabulary_japanese ON vocabulary (japanese_text);
CREATE INDEX IF NOT EXISTS idx_vocabulary_import_batch ON vocabulary (import_batch_id);
CREATE INDEX IF NOT EXISTS idx_kanji_topic ON kanji_entry (topic_id);
CREATE INDEX IF NOT EXISTS idx_kanji_import_batch ON kanji_entry (import_batch_id);
CREATE INDEX IF NOT EXISTS idx_import_audit_imported_at ON import_audit_log (imported_at);

-- 5. Bảng study_session: lịch sử học tập bền vững theo người học ẩn danh (cookie nihon_learner, F49).
--    Chỉ lưu số liệu tổng hợp; không lưu tên, email, IP hay nội dung câu trả lời.
CREATE TABLE IF NOT EXISTS study_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    learner_id CHAR(36) NOT NULL,
    mode VARCHAR(40) NOT NULL,
    topic_id BIGINT NULL,
    total_questions INT NOT NULL DEFAULT 0,
    correct_count INT NOT NULL DEFAULT 0,
    duration_seconds INT NOT NULL DEFAULT 0,
    completed_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_study_session_topic FOREIGN KEY (topic_id) REFERENCES topic (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_study_session_learner_completed ON study_session (learner_id, completed_at);

-- 6. Bảng study_answer: đúng/sai theo từng mục của một phiên (xoá theo phiên).
CREATE TABLE IF NOT EXISTS study_answer (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    vocab_id BIGINT NULL,
    kanji_id BIGINT NULL,
    correct BOOLEAN NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_study_answer_session FOREIGN KEY (session_id) REFERENCES study_session (id) ON DELETE CASCADE
);

-- 7. Bảng verb: Quản lý động từ tiếng Nhật và các thể chia (F13, F16)
CREATE TABLE IF NOT EXISTS verb (
    id BIGINT NOT NULL AUTO_INCREMENT,
    topic_id BIGINT NULL,
    masu_form VARCHAR(100) NOT NULL,
    dictionary_form VARCHAR(100) NOT NULL,
    kanji_text VARCHAR(100) NULL,
    verb_group TINYINT NOT NULL,
    meaning_vi VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_verb_dict_kanji UNIQUE (dictionary_form, kanji_text),
    CONSTRAINT fk_verb_topic FOREIGN KEY (topic_id) REFERENCES topic (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_verb_dictionary_form ON verb (dictionary_form);
CREATE INDEX IF NOT EXISTS idx_verb_topic_id ON verb (topic_id);
CREATE INDEX IF NOT EXISTS idx_verb_verb_group ON verb (verb_group);
