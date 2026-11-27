-- =============================================================================
-- Migration V10: Quản lý mô hình dữ liệu đề thi, phần thi và câu hỏi (F41)
-- Hỗ trợ đầy đủ bộ ký tự UTF-8 / Unicode (Hiragana, Katakana, Kanji, tiếng Việt có dấu)
-- =============================================================================

-- 1. Bảng exam_paper: Đề thi
CREATE TABLE IF NOT EXISTS exam_paper (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(1000) NULL,
    duration_minutes INT NOT NULL,
    category VARCHAR(40) NOT NULL,
    topic_id BIGINT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_exam_paper_code UNIQUE (code),
    CONSTRAINT fk_exam_paper_topic FOREIGN KEY (topic_id) REFERENCES topic (id) ON DELETE SET NULL
);

CREATE INDEX idx_exam_paper_topic_id ON exam_paper (topic_id);
CREATE INDEX idx_exam_paper_category ON exam_paper (category);
CREATE INDEX idx_exam_paper_published ON exam_paper (published);

-- 2. Bảng exam_section: Phần thi trong đề thi
CREATE TABLE IF NOT EXISTS exam_section (
    id BIGINT NOT NULL AUTO_INCREMENT,
    paper_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL DEFAULT 1,
    instructions VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_exam_section_paper FOREIGN KEY (paper_id) REFERENCES exam_paper (id) ON DELETE CASCADE
);

CREATE INDEX idx_exam_section_paper_sort ON exam_section (paper_id, sort_order);

-- 3. Bảng exam_question: Câu hỏi thi
CREATE TABLE IF NOT EXISTS exam_question (
    id BIGINT NOT NULL AUTO_INCREMENT,
    section_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 1,
    type VARCHAR(30) NOT NULL,
    prompt_ja VARCHAR(1000) NULL,
    prompt_vi VARCHAR(1000) NULL,
    points DECIMAL(5,2) NOT NULL DEFAULT 1.00,
    explanation VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_exam_question_section FOREIGN KEY (section_id) REFERENCES exam_section (id) ON DELETE CASCADE
);

CREATE INDEX idx_exam_question_section_sort ON exam_question (section_id, sort_order);

-- 4. Bảng exam_choice: Lựa chọn câu hỏi (cho MULTIPLE_CHOICE hoặc các mảnh ORDERING)
CREATE TABLE IF NOT EXISTS exam_choice (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 1,
    text VARCHAR(500) NOT NULL,
    correct BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT fk_exam_choice_question FOREIGN KEY (question_id) REFERENCES exam_question (id) ON DELETE CASCADE
);

CREATE INDEX idx_exam_choice_question_sort ON exam_choice (question_id, sort_order);

-- 5. Bảng exam_answer_key: Đáp án chấp nhận, bài mẫu, từ khóa hoặc thứ tự
CREATE TABLE IF NOT EXISTS exam_answer_key (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    answer_text VARCHAR(1000) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_exam_answer_key_question FOREIGN KEY (question_id) REFERENCES exam_question (id) ON DELETE CASCADE
);

CREATE INDEX idx_exam_answer_key_question_kind ON exam_answer_key (question_id, kind);

