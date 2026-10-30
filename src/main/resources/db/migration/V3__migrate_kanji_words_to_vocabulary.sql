-- =============================================================================
-- Migration V3: Chuyển toàn bộ dữ liệu từ kanji_word sang bảng từ vựng chung vocabulary
-- Áp dụng cho MySQL (utf8mb4 / InnoDB)
--
-- Mỗi từ cần nhớ trong kanji_word được chuyển thành một bản ghi trong vocabulary:
-- - topic_id: lấy từ topic_id của Kanji cha (kanji_entry)
-- - japanese_text: giữ nguyên chuỗi gốc từ kanji_word
-- - reading: trích xuất âm đọc trong dấu ngoặc cuối (ví dụ "お酒（おさけ）" -> "おさけ", "地味（な）（じみ）" -> "じみ")
-- - romaji: lấy từ romaji
-- - meaning_vi: lấy từ meaning_vi
-- - has_kanji: 1 (luôn chứa chữ Hán)
-- - source_line: NULL
-- - Chạy an toàn nhiều lần (idempotent): chỉ chèn các từ chưa tồn tại theo cặp (topic_id, japanese_text)
-- =============================================================================

INSERT INTO vocabulary (topic_id, japanese_text, reading, romaji, meaning_vi, has_kanji, source_line, created_at, updated_at)
SELECT 
    k.topic_id,
    kw.japanese,
    NULLIF(REGEXP_REPLACE(REGEXP_SUBSTR(TRIM(kw.japanese), '[（(][^()（）]+[）)]$'), '^[（(]|[）)]$', ''), ''),
    kw.romaji,
    kw.meaning_vi,
    1,
    NULL,
    NOW(),
    NOW()
FROM kanji_word kw
JOIN kanji_entry k ON kw.kanji_id = k.id
WHERE NOT EXISTS (
    SELECT 1 FROM vocabulary v 
    WHERE v.topic_id = k.topic_id AND v.japanese_text = kw.japanese
);
