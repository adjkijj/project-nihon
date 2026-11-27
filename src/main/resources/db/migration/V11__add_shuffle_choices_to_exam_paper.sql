-- =============================================================================
-- Migration V11: Thêm cờ shuffle_choices cho đề thi (F42-F48)
-- Cho phép tùy chọn xáo trộn thứ tự các lựa chọn trong câu trắc nghiệm (mặc định TRUE)
-- =============================================================================

ALTER TABLE exam_paper ADD COLUMN shuffle_choices BOOLEAN NOT NULL DEFAULT TRUE;
