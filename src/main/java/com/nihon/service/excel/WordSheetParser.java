package com.nihon.service.excel;

import com.nihon.dto.excel.ParseResult;
import com.nihon.dto.excel.RowError;
import com.nihon.dto.excel.WordRow;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Bộ phân tích cú pháp sheet "New Word" từ file Excel định dạng .xlsx.
 * Áp dụng các quy tắc chuẩn hóa văn bản NFC, xác thực độ dài, kế thừa chủ đề và chặn công thức độc hại.
 */
@Component
public class WordSheetParser {

    public static final String TARGET_SHEET_NAME = "New Word";

    public static final String HEADER_TOPIC = "Topic";
    public static final String HEADER_JAPANESE = "Tiếng Nhật";
    public static final String HEADER_ROMAJI = "Romaji";
    public static final String HEADER_MEANING = "Nghĩa tiếng Việt";

    public static final int MAX_TOPIC_LENGTH = 255;
    public static final int MAX_JAPANESE_LENGTH = 255;
    public static final int MAX_ROMAJI_LENGTH = 255;
    public static final int MAX_MEANING_LENGTH = 500;

    private final DataFormatter dataFormatter = new DataFormatter();

    /**
     * Phân tích luồng dữ liệu Excel .xlsx và trích xuất danh sách từ vựng hợp lệ kèm danh sách lỗi.
     *
     * @param inputStream luồng dữ liệu của file .xlsx
     * @return {@link ParseResult} chứa danh sách dòng hợp lệ và danh sách lỗi phát sinh
     * @throws ExcelParseException nếu file không đúng định dạng .xlsx hoặc có lỗi I/O
     * @throws IllegalArgumentException nếu inputStream là null
     */
    public ParseResult<WordRow> parse(InputStream inputStream) {
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream không được null");
        }

        List<WordRow> validRows = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();

        try (XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = ExcelCellUtils.findSheetCaseInsensitive(workbook, TARGET_SHEET_NAME);
            if (sheet == null) {
                errors.add(new RowError(0, "Sheet", "Không tìm thấy sheet '" + TARGET_SHEET_NAME + "' (không phân biệt hoa thường)"));
                return new ParseResult<>(validRows, errors);
            }

            // 1. Kiểm tra hàng tiêu đề (Hàng 1 trong Excel, index 0)
            if (sheet.getLastRowNum() < 0) {
                errors.add(new RowError(1, "Header", "Sheet rỗng, không có hàng tiêu đề"));
                return new ParseResult<>(validRows, errors);
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null || !validateHeader(headerRow, errors)) {
                return new ParseResult<>(validRows, errors);
            }

            // 2. Đọc dữ liệu từ hàng 2 trở đi (index 1 đến sheet.getLastRowNum())
            String currentTopic = null;
            int lastRowNum = sheet.getLastRowNum();

            for (int rowIndex = 1; rowIndex <= lastRowNum; rowIndex++) {
                int rowNumber = rowIndex + 1;
                Row row = sheet.getRow(rowIndex);

                if (row == null) {
                    // Hàng trống hoàn toàn trong POI -> bỏ qua
                    continue;
                }

                Cell cellA = row.getCell(0);
                Cell cellB = row.getCell(1);
                Cell cellC = row.getCell(2);
                Cell cellD = row.getCell(3);

                boolean formulaA = ExcelCellUtils.isFormula(cellA);
                boolean formulaB = ExcelCellUtils.isFormula(cellB);
                boolean formulaC = ExcelCellUtils.isFormula(cellC);
                boolean formulaD = ExcelCellUtils.isFormula(cellD);

                String valA = formulaA ? null : ExcelCellUtils.normalizeTopic(dataFormatter, cellA);
                String valB = formulaB ? null : ExcelCellUtils.normalizeCellOrNull(dataFormatter, cellB);
                String valC = formulaC ? null : ExcelCellUtils.normalizeCellOrNull(dataFormatter, cellC);
                String valD = formulaD ? null : ExcelCellUtils.normalizeCellOrNull(dataFormatter, cellD);

                // Hàng trống hoàn toàn thì bỏ qua
                boolean hasAnyFormula = formulaA || formulaB || formulaC || formulaD;
                if (!hasAnyFormula && valA == null && valB == null && valC == null && valD == null) {
                    continue;
                }

                // Mỗi hàng chỉ ghi một lỗi chính
                RowError primaryError = null;

                // Kiểm soát PCI-DSS: Tuyệt đối không nhập số thẻ thanh toán (PAN) vào hệ thống
                if (ExcelCellUtils.containsPotentialPaymentCard(valA)) {
                    primaryError = new RowError(rowNumber, HEADER_TOPIC, "Dữ liệu giống số thẻ thanh toán, không được nhập");
                } else if (ExcelCellUtils.containsPotentialPaymentCard(valB)) {
                    primaryError = new RowError(rowNumber, HEADER_JAPANESE, "Dữ liệu giống số thẻ thanh toán, không được nhập");
                } else if (ExcelCellUtils.containsPotentialPaymentCard(valC)) {
                    primaryError = new RowError(rowNumber, HEADER_ROMAJI, "Dữ liệu giống số thẻ thanh toán, không được nhập");
                } else if (ExcelCellUtils.containsPotentialPaymentCard(valD)) {
                    primaryError = new RowError(rowNumber, HEADER_MEANING, "Dữ liệu giống số thẻ thanh toán, không được nhập");
                }

                // Quy tắc 7: Không thực thi công thức. Ô có công thức bị từ chối
                if (primaryError == null) {
                    if (formulaA) primaryError = new RowError(rowNumber, HEADER_TOPIC, "Ô chứa công thức không được phép");
                    else if (formulaB) primaryError = new RowError(rowNumber, HEADER_JAPANESE, "Ô chứa công thức không được phép");
                    else if (formulaC) primaryError = new RowError(rowNumber, HEADER_ROMAJI, "Ô chứa công thức không được phép");
                    else if (formulaD) primaryError = new RowError(rowNumber, HEADER_MEANING, "Ô chứa công thức không được phép");
                }

                // Quy tắc chủ đề: Cột A có giá trị thì đặt chủ đề hiện tại. Cột A trống thì dùng của hàng trên
                if (valA != null) {
                    currentTopic = valA;
                    if (currentTopic.length() > MAX_TOPIC_LENGTH && primaryError == null) {
                        primaryError = new RowError(rowNumber, HEADER_TOPIC,
                                "Chủ đề vượt quá độ dài tối đa " + MAX_TOPIC_LENGTH + " ký tự (hiện tại: " + currentTopic.length() + ")");
                    }
                } else if (!formulaA) {
                    if ((currentTopic == null || currentTopic.isEmpty()) && primaryError == null) {
                        primaryError = new RowError(rowNumber, HEADER_TOPIC, "Hàng có dữ liệu nhưng chưa có chủ đề (chủ đề trống)");
                    }
                }

                // Cột bắt buộc duy nhất: Cột B (Tiếng Nhật)
                if (!formulaB) {
                    if (valB == null) {
                        if (primaryError == null) {
                            primaryError = new RowError(rowNumber, HEADER_JAPANESE, "Thiếu dữ liệu cột Tiếng Nhật");
                        }
                    } else if (valB.length() > MAX_JAPANESE_LENGTH) {
                        if (primaryError == null) {
                            primaryError = new RowError(rowNumber, HEADER_JAPANESE,
                                    "Từ tiếng Nhật vượt quá độ dài tối đa " + MAX_JAPANESE_LENGTH + " ký tự (hiện tại: " + valB.length() + ")");
                        }
                    }
                }

                // Cột C (Romaji) cho phép trống và lưu NULL
                if (!formulaC && valC != null && valC.length() > MAX_ROMAJI_LENGTH && primaryError == null) {
                    primaryError = new RowError(rowNumber, HEADER_ROMAJI,
                            "Romaji vượt quá độ dài tối đa " + MAX_ROMAJI_LENGTH + " ký tự (hiện tại: " + valC.length() + ")");
                }

                // Cột D (Nghĩa tiếng Việt) cho phép trống và lưu NULL
                if (!formulaD && valD != null && valD.length() > MAX_MEANING_LENGTH && primaryError == null) {
                    primaryError = new RowError(rowNumber, HEADER_MEANING,
                            "Nghĩa tiếng Việt vượt quá độ dài tối đa " + MAX_MEANING_LENGTH + " ký tự (hiện tại: " + valD.length() + ")");
                }

                // Nếu hàng không có lỗi và đã có chủ đề
                if (primaryError == null && currentTopic != null && !currentTopic.isEmpty()) {
                    validRows.add(new WordRow(rowNumber, currentTopic, valB, valC, valD));
                }

                if (primaryError != null) {
                    errors.add(primaryError);
                }
            }

        } catch (IOException | RuntimeException e) {
            // Chặn lỗi định dạng không phải .xlsx hoặc lỗi corrupt stream
            throw new ExcelParseException("Không thể đọc file Excel định dạng .xlsx: " + e.getMessage(), e);
        }

