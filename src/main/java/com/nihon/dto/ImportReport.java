package com.nihon.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Báo cáo một lần nhập dữ liệu.
 * "aborted = true" nghĩa là phát hiện lỗi/xung đột và KHÔNG có bản ghi nào được ghi.
 */
@Getter
@Setter
public class ImportReport {

    /** Số hàng dữ liệu (không tính hàng tiêu đề/phân cách bảng) đọc được từ file. */
    private int rowsRead;
    private int topicsInserted;
    private int vocabularyInserted;
    private int kanjiInserted;
    /** Hàng đã được nhập ở lần trước (cùng dòng nguồn, cùng nội dung). */
    private int skippedAlreadyImported;
    /** Hàng trùng hoàn toàn với hàng trước đó trong cùng chủ đề (từ vựng) hoặc cùng ký tự (Kanji). */
    private int skippedDuplicateInFile;
    /** Từ có Kanji nhưng nguồn không ghi cách đọc → reading = NULL. */
    private int readingNotProvided;
    /** Từ có Kanji nhưng ngoặc đọc không xác định duy nhất (nhiều ngoặc, ngoặc giữa từ) → reading = NULL. */
    private int readingAmbiguous;
    private boolean aborted;
    private final List<ImportIssue> errors = new ArrayList<>();

    public int getInserted() {
        return vocabularyInserted + kanjiInserted;
    }

    public int getSkipped() {
        return skippedAlreadyImported + skippedDuplicateInFile;
    }

    public int getErrorCount() {
        return errors.size();
    }
}
