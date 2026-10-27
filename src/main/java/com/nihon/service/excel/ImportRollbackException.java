package com.nihon.service.excel;

import com.nihon.dto.excel.RowError;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * Ngoại lệ ném ra trong quá trình commit dữ liệu khi phát hiện có bất kỳ lỗi dòng dữ liệu nào,
 * nhằm kích hoạt cơ chế hoàn tác (rollback) toàn bộ transaction, đảm bảo không ghi dữ liệu dở dang (không ghi một phần).
 */
@Getter
public class ImportRollbackException extends RuntimeException {

    private final List<RowError> errors;

    /**
     * Khởi tạo ngoại lệ với danh sách lỗi dòng dữ liệu cần hoàn tác.
     *
     * @param message thông báo lý do hoàn tác
     * @param errors  danh sách lỗi dòng phát hiện được
     */
    public ImportRollbackException(String message, List<RowError> errors) {
        super(message);
        this.errors = errors != null ? Collections.unmodifiableList(errors) : Collections.emptyList();
    }
}