        return new ParseResult<>(validRows, errors);
    }

    /**
     * Kiểm tra hàng 1 đúng 4 tiêu đề: Topic, Tiếng Nhật, Romaji, Nghĩa tiếng Việt.
     */
    private boolean validateHeader(Row headerRow, List<RowError> errors) {
        Cell c0 = headerRow.getCell(0);
        Cell c1 = headerRow.getCell(1);
        Cell c2 = headerRow.getCell(2);
        Cell c3 = headerRow.getCell(3);

        if (ExcelCellUtils.isFormula(c0) || ExcelCellUtils.isFormula(c1)
                || ExcelCellUtils.isFormula(c2) || ExcelCellUtils.isFormula(c3)) {
            errors.add(new RowError(1, "Header", "Hàng tiêu đề chứa công thức không hợp lệ"));
            return false;
        }

        String h0 = ExcelCellUtils.normalizeCell(dataFormatter, c0);
        String h1 = ExcelCellUtils.normalizeCell(dataFormatter, c1);
        String h2 = ExcelCellUtils.normalizeCell(dataFormatter, c2);
        String h3 = ExcelCellUtils.normalizeCell(dataFormatter, c3);

        boolean match = HEADER_TOPIC.equalsIgnoreCase(h0)
                && HEADER_JAPANESE.equalsIgnoreCase(h1)
                && HEADER_ROMAJI.equalsIgnoreCase(h2)
                && HEADER_MEANING.equalsIgnoreCase(h3);

        if (!match) {
            errors.add(new RowError(1, "Header",
                    "Hàng 1 phải đúng 4 tiêu đề: 'Topic', 'Tiếng Nhật', 'Romaji', 'Nghĩa tiếng Việt' (hiện tại: ['"
                            + h0 + "', '" + h1 + "', '" + h2 + "', '" + h3 + "'])"));
            return false;
        }

        return true;
    }
}
