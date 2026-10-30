package com.nihon.config;

import com.nihon.service.KanjiWordMigrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Runner tự động kiểm tra và di chuyển dữ liệu từ bảng kanji_word sang vocabulary khi ứng dụng khởi động.
 * Đảm bảo tính tương thích và chuyển đổi tự động một lần duy nhất.
 */
@Component
@Order(10)
public class KanjiWordMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KanjiWordMigrationRunner.class);

    private final KanjiWordMigrationService migrationService;

    public KanjiWordMigrationRunner(KanjiWordMigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int migrated = migrationService.migrateAll();
        if (migrated > 0) {
            log.info("KanjiWordMigrationRunner completed: migrated {} records to vocabulary.", migrated);
        }
    }
}
