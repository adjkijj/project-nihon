-- =============================================================================
-- Migration V14: Bảng theo dõi tiến độ học ngữ pháp theo người học ẩn danh (F30)
-- =============================================================================

CREATE TABLE IF NOT EXISTS grammar_progress (
    learner_id VARCHAR(36) NOT NULL,
    grammar_id BIGINT NOT NULL,
    learned_at TIMESTAMP NOT NULL,
    PRIMARY KEY (learner_id, grammar_id),
    CONSTRAINT fk_grammar_progress_grammar FOREIGN KEY (grammar_id) REFERENCES grammar_topic (id) ON DELETE CASCADE
);

CREATE INDEX idx_grammar_progress_learner_time ON grammar_progress (learner_id, learned_at);

