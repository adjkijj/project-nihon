package com.nihon.config;

import com.nihon.dto.ImportReport;
import com.nihon.service.VocabularyImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tự động nạp dữ liệu từ file từ vựng khi khởi động ứng dụng nếu file tồn tại.
 * Chạy cơ chế idempotent: không xóa dữ liệu cũ, không tạo bản ghi trùng.
 * Có thể bật/tắt qua thuộc tính: nihon.import.auto-enabled=true/false.
 */
@Component
@ConditionalOnProperty(name = "nihon.import.auto-enabled", havingValue = "true", matchIfMissing = true)
public class VocabularyDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VocabularyDataInitializer.class);

    private final VocabularyImportService importService;

    @Value("${nihon.import.file-path:tu-vung-tieng-nhat-da-sap-xep.md}")
    private String filePath;

    public VocabularyDataInitializer(VocabularyImportService importService) {
        this.importService = importService;
    }

    @Override
    public void run(ApplicationArguments args) {
        Path path = Path.of(filePath);
        if (Files.exists(path)) {
            log.info("Starting automatic vocabulary data import from file...");
            ImportReport report = importService.importFrom(path);
            if (report.isAborted()) {
                log.warn("Automatic vocabulary data import aborted due to issues. Error count: {}", report.getErrorCount());
            } else {
                log.info("Automatic vocabulary data import finished: inserted={}, skippedAlreadyImported={}, skippedDuplicateInFile={}",
                        report.getInserted(), report.getSkippedAlreadyImported(), report.getSkippedDuplicateInFile());
            }
        } else {
            log.info("Source file not found at [{}]. Skipping automatic import.", filePath);
        }
    }
}
