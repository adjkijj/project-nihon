package com.nihon.dto.excel;

/**
 * DTO đại diện cho một từ vựng cần nhớ thuộc một Kanji trong sheet "Kanji".
 *
 * @param rowNumber số thứ tự của hàng trong file Excel (bắt đầu từ 1)
 * @param japanese từ vựng tiếng Nhật (cột G)
 * @param romaji phiên âm Romaji (cột H)
 * @param meaningVi ý nghĩa tiếng Việt (cột I)
 */
public record KanjiWordRow(
        int rowNumber,
        String japanese,
        String romaji,
        String meaningVi
) {
}
