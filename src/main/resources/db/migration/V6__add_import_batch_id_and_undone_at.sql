-- =============================================================================
-- Migration V6: Thêm cột import_batch_id (BIGINT NULL) cho bảng topic, vocabulary,
-- kanji_entry và cột undone_at (DATETIME NULL) cho bảng import_audit_log để hỗ trợ
-- hoàn tác (undo) một lần import theo batch.
--
-- An toàn khi chạy lại (idempotent) và không làm ảnh hưởng dữ liệu hiện có (import_batch_id = NULL).
-- =============================================================================

ALTER TABLE topic ADD COLUMN IF NOT EXISTS import_batch_id BIGINT NULL;
ALTER TABLE vocabulary ADD COLUMN IF NOT EXISTS import_batch_id BIGINT NULL;
ALTER TABLE kanji_entry ADD COLUMN IF NOT EXISTS import_batch_id BIGINT NULL;
ALTER TABLE import_audit_log ADD COLUMN IF NOT EXISTS undone_at DATETIME NULL;

CREATE INDEX IF NOT EXISTS idx_topic_import_batch ON topic (import_batch_id);
CREATE INDEX IF NOT EXISTS idx_vocabulary_import_batch ON vocabulary (import_batch_id);
CREATE INDEX IF NOT EXISTS idx_kanji_import_batch ON kanji_entry (import_batch_id);

