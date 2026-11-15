-- =============================================================================
-- Migration V15: Thêm cột self_approved vào exam_attempt_answer
-- Hỗ trợ người học tự xác nhận câu dịch thuật (TRANSLATION) sau khi nộp bài
-- =============================================================================

ALTER TABLE exam_attempt_answer ADD COLUMN self_approved BOOLEAN NOT NULL DEFAULT FALSE;

