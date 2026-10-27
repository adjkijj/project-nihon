package com.nihon.service.excel;

/**
 * Ngoại lệ ném ra khi tệp tin tải lên bị từ chối do không đạt các tiêu chuẩn kiểm tra an toàn.
 * Thông điệp chứa thông báo chung chung, an toàn để phản hồi cho người dùng mà không làm lộ chi tiết hệ thống.
 */
public class UploadRejectedException extends RuntimeException {

    /**
     * Khởi tạo ngoại lệ với thông điệp an toàn cho người dùng.
     *
     * @param userMessage thông báo lỗi chung chung dành cho người dùng
     */
    public UploadRejectedException(String userMessage) {
        super(userMessage);
    }

    /**
     * Khởi tạo ngoại lệ với thông điệp an toàn cho người dùng và nguyên nhân nội bộ.
     *
     * @param userMessage thông báo lỗi chung chung dành cho người dùng
     * @param cause       nguyên nhân nội bộ (để ghi log, không trả về cho client)
     */
    public UploadRejectedException(String userMessage, Throwable cause) {
        super(userMessage, cause);
    }
}
