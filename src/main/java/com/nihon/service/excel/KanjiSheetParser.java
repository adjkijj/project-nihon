package com.nihon.service.excel;

import com.nihon.dto.excel.KanjiRow;
import com.nihon.dto.excel.KanjiWordRow;
import com.nihon.dto.excel.ParseResult;
import com.nihon.dto.excel.RowError;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bộ phân tích cú pháp sheet "Kanji" từ file Excel .xlsx.
 * Trích xuất danh sách Hán tự (Kanji) kèm các từ cần nhớ liên thuộc theo đúng cấu trúc phân cấp.
 */
@Component
public class KanjiSheetParser {

    public static final String TARGET_SHEET_NAME = "Kanji";

    public static final String HEADER_TOPIC = "Topic";
    public static final String HEADER_KANJI = "Kanji";
    public static final String HEADER_HAN_VIET = "Hán Việt";
    public static final String HEADER_MEANING = "Ý nghĩa";
    public static final String HEADER_ON = "On";
    public static final String HEADER_KUN = "Kun";

    public static final String HEADER_WORD_JAPANESE = "Tiếng Nhật";
    public static final String HEADER_WORD_ROMAJI = "Romaji";
    public static final String HEADER_WORD_MEANING = "Nghĩa tiếng Việt";

    public static final int MAX_TOPIC_LENGTH = 255;
    public static final int MAX_HAN_VIET_LENGTH = 255;
    public static final int MAX_MEANING_LENGTH = 500;
    public static final int MAX_ON_KUN_LENGTH = 255;
    public static final int MAX_WORD_JAPANESE_LENGTH = 255;
    public static final int MAX_WORD_ROMAJI_LENGTH = 255;
    public static final int MAX_WORD_MEANING_LENGTH = 500;

    private final DataFormatter dataFormatter = new DataFormatter();

    /**
     * Phân tích sheet "Kanji" từ luồng InputStream của file Excel .xlsx.
     *
     * @param inputStream luồng dữ liệu file .xlsx
     * @return {@link ParseResult} chứa danh sách {@link KanjiRow} hợp lệ và danh sách {@link RowError}
     * @throws ExcelParseException nếu file không đúng định dạng .xlsx hoặc có lỗi I/O
     * @throws IllegalArgumentException nếu inputStream là null
     */
    public ParseResult<KanjiRow> parse(InputStream inputStream) {
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream không được null");
        }

        List<KanjiRow> validKanjiList = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();

