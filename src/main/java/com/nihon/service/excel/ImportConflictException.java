package com.nihon.service.excel;

/**
 * Ngoại lệ ném ra khi phát hiện xung đột dữ liệu đồng thời do vi phạm ràng buộc độc nhất (unique constraint),
 * giúp chuyển đổi lỗi kỹ thuật tầng cơ sở dữ liệu thành thông báo rõ nghĩa, thân thiện với người dùng.
 */
public class ImportConflictException extends RuntimeException {

    /**
     * Khởi tạo ngoại lệ xung đột dữ liệu với thông điệp rõ nghĩa.
     *
     * @param message thông điệp mô tả nguyên nhân xung đột
     */
    public ImportConflictException(String message) {
        super(message);
    }

    /**
     * Khởi tạo ngoại lệ xung đột dữ liệu với thông điệp và nguyên nhân ban đầu.
     *
     * @param message thông điệp mô tả nguyên nhân xung đột
     * @param cause   nguyên nhân kỹ thuật
     */
    public ImportConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
