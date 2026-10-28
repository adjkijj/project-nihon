-- =============================================================================
-- Migration V2: Cho phép NULL cho các cột không bắt buộc khi import dữ liệu
-- Áp dụng cho MySQL (utf8mb4 / InnoDB)
--
-- 1. Bảng kanji_entry:
--    - han_viet VARCHAR(100) NULL
--    - meaning_vi VARCHAR(255) NULL
--    - onyomi VARCHAR(100) NULL
--    - kunyomi VARCHAR(100) NULL
--
-- 2. Bảng vocabulary:
--    - romaji VARCHAR(255) NULL
--    - meaning_vi VARCHAR(500) NULL
--
-- 3. Bảng kanji_word:
--    - romaji VARCHAR(255) NULL
--    - meaning_vi VARCHAR(500) NULL
--
-- An toàn khi chạy lại và bảo toàn toàn bộ dữ liệu hiện có trong database.
-- =============================================================================

ALTER TABLE kanji_entry MODIFY COLUMN han_viet VARCHAR(100) NULL;
ALTER TABLE kanji_entry MODIFY COLUMN meaning_vi VARCHAR(255) NULL;
ALTER TABLE kanji_entry MODIFY COLUMN onyomi VARCHAR(100) NULL;
ALTER TABLE kanji_entry MODIFY COLUMN kunyomi VARCHAR(100) NULL;

ALTER TABLE vocabulary MODIFY COLUMN romaji VARCHAR(255) NULL;
ALTER TABLE vocabulary MODIFY COLUMN meaning_vi VARCHAR(500) NULL;

ALTER TABLE kanji_word MODIFY COLUMN romaji VARCHAR(255) NULL;
ALTER TABLE kanji_word MODIFY COLUMN meaning_vi VARCHAR(500) NULL;
