package com.nihon.dto.excel;

/**
 * DTO đại diện cho lỗi phát sinh tại một hàng hoặc một cột trong quá trình đọc file Excel.
 *
 * @param rowNumber số thứ tự của hàng bị lỗi (bắt đầu từ 1, hoặc 0 nếu lỗi cấp sheet)
 * @param column tên cột xảy ra lỗi (ví dụ: "Topic", "Tiếng Nhật", "Romaji", "Nghĩa tiếng Việt")
 * @param message mô tả chi tiết lỗi phát hiện
 */
public record RowError(
        int rowNumber,
        String column,
        String message
) {
}
