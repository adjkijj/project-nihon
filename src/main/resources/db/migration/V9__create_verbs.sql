-- =============================================================================
-- Migration V9: Quản lý động từ tiếng Nhật và chia động từ (F13, F16)
-- Hỗ trợ đầy đủ bộ ký tự UTF-8 / Unicode (Hiragana, Katakana, Kanji, tiếng Việt có dấu)
-- =============================================================================

CREATE TABLE IF NOT EXISTS verb (
    id BIGINT NOT NULL AUTO_INCREMENT,
    topic_id BIGINT NULL,
    masu_form VARCHAR(100) NOT NULL,
    dictionary_form VARCHAR(100) NOT NULL,
    kanji_text VARCHAR(100) NULL,
    verb_group INT NOT NULL,
    meaning_vi VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_verb_dict_kanji UNIQUE (dictionary_form, kanji_text),
    CONSTRAINT fk_verb_topic FOREIGN KEY (topic_id) REFERENCES topic (id) ON DELETE SET NULL
);

CREATE INDEX idx_verb_dictionary_form ON verb (dictionary_form);
CREATE INDEX idx_verb_topic_id ON verb (topic_id);
CREATE INDEX idx_verb_verb_group ON verb (verb_group);
