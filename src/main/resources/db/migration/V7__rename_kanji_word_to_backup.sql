-- =============================================================================
-- Migration V7: Đổi tên bảng cũ kanji_word thành kanji_word_backup_20261109
-- thay vì xóa ngay, sau khi đã kiểm tra toàn bộ dữ liệu từ cần nhớ trong
-- kanji_word đều đã có bản ghi tương ứng trong bảng vocabulary.
--
-- Cách xóa hẳn sau thời gian theo dõi an toàn (xem thêm README.md):
--   DROP TABLE IF EXISTS kanji_word_backup_20261109;
-- =============================================================================

ALTER TABLE kanji_word RENAME TO kanji_word_backup_20261109;

