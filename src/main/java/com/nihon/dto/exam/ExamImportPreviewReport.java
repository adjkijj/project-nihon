package com.nihon.dto.exam;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Báo cáo xem trước kết quả thẩm định tệp Excel nhập đề thi (F41).
 * Lưu tạm trong ImportSessionStorage trong 10 phút.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamImportPreviewReport implements Serializable {
    private static final long serialVersionUID = 1L;

    public record RowError(int rowNumber, String column, String message) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    private String token;
    private String fileName;
    private String paperCode;
    private String paperTitle;
    private int durationMinutes;
    private String category;

    private int sectionCount;
    private int questionCount;

    @Builder.Default
    private List<ExamExcelRow> rows = new ArrayList<>();

    @Builder.Default
    private List<String> globalErrors = new ArrayList<>();

    public boolean canCommit() {
        if (!globalErrors.isEmpty()) {
            return false;
        }
        if (rows == null || rows.isEmpty()) {
            return false;
        }
        return rows.stream().noneMatch(ExamExcelRow::hasErrors);
    }

    public boolean isValid() {
        return canCommit();
    }

    public int getSectionsCount() {
        return sectionCount;
    }

    public int getQuestionsCount() {
        return questionCount;
    }

    public int getValidRowsCount() {
        return rows != null ? (int) rows.stream().filter(r -> !r.hasErrors()).count() : 0;
    }

    public List<RowError> getErrors() {
        List<RowError> list = new ArrayList<>();
        if (globalErrors != null) {
            for (String ge : globalErrors) {
                list.add(new RowError(0, "Toàn thể", ge));
            }
        }
        if (rows != null) {
            for (ExamExcelRow row : rows) {
                if (row.getErrors() != null) {
                    for (String err : row.getErrors()) {
                        list.add(new RowError(row.getRowNum(), "Cột", err));
                    }
                }
            }
        }
        return list;
    }

    public int getTotalErrors() {
        return getErrors().size();
    }
}
