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
    romaji VARCHAR(255) NOT NULL,
    meaning_vi VARCHAR(500) NOT NULL,
    has_kanji BOOLEAN NOT NULL DEFAULT FALSE,
    source_line INT NULL,
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
    han_viet VARCHAR(100) NOT NULL,
    meaning_vi VARCHAR(255) NOT NULL,
    onyomi VARCHAR(100) NULL,
    kunyomi VARCHAR(100) NULL,
    source_line INT NOT NULL,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_kanji_character UNIQUE (kanji_character),
    CONSTRAINT uk_kanji_source_line UNIQUE (source_line),
    CONSTRAINT fk_kanji_topic FOREIGN KEY (topic_id) REFERENCES topic (id) ON DELETE RESTRICT
);

-- 4. Bảng kanji_word: Quản lý các từ vựng cần nhớ thuộc Kanji
CREATE TABLE IF NOT EXISTS kanji_word (
    id BIGINT NOT NULL AUTO_INCREMENT,
    kanji_id BIGINT NOT NULL,
    japanese VARCHAR(255) NOT NULL,
    romaji VARCHAR(255) NOT NULL,
    meaning_vi VARCHAR(500) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_kanji_word_kanji_japanese UNIQUE (kanji_id, japanese),
    CONSTRAINT fk_kanji_word_kanji FOREIGN KEY (kanji_id) REFERENCES kanji_entry (id) ON DELETE CASCADE
);

-- 5. Bảng import_audit_log: Ghi nhật ký kiểm toán cho mỗi lần import tệp Excel
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
    PRIMARY KEY (id)
);

-- Chỉ mục hỗ trợ truy vấn hiệu năng cao
CREATE INDEX IF NOT EXISTS idx_topic_parent ON topic (parent_id);
CREATE INDEX IF NOT EXISTS idx_topic_sort ON topic (sort_order);
CREATE INDEX IF NOT EXISTS idx_vocabulary_topic ON vocabulary (topic_id);
CREATE INDEX IF NOT EXISTS idx_vocabulary_japanese ON vocabulary (japanese_text);
CREATE INDEX IF NOT EXISTS idx_kanji_topic ON kanji_entry (topic_id);
CREATE INDEX IF NOT EXISTS idx_kanji_word_kanji ON kanji_word (kanji_id);
CREATE INDEX IF NOT EXISTS idx_import_audit_imported_at ON import_audit_log (imported_at);
