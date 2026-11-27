-- =============================================================================
-- Migration V1: Tạo các bảng cơ sở dữ liệu ban đầu cho hệ thống học tiếng Nhật
-- Hỗ trợ đầy đủ bộ ký tự UTF-8 / Unicode (tiếng Nhật Furigana, Kanji và tiếng Việt có dấu)
-- =============================================================================

CREATE TABLE IF NOT EXISTS topic (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    parent_id BIGINT NULL,
    sort_order INT NOT NULL,
    source_line INT NULL,
    kanji_section BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_topic_source_line UNIQUE (source_line),
    CONSTRAINT fk_topic_parent FOREIGN KEY (parent_id) REFERENCES topic (id) ON DELETE RESTRICT
);
CREATE INDEX idx_topic_parent ON topic (parent_id);
CREATE INDEX idx_topic_sort ON topic (sort_order);

CREATE TABLE IF NOT EXISTS vocabulary (
    id BIGINT NOT NULL AUTO_INCREMENT,
    topic_id BIGINT NOT NULL,
    japanese_text VARCHAR(255) NOT NULL,
    reading VARCHAR(255) NULL,
    romaji VARCHAR(255) NOT NULL,
    meaning_vi VARCHAR(500) NOT NULL,
    has_kanji BOOLEAN NOT NULL DEFAULT FALSE,
    source_line INT NULL,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_vocabulary_source_line UNIQUE (source_line),
    CONSTRAINT uk_vocabulary_topic_japanese UNIQUE (topic_id, japanese_text),
    CONSTRAINT fk_vocabulary_topic FOREIGN KEY (topic_id) REFERENCES topic (id) ON DELETE RESTRICT
);
CREATE INDEX idx_vocabulary_topic ON vocabulary (topic_id);
CREATE INDEX idx_vocabulary_japanese ON vocabulary (japanese_text);

CREATE TABLE IF NOT EXISTS kanji_entry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    topic_id BIGINT NOT NULL,
    kanji_character VARCHAR(8) NOT NULL,
    han_viet VARCHAR(100) NOT NULL,
    meaning_vi VARCHAR(255) NOT NULL,
    onyomi VARCHAR(100) NULL,
    kunyomi VARCHAR(100) NULL,
    source_line INT NOT NULL,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_kanji_character UNIQUE (kanji_character),
    CONSTRAINT uk_kanji_source_line UNIQUE (source_line),
    CONSTRAINT fk_kanji_topic FOREIGN KEY (topic_id) REFERENCES topic (id) ON DELETE RESTRICT
);
CREATE INDEX idx_kanji_topic ON kanji_entry (topic_id);

CREATE TABLE IF NOT EXISTS kanji_word (
    id BIGINT NOT NULL AUTO_INCREMENT,
    kanji_id BIGINT NOT NULL,
    japanese VARCHAR(255) NOT NULL,
    romaji VARCHAR(255) NOT NULL,
    meaning_vi VARCHAR(500) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_kanji_word_kanji_japanese UNIQUE (kanji_id, japanese),
    CONSTRAINT fk_kanji_word_kanji FOREIGN KEY (kanji_id) REFERENCES kanji_entry (id) ON DELETE CASCADE
);
CREATE INDEX idx_kanji_word_kanji ON kanji_word (kanji_id);

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
    PRIMARY KEY (id)
);
CREATE INDEX idx_import_audit_imported_at ON import_audit_log (imported_at);

