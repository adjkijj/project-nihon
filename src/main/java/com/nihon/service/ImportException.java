package com.nihon.service;

/**
 * Lỗi kỹ thuật khi nhập (không đọc được file, lỗi database...).
 * Ném ra trong transaction nên toàn bộ thay đổi của lần nhập bị rollback.
 */
public class ImportException extends RuntimeException {

    public ImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
