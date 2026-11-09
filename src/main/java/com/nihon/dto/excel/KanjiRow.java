package com.nihon.dto.excel;

import java.util.Collections;
import java.util.List;

/**
 * DTO đại diện cho một Kanji hợp lệ kèm danh sách các từ cần nhớ đi kèm trong sheet "Kanji"
 * (các từ cần nhớ sẽ được lưu trực tiếp vào bảng {@code vocabulary} thuộc chủ đề của Kanji).
 *
 * @param rowNumber số thứ tự của hàng định nghĩa Kanji trong file Excel (bắt đầu từ 1)
 * @param topic tên chủ đề áp dụng cho Kanji này
 * @param kanji ký tự Kanji đơn (đúng 1 ký tự thuộc Han script)
 * @param hanViet âm Hán Việt
 * @param meaningVi ý nghĩa tiếng Việt của Kanji
 * @param onReading âm On (Onyomi)
 * @param kunReading âm Kun (Kunyomi)
 * @param words danh sách các dòng từ cần nhớ đi kèm Kanji trên sheet Excel
 */
public record KanjiRow(
        int rowNumber,
        String topic,
        String kanji,
        String hanViet,
        String meaningVi,
        String onReading,
        String kunReading,
        List<KanjiWordRow> words
) {
    public KanjiRow {
        words = words != null ? Collections.unmodifiableList(words) : Collections.emptyList();
    }
}
