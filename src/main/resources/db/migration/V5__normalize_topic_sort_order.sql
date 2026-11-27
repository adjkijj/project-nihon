-- =============================================================================
-- Migration V5: Chuẩn hóa một lần cột sort_order của bảng topic để đảm bảo
-- không có hai bản ghi nào trùng giá trị sort_order, giữ nguyên thứ tự hiện tại
-- theo (sort_order ASC, id ASC).
-- =============================================================================

UPDATE topic
SET sort_order = (
    SELECT ranked.new_sort_order
    FROM (
        SELECT id,
               ROW_NUMBER() OVER (ORDER BY sort_order ASC, id ASC) AS new_sort_order
        FROM topic
    ) ranked
    WHERE ranked.id = topic.id
);

