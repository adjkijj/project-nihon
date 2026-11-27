-- =============================================================================
-- Migration V8 (F49): lịch sử học tập bền vững theo người học ẩn danh.
-- Không lưu tên, email, IP hay nội dung câu trả lời. Idempotent.
-- =============================================================================
CREATE TABLE IF NOT EXISTS study_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    learner_id CHAR(36) NOT NULL,
    mode VARCHAR(40) NOT NULL,
    topic_id BIGINT NULL,
    total_questions INT NOT NULL DEFAULT 0,
    correct_count INT NOT NULL DEFAULT 0,
    duration_seconds INT NOT NULL DEFAULT 0,
    completed_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_study_session_topic FOREIGN KEY (topic_id) REFERENCES topic (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_study_session_learner_completed ON study_session (learner_id, completed_at);


-- Bảng study_answer: đúng/sai theo từng mục của một phiên (xoá theo phiên).
CREATE TABLE IF NOT EXISTS study_answer (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    vocab_id BIGINT NULL,
    kanji_id BIGINT NULL,
    correct BOOLEAN NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_study_answer_session FOREIGN KEY (session_id) REFERENCES study_session (id) ON DELETE CASCADE
);