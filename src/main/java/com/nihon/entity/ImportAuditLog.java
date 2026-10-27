package com.nihon.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Thực thể lưu vết kiểm toán (audit log) cho mỗi lần thực hiện commit import tệp Excel.
 * Tuyệt đối không lưu dữ liệu chi tiết ô nhằm đảm bảo an toàn thông tin và chống lộ dữ liệu nhạy cảm.
 */
@Entity
@Table(name = "import_audit_log",
        indexes = @Index(name = "idx_import_audit_imported_at", columnList = "imported_at"))
@Getter
@Setter
@NoArgsConstructor
public class ImportAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "imported_at", nullable = false)
    private LocalDateTime importedAt;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_hash_sha256", nullable = false, length = 64)
    private String fileHashSha256;

    @Column(name = "records_added", nullable = false)
    private int recordsAdded;

    @Column(name = "records_updated", nullable = false)
    private int recordsUpdated;

    @Column(name = "records_skipped", nullable = false)
    private int recordsSkipped;

    @Column(name = "result", nullable = false, length = 50)
    private String result;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        if (importedAt == null) {
            importedAt = LocalDateTime.now();
        }
    }
}
