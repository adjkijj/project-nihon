package com.nihon.dto.excel;

/**
 * Chế độ xử lý dữ liệu khi gặp bản ghi trùng lặp trong quá trình import:
 * - {@link #SKIP_DUPLICATES}: Bỏ qua, giữ nguyên bản ghi hiện có trong database (mặc định).
 * - {@link #UPDATE_EXISTING}: Cập nhật nội dung mới cho bản ghi đã tồn tại.
 */
public enum ImportMode {

    /**
     * Bỏ qua các bản ghi đã tồn tại trong database (mặc định).
     */
    SKIP_DUPLICATES,

    /**
     * Cập nhật thông tin mới từ tệp Excel vào các bản ghi đã tồn tại trong database.
     */
    UPDATE_EXISTING
}
