package com.nihon.dto.excel;

import java.util.Collections;
import java.util.List;

/**
 * Kết quả phân tích cú pháp sheet Excel, gồm danh sách các dòng hợp lệ kiểu T và danh sách lỗi.
 *
 * @param <T> kiểu dữ liệu hàng hợp lệ (ví dụ: WordRow, KanjiRow)
 * @param rows danh sách dữ liệu hợp lệ trích xuất thành công
 * @param errors danh sách các lỗi phát hiện tại từng hàng/cột
 */
public record ParseResult<T>(
        List<T> rows,
        List<RowError> errors
) {
    public ParseResult {
        rows = rows != null ? Collections.unmodifiableList(rows) : Collections.emptyList();
        errors = errors != null ? Collections.unmodifiableList(errors) : Collections.emptyList();
    }

    /**
     * Kiểm tra xem quá trình đọc có hoàn toàn không có lỗi nào hay không.
     *
     * @return true nếu không có lỗi
     */
    public boolean isSuccess() {
        return errors.isEmpty();
    }

    /**
     * Kiểm tra xem có lỗi nào phát sinh trong quá trình đọc hay không.
     *
     * @return true nếu có ít nhất một lỗi
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
