-- 1. Check all tables and foreign keys
SHOW TABLES;

SELECT TABLE_NAME, COLUMN_NAME, CONSTRAINT_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = 'nihon_db' AND REFERENCED_TABLE_NAME IS NOT NULL;

-- 2. Describe tables
DESCRIBE vocabulary;
DESCRIBE topic;
DESCRIBE kanji_entry;
DESCRIBE kanji_word;

-- 3. Topic 63 details
SELECT * FROM topic WHERE id = 63;

-- 4. 5 sample words from topicId = 63
SELECT id, topic_id, japanese_text, reading, romaji, meaning_vi, has_kanji, source_line, created_at 
FROM vocabulary 
WHERE topic_id = 63 
ORDER BY id ASC 
LIMIT 5;

-- 5. Topics with 'Kanji A2-1'
SELECT id, name, kanji_section, parent_id, sort_order 
FROM topic 
WHERE name LIKE '%Kanji A2-1%';

-- 6. Kanji word counts for Lesson 11 and 12
SELECT t.id AS topic_id, t.name AS topic_name, k.id AS kanji_id, k.kanji_character, COUNT(kw.id) AS word_count
FROM kanji_entry k
JOIN topic t ON k.topic_id = t.id
LEFT JOIN kanji_word kw ON kw.kanji_id = k.id
WHERE t.name LIKE '%Kanji A2-1%'
GROUP BY t.id, t.name, k.id, k.kanji_character, k.source_line
ORDER BY t.id, k.source_line;

-- 7. Check word '店' across topics
SELECT v.id, v.topic_id, t.name AS topic_name, v.japanese_text, v.reading, v.romaji, v.meaning_vi
FROM vocabulary v
JOIN topic t ON v.topic_id = t.id
WHERE v.japanese_text LIKE '%店%'
LIMIT 10;
