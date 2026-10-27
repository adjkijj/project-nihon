package com.nihon.dto.excel;

import java.util.Collections;
import java.util.List;

/**
 * Báo cáo xem trước (preview) kết quả phân tích tệp Excel trước khi ghi vào cơ sở dữ liệu:
 * <ul>
 *   <li>Số lượng chủ đề mới</li>
 *   <li>Số lượng từ mới</li>
 *   <li>Số lượng từ trùng (sẽ bỏ qua hoặc cập nhật tùy thuộc vào chế độ import)</li>
 *   <li>Số lượng Kanji mới</li>
 *   <li>Số lượng Kanji trùng</li>
 *   <li>Số lượng từ cần nhớ của Kanji mới</li>
 *   <li>Số lượng từ cần nhớ của Kanji trùng</li>
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

    /**
     * Kiểm tra xem tệp tin tải lên có chứa lỗi dòng hay không.
     *
     * @return true nếu có ít nhất một lỗi dòng
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
