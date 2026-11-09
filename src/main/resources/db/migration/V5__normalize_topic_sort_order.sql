-- =============================================================================
-- Migration V5: Chuẩn hóa một lần cột sort_order của bảng topic để đảm bảo
-- không có hai bản ghi nào trùng giá trị sort_order, giữ nguyên thứ tự hiện tại
-- theo (sort_order ASC, id ASC).
-- =============================================================================

UPDATE topic t
JOIN (
    SELECT id,
           ROW_NUMBER() OVER (ORDER BY sort_order ASC, id ASC) AS new_sort_order
    FROM topic
) ranked ON t.id = ranked.id
SET t.sort_order = ranked.new_sort_order;

