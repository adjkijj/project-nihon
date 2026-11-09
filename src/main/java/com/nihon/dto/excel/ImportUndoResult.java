package com.nihon.dto.excel;

import java.util.Collections;
import java.util.List;

/**
 * Kết quả sau khi thực hiện hoàn tác (undo) một lần import thành công.
 */
public record ImportUndoResult(
        long batchId,
        int vocabDeleted,
        int kanjiDeleted,
        int topicsDeleted,
        int topicsRetained,
        int modifiedAfterImportDeleted,
        List<String> retainedTopicNames,
        Long undoAuditLogId
) {
    public ImportUndoResult {
        retainedTopicNames = (retainedTopicNames != null) ? List.copyOf(retainedTopicNames) : Collections.emptyList();
    }

    public int totalDeleted() {
        return vocabDeleted + kanjiDeleted + topicsDeleted;
    }

    public String toNotificationMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("Đã hoàn tác lần import #").append(batchId)
          .append(": Đã xóa ").append(totalDeleted()).append(" bản ghi (")
          .append(vocabDeleted).append(" từ vựng, ")
          .append(kanjiDeleted).append(" Kanji, ")
          .append(topicsDeleted).append(" chủ đề).");
        if (modifiedAfterImportDeleted > 0) {
            sb.append(" Trong đó có ").append(modifiedAfterImportDeleted)
              .append(" bản ghi đã bị người dùng sửa sau khi import.");
        }
        if (topicsRetained > 0) {
            sb.append(" Giữ lại ").append(topicsRetained).append(" chủ đề (")
              .append(String.join(", ", retainedTopicNames))
              .append(") do đã có dữ liệu thêm vào sau đó.");
        }
        return sb.toString();
    }
}