        try (XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = ExcelCellUtils.findSheetCaseInsensitive(workbook, TARGET_SHEET_NAME);
            if (sheet == null) {
                errors.add(new RowError(0, "Sheet", "Không tìm thấy sheet '" + TARGET_SHEET_NAME + "' (không phân biệt hoa thường)"));
                return new ParseResult<>(validKanjiList, errors);
            }

            // Kiểm tra tối thiểu 2 hàng tiêu đề
            if (sheet.getLastRowNum() < 1) {
                errors.add(new RowError(1, "Header", "Sheet 'Kanji' không đủ 2 hàng tiêu đề"));
                return new ParseResult<>(validKanjiList, errors);
            }

            Row headerRow1 = sheet.getRow(0);
            Row headerRow2 = sheet.getRow(1);
            if (headerRow1 == null || headerRow2 == null || !validateHeaders(headerRow1, headerRow2, errors)) {
                return new ParseResult<>(validKanjiList, errors);
            }

            // Đọc dữ liệu từ hàng 3 trở đi (index 2 đến sheet.getLastRowNum())
            String currentTopic = null;
            KanjiRowBuilder currentKanjiBuilder = null;
            Integer lastInvalidKanjiRow = null;
            Set<String> seenKanjiInFile = new HashSet<>();
            int lastRowNum = sheet.getLastRowNum();

            for (int rowIndex = 2; rowIndex <= lastRowNum; rowIndex++) {
                int rowNumber = rowIndex + 1;
                Row row = sheet.getRow(rowIndex);

                if (row == null) {
                    continue; // Hàng trống trong POI -> bỏ qua
                }

                // Đọc 9 cột từ A đến I (indices 0..8)
                Cell cellA = row.getCell(0); // Topic
                Cell cellB = row.getCell(1); // Kanji
                Cell cellC = row.getCell(2); // Hán Việt
                Cell cellD = row.getCell(3); // Ý nghĩa
                Cell cellE = row.getCell(4); // On
                Cell cellF = row.getCell(5); // Kun
                Cell cellG = row.getCell(6); // Word Tiếng Nhật
                Cell cellH = row.getCell(7); // Word Romaji
                Cell cellI = row.getCell(8); // Word Nghĩa tiếng Việt

                boolean formulaA = ExcelCellUtils.isFormula(cellA);
                boolean formulaB = ExcelCellUtils.isFormula(cellB);
                boolean formulaC = ExcelCellUtils.isFormula(cellC);
                boolean formulaD = ExcelCellUtils.isFormula(cellD);
                boolean formulaE = ExcelCellUtils.isFormula(cellE);
                boolean formulaF = ExcelCellUtils.isFormula(cellF);
                boolean formulaG = ExcelCellUtils.isFormula(cellG);
                boolean formulaH = ExcelCellUtils.isFormula(cellH);
                boolean formulaI = ExcelCellUtils.isFormula(cellI);

                String valA = formulaA ? null : ExcelCellUtils.normalizeTopic(dataFormatter, cellA);
                String valB = formulaB ? null : ExcelCellUtils.normalizeCellOrNull(dataFormatter, cellB);
                String valC = formulaC ? null : ExcelCellUtils.normalizeCellOrNull(dataFormatter, cellC);
                String valD = formulaD ? null : ExcelCellUtils.normalizeCellOrNull(dataFormatter, cellD);
                String valE = formulaE ? null : ExcelCellUtils.normalizeCellOrNull(dataFormatter, cellE);
                String valF = formulaF ? null : ExcelCellUtils.normalizeCellOrNull(dataFormatter, cellF);
                String valG = formulaG ? null : ExcelCellUtils.normalizeCellOrNull(dataFormatter, cellG);
                String valH = formulaH ? null : ExcelCellUtils.normalizeCellOrNull(dataFormatter, cellH);
                String valI = formulaI ? null : ExcelCellUtils.normalizeCellOrNull(dataFormatter, cellI);

                // Quy tắc 3h: Hàng trống hoàn toàn thì bỏ qua
                boolean hasAnyFormula = formulaA || formulaB || formulaC || formulaD || formulaE
                        || formulaF || formulaG || formulaH || formulaI;
                if (!hasAnyFormula && valA == null && valB == null && valC == null && valD == null
                        && valE == null && valF == null && valG == null && valH == null && valI == null) {
                    continue;
                }

                // Mỗi hàng chỉ ghi một lỗi chính (không để một lỗi sinh ra nhiều lỗi dây chuyền)
                RowError primaryError = null;

                // Kiểm soát PCI-DSS: Tuyệt đối không nhập số thẻ thanh toán (PAN) vào hệ thống
                if (ExcelCellUtils.containsPotentialPaymentCard(valA)) {
                    primaryError = new RowError(rowNumber, HEADER_TOPIC, "Dữ liệu giống số thẻ thanh toán, không được nhập");
                } else if (ExcelCellUtils.containsPotentialPaymentCard(valB)) {
                    primaryError = new RowError(rowNumber, HEADER_KANJI, "Dữ liệu giống số thẻ thanh toán, không được nhập");
                } else if (ExcelCellUtils.containsPotentialPaymentCard(valC)) {
                    primaryError = new RowError(rowNumber, HEADER_HAN_VIET, "Dữ liệu giống số thẻ thanh toán, không được nhập");
                } else if (ExcelCellUtils.containsPotentialPaymentCard(valD)) {
                    primaryError = new RowError(rowNumber, HEADER_MEANING, "Dữ liệu giống số thẻ thanh toán, không được nhập");
                } else if (ExcelCellUtils.containsPotentialPaymentCard(valE)) {
                    primaryError = new RowError(rowNumber, HEADER_ON, "Dữ liệu giống số thẻ thanh toán, không được nhập");
                } else if (ExcelCellUtils.containsPotentialPaymentCard(valF)) {
                    primaryError = new RowError(rowNumber, HEADER_KUN, "Dữ liệu giống số thẻ thanh toán, không được nhập");
                } else if (ExcelCellUtils.containsPotentialPaymentCard(valG)) {
                    primaryError = new RowError(rowNumber, HEADER_WORD_JAPANESE, "Dữ liệu giống số thẻ thanh toán, không được nhập");
                } else if (ExcelCellUtils.containsPotentialPaymentCard(valH)) {
                    primaryError = new RowError(rowNumber, HEADER_WORD_ROMAJI, "Dữ liệu giống số thẻ thanh toán, không được nhập");
                } else if (ExcelCellUtils.containsPotentialPaymentCard(valI)) {
                    primaryError = new RowError(rowNumber, HEADER_WORD_MEANING, "Dữ liệu giống số thẻ thanh toán, không được nhập");
                }

                // Quy tắc 5: Ô có công thức bị từ chối
                if (primaryError == null) {
                    if (formulaA) primaryError = new RowError(rowNumber, HEADER_TOPIC, "Ô chứa công thức không được phép");
                    else if (formulaB) primaryError = new RowError(rowNumber, HEADER_KANJI, "Ô chứa công thức không được phép");
                    else if (formulaC) primaryError = new RowError(rowNumber, HEADER_HAN_VIET, "Ô chứa công thức không được phép");
                    else if (formulaD) primaryError = new RowError(rowNumber, HEADER_MEANING, "Ô chứa công thức không được phép");
                    else if (formulaE) primaryError = new RowError(rowNumber, HEADER_ON, "Ô chứa công thức không được phép");
                    else if (formulaF) primaryError = new RowError(rowNumber, HEADER_KUN, "Ô chứa công thức không được phép");
                    else if (formulaG) primaryError = new RowError(rowNumber, HEADER_WORD_JAPANESE, "Ô chứa công thức không được phép");
                    else if (formulaH) primaryError = new RowError(rowNumber, HEADER_WORD_ROMAJI, "Ô chứa công thức không được phép");
                    else if (formulaI) primaryError = new RowError(rowNumber, HEADER_WORD_MEANING, "Ô chứa công thức không được phép");
                }

                // Quy tắc chủ đề: Cột A trống thì dùng chủ đề hàng trên. Nếu chưa có chủ đề nào và cột A trống thì lỗi
                boolean topicError = false;
                if (valA != null) {
                    currentTopic = valA;
                    if (currentTopic.length() > MAX_TOPIC_LENGTH) {
                        if (primaryError == null) {
                            primaryError = new RowError(rowNumber, HEADER_TOPIC,
                                    "Chủ đề vượt quá độ dài tối đa " + MAX_TOPIC_LENGTH + " ký tự (hiện tại: " + currentTopic.length() + ")");
                        }
                        topicError = true;
                    }
                } else if (!formulaA) {
                    if (currentTopic == null || currentTopic.isEmpty()) {
                        if (primaryError == null) {
                            primaryError = new RowError(rowNumber, HEADER_TOPIC, "Hàng có dữ liệu nhưng chưa có chủ đề (chủ đề trống)");
                        }
                        topicError = true;
                    }
                }

                boolean hasKanjiCell = (valB != null) || formulaB;
                boolean hasWordCell = (valG != null) || (valH != null) || (valI != null)
                        || formulaG || formulaH || formulaI;

                if (hasKanjiCell) {
                    // Kiểm tra tính hợp lệ của cột B (Kanji: đúng 1 ký tự Han)
                    boolean kanjiColBValid = false;
                    if (!formulaB && valB != null && (primaryError == null || !HEADER_KANJI.equals(primaryError.column()))) {
                        int codePointCount = valB.codePointCount(0, valB.length());
                        if (codePointCount != 1) {
                            if (primaryError == null) {
                                primaryError = new RowError(rowNumber, HEADER_KANJI,
                                        "Cột Kanji phải là đúng 1 ký tự (hiện tại có " + codePointCount + " ký tự: '" + valB + "')");
                            }
                        } else {
                            int codePoint = valB.codePointAt(0);
                            if (Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.HAN) {
                                if (primaryError == null) {
                                    primaryError = new RowError(rowNumber, HEADER_KANJI,
                                            "Cột Kanji phải là ký tự chữ Hán (Han script), hiện tại: '" + valB + "'");
                                }
                            } else if (!seenKanjiInFile.add(valB)) {
                                if (primaryError == null) {
                                    primaryError = new RowError(rowNumber, HEADER_KANJI,
                                            "Ký tự Kanji '" + valB + "' bị trùng lặp trong file (đã xuất hiện ở hàng trước)");
                                }
                            } else {
                                kanjiColBValid = true;
                            }
                        }
                    }

                    if (kanjiColBValid) {
                        // Đóng gói Kanji trước đó nếu có
                        if (currentKanjiBuilder != null) {
                            if (!currentKanjiBuilder.hasError()) {
                                validKanjiList.add(currentKanjiBuilder.build());
                            }
                            currentKanjiBuilder = null;
                        }

                        // Quy tắc: ngay khi hàng có cột B hợp lệ, đặt nó làm Kanji hiện tại
                        lastInvalidKanjiRow = null;
                        currentKanjiBuilder = new KanjiRowBuilder(rowNumber, currentTopic, valB, valC, valD, valE, valF);

                        // Kiểm tra độ dài tối đa của các cột không bắt buộc C, D, E, F nếu có
                        if (valC != null && valC.length() > MAX_HAN_VIET_LENGTH && primaryError == null) {
                            primaryError = new RowError(rowNumber, HEADER_HAN_VIET, "Hán Việt vượt quá " + MAX_HAN_VIET_LENGTH + " ký tự");
                        }
                        if (valD != null && valD.length() > MAX_MEANING_LENGTH && primaryError == null) {
                            primaryError = new RowError(rowNumber, HEADER_MEANING, "Ý nghĩa vượt quá " + MAX_MEANING_LENGTH + " ký tự");
                        }
                        if (valE != null && valE.length() > MAX_ON_KUN_LENGTH && primaryError == null) {
                            primaryError = new RowError(rowNumber, HEADER_ON, "Âm On vượt quá " + MAX_ON_KUN_LENGTH + " ký tự");
                        }
                        if (valF != null && valF.length() > MAX_ON_KUN_LENGTH && primaryError == null) {
                            primaryError = new RowError(rowNumber, HEADER_KUN, "Âm Kun vượt quá " + MAX_ON_KUN_LENGTH + " ký tự");
                        }

                        if (primaryError != null || topicError) {
                            currentKanjiBuilder.setHasError(true);
                        }

                        // Nếu hàng có kèm từ cần nhớ
                        if (hasWordCell) {
                            RowError wordErr = validateWordRow(rowNumber, valG, valH, valI, formulaG, formulaH, formulaI);
                            if (wordErr != null) {
                                if (primaryError == null) {
                                    primaryError = wordErr;
                                }
                            } else {
                                currentKanjiBuilder.addWord(new KanjiWordRow(rowNumber, valG, valH, valI));
                            }
                        }
                    } else {
                        // Cột B không hợp lệ: đóng gói Kanji trước đó, đặt Kanji hiện tại là null và lưu hàng lỗi
                        if (currentKanjiBuilder != null) {
                            if (!currentKanjiBuilder.hasError()) {
                                validKanjiList.add(currentKanjiBuilder.build());
                            }
                            currentKanjiBuilder = null;
                        }
                        lastInvalidKanjiRow = rowNumber;
                    }

                } else if (hasWordCell) {
                    // Hàng từ cần nhớ độc lập (cột B trống)
                    if (currentKanjiBuilder == null) {
                        if (lastInvalidKanjiRow != null) {
                            if (primaryError == null) {
                                primaryError = new RowError(rowNumber, "Word",
                                        "Hàng chứa từ cần nhớ nhưng chưa có Kanji nào phía trên (hàng mồ côi): Kanji ở hàng " + lastInvalidKanjiRow + " không hợp lệ");
                            }
                        } else {
                            if (primaryError == null) {
                                primaryError = new RowError(rowNumber, "Word",
                                        "Hàng chứa từ cần nhớ nhưng chưa có Kanji nào phía trên (hàng mồ côi)");
                            }
                        }
                    }

                    RowError wordErr = validateWordRow(rowNumber, valG, valH, valI, formulaG, formulaH, formulaI);
                    if (wordErr != null) {
                        if (primaryError == null) {
                            primaryError = wordErr;
                        }
                    } else if (currentKanjiBuilder != null && primaryError == null) {
                        currentKanjiBuilder.addWord(new KanjiWordRow(rowNumber, valG, valH, valI));
                    }
                } else if (valC != null || valD != null || valE != null || valF != null) {
                    if (primaryError == null) {
                        primaryError = new RowError(rowNumber, HEADER_KANJI, "Hàng có thông tin Hán Việt/âm đọc nhưng thiếu ký tự Kanji");
                    }
                }

                if (primaryError != null) {
                    errors.add(primaryError);
                }
            }

            // Đóng gói Kanji cuối cùng nếu có
            if (currentKanjiBuilder != null && !currentKanjiBuilder.hasError()) {
                validKanjiList.add(currentKanjiBuilder.build());
            }

        } catch (IOException | RuntimeException e) {
            throw new ExcelParseException("Không thể đọc file Excel định dạng .xlsx: " + e.getMessage(), e);
        }

