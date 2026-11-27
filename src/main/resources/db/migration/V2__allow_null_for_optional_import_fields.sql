-- =============================================================================
-- Migration V2: Cho phép NULL cho các cột không bắt buộc khi import dữ liệu
-- =============================================================================

ALTER TABLE kanji_entry MODIFY COLUMN han_viet VARCHAR(100) NULL;
ALTER TABLE kanji_entry MODIFY COLUMN meaning_vi VARCHAR(255) NULL;
ALTER TABLE kanji_entry MODIFY COLUMN onyomi VARCHAR(100) NULL;
ALTER TABLE kanji_entry MODIFY COLUMN kunyomi VARCHAR(100) NULL;

ALTER TABLE vocabulary MODIFY COLUMN romaji VARCHAR(255) NULL;
ALTER TABLE vocabulary MODIFY COLUMN meaning_vi VARCHAR(500) NULL;

ALTER TABLE kanji_word MODIFY COLUMN romaji VARCHAR(255) NULL;
ALTER TABLE kanji_word MODIFY COLUMN meaning_vi VARCHAR(500) NULL;

