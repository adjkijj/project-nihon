package com.nihon.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình bảo mật và giới hạn cho việc tải lên tệp Excel.
 * Đọc giá trị từ cấu hình {@code nihon.upload.excel.*} trong {@code application.properties}.
 */
@Component
@ConfigurationProperties(prefix = "nihon.upload.excel")
@Getter
@Setter
public class ExcelUploadProperties {

    /**
     * Kích thước tệp tối đa (bytes), mặc định 5 MB (5 * 1024 * 1024).
     */
    private long maxFileSizeBytes = 5242880L;

    /**
     * Số hàng tối đa cho phép trên mỗi sheet, mặc định 5000.
     */
    private int maxRowsPerSheet = 5000;

    /**
     * Số cột tối đa cho phép trên mỗi sheet, mặc định 20.
     */
    private int maxColumnsPerSheet = 20;

    /**
     * Tỷ lệ nén tối thiểu (min inflate ratio) để chống zip bomb trong POI, mặc định 0.01 (1%).
     */
    private double minInflateRatio = 0.01;

    /**
     * Kích thước tối đa cho phép của một entry bên trong zip (bytes), mặc định 10 MB.
     */
    private long maxEntrySize = 10485760L;

    /**
     * Kích thước tối đa của đoạn văn bản được giải nén trong POI (bytes), mặc định 10 MB.
     */
    private long maxTextSize = 10485760L;
}
