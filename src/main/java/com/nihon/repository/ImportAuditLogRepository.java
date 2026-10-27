package com.nihon.repository;

import com.nihon.entity.ImportAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository truy xuất dữ liệu nhật ký kiểm toán import tệp tin (bảng import_audit_log).
 */
public interface ImportAuditLogRepository extends JpaRepository<ImportAuditLog, Long> {

    /**
     * Tìm danh sách lịch sử import của một người dùng theo thời gian giảm dần.
     *
     * @param userId mã định danh người dùng
     * @return danh sách audit log
     */
    List<ImportAuditLog> findByUserIdOrderByImportedAtDesc(String userId);

    /**
     * Tìm audit log theo mã băm SHA-256 của tệp tin.
     *
     * @param fileHashSha256 chuỗi băm 64 ký tự hex
     * @return danh sách các lần tệp tin tương ứng được import
     */
    List<ImportAuditLog> findByFileHashSha256OrderByImportedAtDesc(String fileHashSha256);
}
