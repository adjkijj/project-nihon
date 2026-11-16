-- =============================================================================
-- Migration V12: Quản lý kết quả bài thi (exam_attempt) và câu trả lời (exam_attempt_answer) (F40 - F45)
-- Lưu trữ bền vững điểm số, câu trả lời, trạng thái đúng sai và chấm điểm tự luận/dịch thuật
-- =============================================================================

-- 1. Bảng exam_attempt: Lượt làm bài thi của học viên
CREATE TABLE IF NOT EXISTS exam_attempt (
    id VARCHAR(36) NOT NULL,
    paper_id BIGINT NULL,
    learner_id VARCHAR(36) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    submitted_at TIMESTAMP NULL,
    duration_seconds INT NOT NULL DEFAULT 0,
    total_points DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    max_points DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_exam_attempt_paper FOREIGN KEY (paper_id) REFERENCES exam_paper (id) ON DELETE SET NULL
);

CREATE INDEX idx_exam_attempt_learner_status ON exam_attempt (learner_id, status);
CREATE INDEX idx_exam_attempt_paper_id ON exam_attempt (paper_id);
CREATE INDEX idx_exam_attempt_submitted_at ON exam_attempt (submitted_at);

-- 2. Bảng exam_attempt_answer: Chi tiết câu trả lời và điểm số từng câu hỏi
CREATE TABLE IF NOT EXISTS exam_attempt_answer (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attempt_id VARCHAR(36) NOT NULL,
    question_id BIGINT NULL,
    answer_value VARCHAR(1000) NULL,
    points_awarded DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    correct BOOLEAN NULL,
    flagged BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT fk_exam_attempt_answer_attempt FOREIGN KEY (attempt_id) REFERENCES exam_attempt (id) ON DELETE CASCADE
);

CREATE INDEX idx_exam_attempt_answer_attempt ON exam_attempt_answer (attempt_id);
CREATE INDEX idx_exam_attempt_answer_question ON exam_attempt_answer (question_id);

