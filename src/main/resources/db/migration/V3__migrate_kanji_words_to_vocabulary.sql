-- =============================================================================
-- Migration V3: Chuyển toàn bộ dữ liệu từ kanji_word sang bảng từ vựng chung vocabulary
-- =============================================================================

INSERT INTO vocabulary (topic_id, japanese_text, reading, romaji, meaning_vi, has_kanji, source_line, created_at, updated_at)
SELECT 
    k.topic_id,
    kw.japanese,
    NULL,
    kw.romaji,
    kw.meaning_vi,
    1,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM kanji_word kw
JOIN kanji_entry k ON kw.kanji_id = k.id
WHERE NOT EXISTS (
    SELECT 1 FROM vocabulary v 
    WHERE v.topic_id = k.topic_id AND v.japanese_text = kw.japanese
);

