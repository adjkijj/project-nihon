-- =============================================================================
-- Migration V13: Quản lý kho ngữ pháp và kiến thức bổ sung (F30-F32)
-- Hỗ trợ đầy đủ bộ ký tự UTF-8 / Unicode (Hiragana, Katakana, Kanji, tiếng Việt có dấu)
-- =============================================================================

-- 1. Bảng grammar_topic: Chủ đề ngữ pháp & kiến thức bổ sung
CREATE TABLE IF NOT EXISTS grammar_topic (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    category VARCHAR(40) NOT NULL,
    topic_id BIGINT NULL,
    formula VARCHAR(500) NULL,
    explanation TEXT NULL,
    notes VARCHAR(1000) NULL,
    sort_order INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_grammar_topic_code UNIQUE (code),
    CONSTRAINT fk_grammar_topic_topic FOREIGN KEY (topic_id) REFERENCES topic (id) ON DELETE SET NULL
);

CREATE INDEX idx_grammar_topic_category ON grammar_topic (category);
CREATE INDEX idx_grammar_topic_topic_id ON grammar_topic (topic_id);
CREATE INDEX idx_grammar_topic_sort_order ON grammar_topic (sort_order);

-- 2. Bảng grammar_example: Các câu ví dụ của chủ đề ngữ pháp
CREATE TABLE IF NOT EXISTS grammar_example (
    id BIGINT NOT NULL AUTO_INCREMENT,
    grammar_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 1,
    japanese VARCHAR(500) NOT NULL,
    reading VARCHAR(500) NULL,
    meaning_vi VARCHAR(500) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_grammar_example_grammar FOREIGN KEY (grammar_id) REFERENCES grammar_topic (id) ON DELETE CASCADE
);

CREATE INDEX idx_grammar_example_grammar_sort ON grammar_example (grammar_id, sort_order);

-- 3. Bảng grammar_quiz_question: Câu hỏi luyện tập ngữ pháp
CREATE TABLE IF NOT EXISTS grammar_quiz_question (
    id BIGINT NOT NULL AUTO_INCREMENT,
    grammar_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 1,
    prompt VARCHAR(500) NOT NULL,
    explanation VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_grammar_quiz_q_grammar FOREIGN KEY (grammar_id) REFERENCES grammar_topic (id) ON DELETE CASCADE
);

CREATE INDEX idx_grammar_quiz_q_grammar_sort ON grammar_quiz_question (grammar_id, sort_order);

-- 4. Bảng grammar_quiz_choice: Lựa chọn trắc nghiệm cho câu hỏi
CREATE TABLE IF NOT EXISTS grammar_quiz_choice (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 1,
    text VARCHAR(300) NOT NULL,
    correct BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT fk_grammar_quiz_choice_q FOREIGN KEY (question_id) REFERENCES grammar_quiz_question (id) ON DELETE CASCADE
);

CREATE INDEX idx_grammar_quiz_choice_q_sort ON grammar_quiz_choice (question_id, sort_order);

