package com.nihon.service.excel;

import com.nihon.entity.ImportAuditLog;
import com.nihon.repository.ImportAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Dịch vụ ghi nhật ký kiểm toán (audit log) cho hoạt động import tệp tin.
 * Sử dụng {@link Propagation#REQUIRES_NEW} để bảo đảm nhật ký luôn được lưu trữ độc lập
 * kể cả khi giao dịch nghiệp vụ chính bị hoàn tác (rollback).
 * Do hệ thống không có tài khoản và vai trò, trường định danh lưu trữ địa chỉ IP của client (hoặc "anonymous").
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportAuditService {

    private final ImportAuditLogRepository auditLogRepository;

    /**
     * Lấy danh sách lịch sử import có phân trang (mới nhất trước).
     */
    @Transactional(readOnly = true)
    public Page<ImportAuditLog> findHistory(Pageable pageable) {
        return auditLogRepository.findAllByOrderByImportedAtDescIdDesc(pageable);
    }

    /**
     * Tìm bản ghi kiểm toán theo ID.
     */
    @Transactional(readOnly = true)
    public Optional<ImportAuditLog> findById(Long id) {
        return auditLogRepository.findById(id);
    }

    /**
     * Lưu cập nhật bản ghi kiểm toán (ví dụ: ghi nhận undone_at) trong cùng transaction hiện tại.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public ImportAuditLog saveInCurrentTransaction(ImportAuditLog auditLog) {
        return auditLogRepository.saveAndFlush(auditLog);
    }

    /**
     * Ghi thêm một dòng audit cho hành động hoàn tác (kết quả UNDO, lưu số bản ghi đã xóa)
     * trong cùng transaction của thao tác hoàn tác.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public ImportAuditLog logUndo(String clientIp, ImportAuditLog originalLog, int totalDeleted,
                                  int topicsRetained, String detailMessage) {
        String safeUserId = clientIp != null && !clientIp.isBlank() ? clientIp.trim() : "anonymous";
        if (safeUserId.length() > 100) {
            safeUserId = safeUserId.substring(0, 100);
        }
        String safeFileName = (originalLog != null && originalLog.getFileName() != null)
                ? originalLog.getFileName()
                : "unknown.xlsx";
        String safeHash = (originalLog != null && originalLog.getFileHashSha256() != null)
                ? originalLog.getFileHashSha256()
                : "0".repeat(64);

        ImportAuditLog undoLog = new ImportAuditLog();
        undoLog.setUserId(safeUserId);
        undoLog.setImportedAt(LocalDateTime.now());
        undoLog.setFileName(safeFileName);
        undoLog.setFileHashSha256(safeHash);
        undoLog.setRecordsAdded(0);
        undoLog.setRecordsUpdated(totalDeleted);
        undoLog.setRecordsSkipped(topicsRetained);
        undoLog.setResult("UNDO");
        if (detailMessage != null && detailMessage.length() > 1000) {
            detailMessage = detailMessage.substring(0, 997) + "...";
        }
        undoLog.setErrorMessage(detailMessage);

        ImportAuditLog saved = auditLogRepository.saveAndFlush(undoLog);
        log.info("Undo audit log recorded: id={}, targetBatchId={}, ip/client={}, deleted={}, retainedTopics={}",
                saved.getId(),
                originalLog != null ? originalLog.getId() : null,
                ExcelCellUtils.sanitizeForLog(saved.getUserId()),
                totalDeleted,
                topicsRetained);
        return saved;
    }

    /**
     * Ghi nhận một bản ghi kiểm toán vào cơ sở dữ liệu.
     * Tuyệt đối không ghi nội dung ô vào log hay database audit log.
     *
     * @param clientIp       địa chỉ IP của client (hoặc "anonymous")
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
    public ImportAuditLog log(String clientIp, String cleanFileName, String fileHashSha256,
                              int added, int updated, int skipped, String result, String errorMessage) {
        String safeUserId = clientIp != null && !clientIp.isBlank() ? clientIp.trim() : "anonymous";
        if (safeUserId.length() > 100) {
            safeUserId = safeUserId.substring(0, 100);
        }
        String safeFileName = cleanFileName != null ? cleanFileName : "unknown.xlsx";
        if (safeFileName.length() > 255) {
            safeFileName = safeFileName.substring(0, 255);
        }

        ImportAuditLog auditLog = new ImportAuditLog();
        auditLog.setUserId(safeUserId);
        auditLog.setImportedAt(LocalDateTime.now());
        auditLog.setFileName(safeFileName);
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

        log.info("Audit log recorded: id={}, ip/client={}, file={}, hash={}, added={}, updated={}, skipped={}, result={}",
                saved.getId(),
                ExcelCellUtils.sanitizeForLog(saved.getUserId()),
                ExcelCellUtils.sanitizeForLog(saved.getFileName()),
                saved.getFileHashSha256(),
                added, updated, skipped,
                ExcelCellUtils.sanitizeForLog(result));

        return saved;
    }
}
