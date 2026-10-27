package com.nihon.service.excel;

/**
 * Ngoại lệ xảy ra khi file Excel bị lỗi định dạng, không mở được, hoặc vi phạm cấu trúc sheet/file.
 */
public class ExcelParseException extends RuntimeException {

    public ExcelParseException(String message) {
        super(message);
    }

    public ExcelParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
