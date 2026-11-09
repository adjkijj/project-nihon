package com.nihon.dto.excel;

import java.util.Collections;
import java.util.List;

/**
 * Thông tin xem trước (preview) trước khi người dùng xác nhận hoàn tác (undo) một lần import.
 * Được tính trước bằng truy vấn đếm để hiển thị ngay trong hộp xác nhận.
 */
public record ImportUndoPreview(
        long batchId,
        long vocabToDelete,
        long kanjiToDelete,
        long topicsToDelete,
        long topicsRetained,
        long modifiedAfterImportCount,
        List<String> retainedTopicNames
) {
    public ImportUndoPreview {
        retainedTopicNames = (retainedTopicNames != null) ? List.copyOf(retainedTopicNames) : Collections.emptyList();
    }

    public long totalToDelete() {
        return vocabToDelete + kanjiToDelete + topicsToDelete;
    }

    public String getConfirmMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("Xem trước hoàn tác lần import #").append(batchId)
          .append(": Sẽ xóa ").append(totalToDelete()).append(" bản ghi (")
          .append(vocabToDelete).append(" từ vựng, ")
          .append(kanjiToDelete).append(" Kanji, ")
          .append(topicsToDelete).append(" chủ đề).");
        if (topicsRetained > 0) {
            sb.append(" Giữ lại ").append(topicsRetained).append(" chủ đề (")
              .append(String.join(", ", retainedTopicNames))
              .append(") do đã có dữ liệu thêm vào sau đó.");
        }
        if (modifiedAfterImportCount > 0) {
            sb.append(" Có ").append(modifiedAfterImportCount)
              .append(" bản ghi đã bị chỉnh sửa sau khi import (vẫn sẽ bị xóa).");
        }
        sb.append(" Bạn có chắc chắn muốn hoàn tác?");
        return sb.toString();
    }
}

