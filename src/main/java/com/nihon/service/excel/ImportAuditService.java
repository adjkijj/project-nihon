package com.nihon.service.excel;

import com.nihon.entity.ImportAuditLog;
import com.nihon.repository.ImportAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Dịch vụ ghi nhật ký kiểm toán (audit log) cho hoạt động import tệp tin.
 * Sử dụng {@link Propagation#REQUIRES_NEW} để bảo đảm nhật ký luôn được lưu trữ độc lập
 * kể cả khi giao dịch nghiệp vụ chính bị hoàn tác (rollback).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportAuditService {

    private final ImportAuditLogRepository auditLogRepository;

    /**
     * Ghi nhận một bản ghi kiểm toán vào cơ sở dữ liệu.
     * Tuyệt đối không ghi nội dung ô vào log hay database audit log.
     *
     * @param userId         mã định danh người dùng thực hiện
     * @param cleanFileName  tên tệp tin đã được chuẩn hóa (chống path traversal)
     * @param fileHashSha256 mã băm SHA-256 của tệp tin tải lên
     * @param added          số bản ghi thêm mới
     * @param updated        số bản ghi cập nhật
     * @param skipped        số bản ghi bỏ qua
     * @param result         kết quả thực thi ("SUCCESS", "FAILED", "ROLLBACK")
     * @param errorMessage   tóm tắt thông điệp lỗi (nếu có, không chứa dữ liệu ô)
     * @return bản ghi {@link ImportAuditLog} đã được lưu
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportAuditLog log(String userId, String cleanFileName, String fileHashSha256,
                              int added, int updated, int skipped, String result, String errorMessage) {
        ImportAuditLog auditLog = new ImportAuditLog();
        auditLog.setUserId(userId != null && !userId.isBlank() ? userId : "anonymous");
        auditLog.setImportedAt(LocalDateTime.now());
        auditLog.setFileName(cleanFileName);
        auditLog.setFileHashSha256(fileHashSha256);
        auditLog.setRecordsAdded(added);
        auditLog.setRecordsUpdated(updated);
        auditLog.setRecordsSkipped(skipped);
        auditLog.setResult(result);
        if (errorMessage != null && errorMessage.length() > 1000) {
            errorMessage = errorMessage.substring(0, 997) + "...";
        }
        auditLog.setErrorMessage(errorMessage);

        ImportAuditLog saved = auditLogRepository.save(auditLog);

        log.info("Audit log recorded: id={}, user={}, file={}, hash={}, added={}, updated={}, skipped={}, result={}",
                saved.getId(),
                ExcelCellUtils.sanitizeForLog(saved.getUserId()),
                ExcelCellUtils.sanitizeForLog(saved.getFileName()),
                saved.getFileHashSha256(),
                added, updated, skipped,
                ExcelCellUtils.sanitizeForLog(result));

        return saved;
    }
}
