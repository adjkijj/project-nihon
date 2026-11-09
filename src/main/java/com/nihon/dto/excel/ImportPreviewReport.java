package com.nihon.dto.excel;

import java.util.Collections;
import java.util.List;

/**
 * Báo cáo xem trước (preview) kết quả phân tích tệp Excel trước khi ghi vào cơ sở dữ liệu:
 * <ul>
 *   <li>Số lượng chủ đề mới</li>
 *   <li>Số lượng từ mới (bao gồm từ ở sheet "New Word" và từ cần nhớ ở sheet "Kanji" lưu chung vào bảng {@code vocabulary})</li>
 *   <li>Số lượng từ trùng (sẽ bỏ qua hoặc cập nhật tùy thuộc vào chế độ import)</li>
 *   <li>Số lượng Kanji mới</li>
 *   <li>Số lượng Kanji trùng</li>
 *   <li>Số lượng từ cần nhớ ở nhánh Kanji mới (thống kê chi tiết cho giao diện preview)</li>
 *   <li>Số lượng từ cần nhớ ở nhánh Kanji trùng (thống kê chi tiết cho giao diện preview)</li>
 *   <li>Danh sách các lỗi dòng dữ liệu (RowError)</li>
 * </ul>
 */
public record ImportPreviewReport(
        int newTopicsCount,
        int newWordsCount,
        int duplicateWordsCount,
        int newKanjiCount,
        int duplicateKanjiCount,
        int newKanjiWordsCount,
        int duplicateKanjiWordsCount,
        List<RowError> errors
) {

    public ImportPreviewReport {
        if (errors == null) {
            errors = Collections.emptyList();
        }
    }

    public ImportPreviewReport(
            int newTopicsCount,
            int newWordsCount,
            int duplicateWordsCount,
            int newKanjiCount,
            int duplicateKanjiCount,
            List<RowError> errors
    ) {
        this(newTopicsCount, newWordsCount, duplicateWordsCount, newKanjiCount, duplicateKanjiCount, 0, 0, errors);
    }

    /**
     * Kiểm tra xem tệp tin tải lên có chứa lỗi dòng hay không.
     *
     * @return true nếu có ít nhất một lỗi dòng
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