        return new ParseResult<>(validKanjiList, errors);
    }

    /**
     * Xác thực các trường của từ cần nhớ: Cột bắt buộc duy nhất là cột G (Tiếng Nhật). Cột H và I cho phép null.
     */
    private RowError validateWordRow(int rowNumber, String valG, String valH, String valI,
                                     boolean formulaG, boolean formulaH, boolean formulaI) {
        if (formulaG) return new RowError(rowNumber, HEADER_WORD_JAPANESE, "Ô chứa công thức không được phép");
        if (formulaH) return new RowError(rowNumber, HEADER_WORD_ROMAJI, "Ô chứa công thức không được phép");
        if (formulaI) return new RowError(rowNumber, HEADER_WORD_MEANING, "Ô chứa công thức không được phép");

        if (ExcelCellUtils.containsPotentialPaymentCard(valG)) {
            return new RowError(rowNumber, HEADER_WORD_JAPANESE, "Dữ liệu giống số thẻ thanh toán, không được nhập");
        }
        if (ExcelCellUtils.containsPotentialPaymentCard(valH)) {
            return new RowError(rowNumber, HEADER_WORD_ROMAJI, "Dữ liệu giống số thẻ thanh toán, không được nhập");
        }
        if (ExcelCellUtils.containsPotentialPaymentCard(valI)) {
            return new RowError(rowNumber, HEADER_WORD_MEANING, "Dữ liệu giống số thẻ thanh toán, không được nhập");
        }

        if (valG == null) {
            return new RowError(rowNumber, HEADER_WORD_JAPANESE, "Từ cần nhớ thiếu cột Tiếng Nhật");
        }
        if (valG.length() > MAX_WORD_JAPANESE_LENGTH) {
            return new RowError(rowNumber, HEADER_WORD_JAPANESE,
                    "Từ tiếng Nhật vượt quá độ dài tối đa " + MAX_WORD_JAPANESE_LENGTH + " ký tự");
        }

        if (valH != null && valH.length() > MAX_WORD_ROMAJI_LENGTH) {
            return new RowError(rowNumber, HEADER_WORD_ROMAJI,
                    "Romaji vượt quá độ dài tối đa " + MAX_WORD_ROMAJI_LENGTH + " ký tự");
        }

        if (valI != null && valI.length() > MAX_WORD_MEANING_LENGTH) {
            return new RowError(rowNumber, HEADER_WORD_MEANING,
                    "Nghĩa tiếng Việt vượt quá độ dài tối đa " + MAX_WORD_MEANING_LENGTH + " ký tự");
        }

        return null;
    }

    /**
     * Kiểm tra tiêu đề hàng 1 (cột A đến F) và hàng 2 (cột G đến I).
     */
    private boolean validateHeaders(Row row1, Row row2, List<RowError> errors) {
        // Kiểm tra công thức ở hàng 1 và 2
        for (int c = 0; c <= 8; c++) {
            if (ExcelCellUtils.isFormula(row1.getCell(c))) {
                errors.add(new RowError(1, "Header", "Hàng tiêu đề 1 chứa công thức không hợp lệ"));
                return false;
            }
            if (ExcelCellUtils.isFormula(row2.getCell(c))) {
                errors.add(new RowError(2, "Header", "Hàng tiêu đề 2 chứa công thức không hợp lệ"));
                return false;
            }
        }

        // Hàng 1 cột A đến F: Topic, Kanji, Hán Việt, Ý nghĩa, On, Kun
        String h1A = ExcelCellUtils.normalizeCell(dataFormatter, row1.getCell(0));
        String h1B = ExcelCellUtils.normalizeCell(dataFormatter, row1.getCell(1));
        String h1C = ExcelCellUtils.normalizeCell(dataFormatter, row1.getCell(2));
        String h1D = ExcelCellUtils.normalizeCell(dataFormatter, row1.getCell(3));
        String h1E = ExcelCellUtils.normalizeCell(dataFormatter, row1.getCell(4));
        String h1F = ExcelCellUtils.normalizeCell(dataFormatter, row1.getCell(5));

        boolean matchRow1 = HEADER_TOPIC.equalsIgnoreCase(h1A)
                && HEADER_KANJI.equalsIgnoreCase(h1B)
                && HEADER_HAN_VIET.equalsIgnoreCase(h1C)
                && HEADER_MEANING.equalsIgnoreCase(h1D)
                && HEADER_ON.equalsIgnoreCase(h1E)
                && HEADER_KUN.equalsIgnoreCase(h1F);

        if (!matchRow1) {
            errors.add(new RowError(1, "Header",
                    "Hàng 1 cột A-F phải đúng: 'Topic', 'Kanji', 'Hán Việt', 'Ý nghĩa', 'On', 'Kun'"));
            return false;
        }

        // Hàng 2 cột G đến I: Tiếng Nhật, Romaji, Nghĩa tiếng Việt
        String h2G = ExcelCellUtils.normalizeCell(dataFormatter, row2.getCell(6));
        String h2H = ExcelCellUtils.normalizeCell(dataFormatter, row2.getCell(7));
        String h2I = ExcelCellUtils.normalizeCell(dataFormatter, row2.getCell(8));

        boolean matchRow2 = HEADER_WORD_JAPANESE.equalsIgnoreCase(h2G)
                && HEADER_WORD_ROMAJI.equalsIgnoreCase(h2H)
                && HEADER_WORD_MEANING.equalsIgnoreCase(h2I);

        if (!matchRow2) {
            errors.add(new RowError(2, "Header",
                    "Hàng 2 cột G-I phải đúng: 'Tiếng Nhật', 'Romaji', 'Nghĩa tiếng Việt'"));
            return false;
        }

        return true;
    }

    /**
     * Lớp hỗ trợ tích lũy các từ cần nhớ cho từng Kanji trước khi đóng gói thành {@link KanjiRow}.
     */
    private static class KanjiRowBuilder {
        private final int rowNumber;
        private final String topic;
        private final String kanji;
        private final String hanViet;
        private final String meaningVi;
        private final String onReading;
        private final String kunReading;
        private final List<KanjiWordRow> words = new ArrayList<>();
        private boolean hasError = false;

        public KanjiRowBuilder(int rowNumber, String topic, String kanji, String hanViet,
                               String meaningVi, String onReading, String kunReading) {
            this.rowNumber = rowNumber;
            this.topic = topic;
            this.kanji = kanji;
            this.hanViet = hanViet;
            this.meaningVi = meaningVi;
            this.onReading = onReading;
            this.kunReading = kunReading;
        }

        public boolean hasError() {
            return hasError;
        }

        public void setHasError(boolean hasError) {
            this.hasError = hasError;
        }

        public void addWord(KanjiWordRow word) {
            words.add(word);
        }

        public KanjiRow build() {
            return new KanjiRow(rowNumber, topic, kanji, hanViet, meaningVi, onReading, kunReading, words);
        }
    }
}
