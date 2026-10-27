package com.nihon.dto.excel;

import java.util.Collections;
import java.util.List;

/**
 * Kết quả thực hiện commit import dữ liệu tệp Excel vào cơ sở dữ liệu.
 */
public record ImportCommitResult(
        boolean success,
        int topicsAdded,
        int wordsAdded,
        int wordsUpdated,
        int wordsSkipped,
        int kanjiAdded,
        int kanjiUpdated,
        int kanjiSkipped,
        int kanjiWordsAdded,
        int kanjiWordsUpdated,
        int kanjiWordsSkipped,
        List<RowError> errors,
        String message
) {

    public ImportCommitResult {
        if (errors == null) {
            errors = Collections.emptyList();
        }
    }

    /**
     * Tổng số bản ghi (chủ đề, từ vựng, kanji, từ kanji) đã được thêm mới.
     */
    public int totalAdded() {
        return topicsAdded + wordsAdded + kanjiAdded + kanjiWordsAdded;
    }

    /**
     * Tổng số bản ghi đã được cập nhật nội dung mới.
     */
    public int totalUpdated() {
        return wordsUpdated + kanjiUpdated + kanjiWordsUpdated;
    }

    /**
     * Tổng số bản ghi đã được bỏ qua do trùng lặp.
     */
    public int totalSkipped() {
        return wordsSkipped + kanjiSkipped + kanjiWordsSkipped;
    }

    /**
     * Tạo đối tượng kết quả commit thành công.
     */
    public static ImportCommitResult success(int topicsAdded, int wordsAdded, int wordsUpdated, int wordsSkipped,
                                             int kanjiAdded, int kanjiUpdated, int kanjiSkipped,
                                             int kanjiWordsAdded, int kanjiWordsUpdated, int kanjiWordsSkipped) {
        return new ImportCommitResult(
                true, topicsAdded,
                wordsAdded, wordsUpdated, wordsSkipped,
                kanjiAdded, kanjiUpdated, kanjiSkipped,
                kanjiWordsAdded, kanjiWordsUpdated, kanjiWordsSkipped,
                Collections.emptyList(), "Import dữ liệu thành công."
        );
    }

    /**
     * Tạo đối tượng kết quả commit thất bại do có lỗi dòng dữ liệu.
     */
    public static ImportCommitResult failure(List<RowError> errors, String message) {
        return new ImportCommitResult(
                false, 0,
                0, 0, 0,
                0, 0, 0,
                0, 0, 0,
                errors, message
        );
    }
}
