package com.nihon.dto.excel;

/**
 * DTO đại diện cho một hàng từ vựng hợp lệ được trích xuất từ sheet "New Word".
 *
 * @param rowNumber số thứ tự của hàng trong file Excel (bắt đầu từ 1)
 * @param topic tên chủ đề áp dụng cho từ vựng này
 * @param japanese từ vựng tiếng Nhật
 * @param romaji phiên âm Romaji
 * @param meaningVi ý nghĩa tiếng Việt
 */
public record WordRow(
        int rowNumber,
        String topic,
        String japanese,
        String romaji,
        String meaningVi
) {
}
