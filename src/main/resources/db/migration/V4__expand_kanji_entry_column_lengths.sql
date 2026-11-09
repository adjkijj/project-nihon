-- =============================================================================
-- Migration V4: Nâng độ dài cột han_viet, onyomi, kunyomi của bảng kanji_entry lên 255 ký tự
-- (meaning_vi giữ nguyên 255 ký tự) để đồng bộ với KanjiSheetParser và VocabularyFileParser.
-- Áp dụng cho MySQL (utf8mb4 / InnoDB) và tương thích H2 (MODE=MySQL).
--
-- An toàn khi chạy lại (idempotent) và bảo toàn toàn bộ dữ liệu hiện có trong database.
-- =============================================================================

ALTER TABLE kanji_entry MODIFY COLUMN han_viet VARCHAR(255) NULL;
ALTER TABLE kanji_entry MODIFY COLUMN meaning_vi VARCHAR(255) NULL;
ALTER TABLE kanji_entry MODIFY COLUMN onyomi VARCHAR(255) NULL;
ALTER TABLE kanji_entry MODIFY COLUMN kunyomi VARCHAR(255) NULL;

