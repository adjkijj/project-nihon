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

                String valA = formulaA ? "" : ExcelCellUtils.normalizeTopic(dataFormatter, cellA);
                String valB = formulaB ? "" : ExcelCellUtils.normalizeCell(dataFormatter, cellB);
                String valC = formulaC ? "" : ExcelCellUtils.normalizeCell(dataFormatter, cellC);
                String valD = formulaD ? "" : ExcelCellUtils.normalizeCell(dataFormatter, cellD);
                String valE = formulaE ? "" : ExcelCellUtils.normalizeCell(dataFormatter, cellE);
                String valF = formulaF ? "" : ExcelCellUtils.normalizeCell(dataFormatter, cellF);
                String valG = formulaG ? "" : ExcelCellUtils.normalizeCell(dataFormatter, cellG);
                String valH = formulaH ? "" : ExcelCellUtils.normalizeCell(dataFormatter, cellH);
                String valI = formulaI ? "" : ExcelCellUtils.normalizeCell(dataFormatter, cellI);

                // Quy tắc 3h: Hàng trống hoàn toàn thì bỏ qua
                boolean hasAnyFormula = formulaA || formulaB || formulaC || formulaD || formulaE
                        || formulaF || formulaG || formulaH || formulaI;
                if (!hasAnyFormula && valA.isEmpty() && valB.isEmpty() && valC.isEmpty() && valD.isEmpty()
                        && valE.isEmpty() && valF.isEmpty() && valG.isEmpty() && valH.isEmpty() && valI.isEmpty()) {
                    continue;
                }

                // Quy tắc 5: Ô có công thức bị từ chối
                if (formulaA) errors.add(new RowError(rowNumber, HEADER_TOPIC, "Ô chứa công thức không được phép"));
                if (formulaB) errors.add(new RowError(rowNumber, HEADER_KANJI, "Ô chứa công thức không được phép"));
                if (formulaC) errors.add(new RowError(rowNumber, HEADER_HAN_VIET, "Ô chứa công thức không được phép"));
                if (formulaD) errors.add(new RowError(rowNumber, HEADER_MEANING, "Ô chứa công thức không được phép"));
                if (formulaE) errors.add(new RowError(rowNumber, HEADER_ON, "Ô chứa công thức không được phép"));
                if (formulaF) errors.add(new RowError(rowNumber, HEADER_KUN, "Ô chứa công thức không được phép"));
                if (formulaG) errors.add(new RowError(rowNumber, HEADER_WORD_JAPANESE, "Ô chứa công thức không được phép"));
                if (formulaH) errors.add(new RowError(rowNumber, HEADER_WORD_ROMAJI, "Ô chứa công thức không được phép"));
                if (formulaI) errors.add(new RowError(rowNumber, HEADER_WORD_MEANING, "Ô chứa công thức không được phép"));

                // Kiểm soát PCI-DSS: Tuyệt đối không nhập số thẻ thanh toán (PAN) vào hệ thống
                // Không ghi giá trị số thẻ vào log hay thông điệp lỗi
                boolean hasCardData = false;
                if (ExcelCellUtils.containsPotentialPaymentCard(valA)) {
                    errors.add(new RowError(rowNumber, HEADER_TOPIC, "Dữ liệu giống số thẻ thanh toán, không được nhập"));
                    hasCardData = true;
                }
                if (ExcelCellUtils.containsPotentialPaymentCard(valB)) {
                    errors.add(new RowError(rowNumber, HEADER_KANJI, "Dữ liệu giống số thẻ thanh toán, không được nhập"));
                    hasCardData = true;
                }
                if (ExcelCellUtils.containsPotentialPaymentCard(valC)) {
                    errors.add(new RowError(rowNumber, HEADER_HAN_VIET, "Dữ liệu giống số thẻ thanh toán, không được nhập"));
                    hasCardData = true;
                }
                if (ExcelCellUtils.containsPotentialPaymentCard(valD)) {
                    errors.add(new RowError(rowNumber, HEADER_MEANING, "Dữ liệu giống số thẻ thanh toán, không được nhập"));
                    hasCardData = true;
                }
                if (ExcelCellUtils.containsPotentialPaymentCard(valE)) {
                    errors.add(new RowError(rowNumber, HEADER_ON, "Dữ liệu giống số thẻ thanh toán, không được nhập"));
                    hasCardData = true;
                }
                if (ExcelCellUtils.containsPotentialPaymentCard(valF)) {
                    errors.add(new RowError(rowNumber, HEADER_KUN, "Dữ liệu giống số thẻ thanh toán, không được nhập"));
                    hasCardData = true;
                }
                if (ExcelCellUtils.containsPotentialPaymentCard(valG)) {
                    errors.add(new RowError(rowNumber, HEADER_WORD_JAPANESE, "Dữ liệu giống số thẻ thanh toán, không được nhập"));
                    hasCardData = true;
                }
                if (ExcelCellUtils.containsPotentialPaymentCard(valH)) {
                    errors.add(new RowError(rowNumber, HEADER_WORD_ROMAJI, "Dữ liệu giống số thẻ thanh toán, không được nhập"));
                    hasCardData = true;
                }
                if (ExcelCellUtils.containsPotentialPaymentCard(valI)) {
                    errors.add(new RowError(rowNumber, HEADER_WORD_MEANING, "Dữ liệu giống số thẻ thanh toán, không được nhập"));
                    hasCardData = true;
                }

                // Quy tắc 3g: Xử lý chủ đề
                boolean topicHasError = false;
                if (!valA.isEmpty()) {
                    currentTopic = valA;
                    if (currentTopic.length() > MAX_TOPIC_LENGTH) {
                        errors.add(new RowError(rowNumber, HEADER_TOPIC,
                                "Chủ đề vượt quá độ dài tối đa " + MAX_TOPIC_LENGTH + " ký tự (hiện tại: " + currentTopic.length() + ")"));
                        topicHasError = true;
                    }
                } else if (!formulaA) {
                    if (currentTopic == null || currentTopic.isEmpty()) {
                        errors.add(new RowError(rowNumber, HEADER_TOPIC, "Hàng có dữ liệu nhưng chưa có chủ đề (chủ đề trống)"));
                        topicHasError = true;
                    }
                }

                // Kiểm tra xem hàng có định nghĩa Kanji mới hay không (cột B có giá trị)
                boolean hasKanjiValue = !valB.isEmpty() || formulaB;
                boolean hasWordValue = !valG.isEmpty() || !valH.isEmpty() || !valI.isEmpty()
                        || formulaG || formulaH || formulaI;

                if (hasKanjiValue) {
                    // Quy tắc 3d: Tạo Kanji mới
                    boolean kanjiError = formulaB || formulaC || formulaD || formulaE || formulaF || topicHasError || hasCardData;

                    // Kiểm tra đúng 1 ký tự Kanji thuộc Han script theo code point
                    if (!formulaB) {
                        int codePointCount = valB.codePointCount(0, valB.length());
                        if (codePointCount != 1) {
                            errors.add(new RowError(rowNumber, HEADER_KANJI,
                                    "Cột Kanji phải là đúng 1 ký tự (hiện tại có " + codePointCount + " ký tự: '" + valB + "')"));
                            kanjiError = true;
                        } else {
                            int codePoint = valB.codePointAt(0);
                            if (Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.HAN) {
                                errors.add(new RowError(rowNumber, HEADER_KANJI,
                                        "Cột Kanji phải là ký tự chữ Hán (Han script), hiện tại: '" + valB + "'"));
                                kanjiError = true;
                            }
                        }

                        // Quy tắc 3j: Trùng Kanji trong file
                        if (!kanjiError) {
                            if (!seenKanjiInFile.add(valB)) {
                                errors.add(new RowError(rowNumber, HEADER_KANJI,
                                        "Ký tự Kanji '" + valB + "' bị trùng lặp trong file (đã xuất hiện ở hàng trước)"));
                                kanjiError = true;
                            }
                        }
                    }

                    // Kiểm tra các cột bắt buộc C, D, E, F
                    if (!formulaC) {
                        if (valC.isEmpty()) {
                            errors.add(new RowError(rowNumber, HEADER_HAN_VIET, "Thiếu dữ liệu cột Hán Việt"));
                            kanjiError = true;
                        } else if (valC.length() > MAX_HAN_VIET_LENGTH) {
                            errors.add(new RowError(rowNumber, HEADER_HAN_VIET, "Hán Việt vượt quá " + MAX_HAN_VIET_LENGTH + " ký tự"));
                            kanjiError = true;
                        }
                    }
                    if (!formulaD) {
                        if (valD.isEmpty()) {
                            errors.add(new RowError(rowNumber, HEADER_MEANING, "Thiếu dữ liệu cột Ý nghĩa"));
                            kanjiError = true;
                        } else if (valD.length() > MAX_MEANING_LENGTH) {
                            errors.add(new RowError(rowNumber, HEADER_MEANING, "Ý nghĩa vượt quá " + MAX_MEANING_LENGTH + " ký tự"));
                            kanjiError = true;
                        }
                    }
                    if (!formulaE) {
                        if (valE.isEmpty()) {
                            errors.add(new RowError(rowNumber, HEADER_ON, "Thiếu dữ liệu cột On"));
                            kanjiError = true;
                        } else if (valE.length() > MAX_ON_KUN_LENGTH) {
                            errors.add(new RowError(rowNumber, HEADER_ON, "Âm On vượt quá " + MAX_ON_KUN_LENGTH + " ký tự"));
                            kanjiError = true;
                        }
                    }
                    if (!formulaF) {
                        if (valF.isEmpty()) {
                            errors.add(new RowError(rowNumber, HEADER_KUN, "Thiếu dữ liệu cột Kun"));
                            kanjiError = true;
                        } else if (valF.length() > MAX_ON_KUN_LENGTH) {
                            errors.add(new RowError(rowNumber, HEADER_KUN, "Âm Kun vượt quá " + MAX_ON_KUN_LENGTH + " ký tự"));
                            kanjiError = true;
                        }
                    }

                    // Đóng gói Kanji trước đó nếu hợp lệ
                    if (currentKanjiBuilder != null) {
                        validKanjiList.add(currentKanjiBuilder.build());
                        currentKanjiBuilder = null;
                    }

                    if (!kanjiError && currentTopic != null && !currentTopic.isEmpty()) {
                        currentKanjiBuilder = new KanjiRowBuilder(rowNumber, currentTopic, valB, valC, valD, valE, valF);
                    }

                    // Nếu hàng này có kèm từ cần nhớ đầu tiên (cột G hoặc H/I có giá trị)
                    if (hasWordValue) {
                        validateAndAddWord(rowNumber, valG, valH, valI, formulaG, formulaH, formulaI, currentKanjiBuilder, errors);
                    }

                } else if (hasWordValue) {
                    // Quy tắc 3e: Hàng có cột B trống và có từ cần nhớ
                    if (currentKanjiBuilder == null) {
                        errors.add(new RowError(rowNumber, "Word", "Hàng chứa từ cần nhớ nhưng chưa có Kanji nào phía trên (hàng mồ côi)"));
                    }
                    validateAndAddWord(rowNumber, valG, valH, valI, formulaG, formulaH, formulaI, currentKanjiBuilder, errors);
                } else if (!valC.isEmpty() || !valD.isEmpty() || !valE.isEmpty() || !valF.isEmpty()) {
                    errors.add(new RowError(rowNumber, HEADER_KANJI, "Hàng có thông tin Hán Việt/âm đọc nhưng thiếu ký tự Kanji"));
                }
            }

            // Đóng gói Kanji cuối cùng nếu có
            if (currentKanjiBuilder != null) {
                validKanjiList.add(currentKanjiBuilder.build());
            }

        } catch (IOException | RuntimeException e) {
            throw new ExcelParseException("Không thể đọc file Excel định dạng .xlsx: " + e.getMessage(), e);
        }

        return new ParseResult<>(validKanjiList, errors);
    }

    /**
     * Xác thực và thêm từ cần nhớ vào Kanji hiện tại nếu hợp lệ.
     */
    private void validateAndAddWord(int rowNumber, String valG, String valH, String valI,
                                    boolean formulaG, boolean formulaH, boolean formulaI,
                                    KanjiRowBuilder currentKanji, List<RowError> errors) {
        boolean wordError = formulaG || formulaH || formulaI
                || ExcelCellUtils.containsPotentialPaymentCard(valG)
                || ExcelCellUtils.containsPotentialPaymentCard(valH)
                || ExcelCellUtils.containsPotentialPaymentCard(valI);

        // Quy tắc 3f: Từ cần nhớ phải có đủ G, H, I
        if (!formulaG) {
            if (valG.isEmpty()) {
                errors.add(new RowError(rowNumber, HEADER_WORD_JAPANESE, "Từ cần nhớ thiếu cột Tiếng Nhật"));
                wordError = true;
            } else if (valG.length() > MAX_WORD_JAPANESE_LENGTH) {
                errors.add(new RowError(rowNumber, HEADER_WORD_JAPANESE,
                        "Từ tiếng Nhật vượt quá độ dài tối đa " + MAX_WORD_JAPANESE_LENGTH + " ký tự"));
                wordError = true;
            }
        }

        if (!formulaH) {
            if (valH.isEmpty()) {
                errors.add(new RowError(rowNumber, HEADER_WORD_ROMAJI, "Từ cần nhớ thiếu cột Romaji"));
                wordError = true;
            } else if (valH.length() > MAX_WORD_ROMAJI_LENGTH) {
                errors.add(new RowError(rowNumber, HEADER_WORD_ROMAJI,
                        "Romaji vượt quá độ dài tối đa " + MAX_WORD_ROMAJI_LENGTH + " ký tự"));
                wordError = true;
            }
        }

        if (!formulaI) {
            if (valI.isEmpty()) {
                errors.add(new RowError(rowNumber, HEADER_WORD_MEANING, "Từ cần nhớ thiếu cột Nghĩa tiếng Việt"));
                wordError = true;
            } else if (valI.length() > MAX_WORD_MEANING_LENGTH) {
                errors.add(new RowError(rowNumber, HEADER_WORD_MEANING,
                        "Nghĩa tiếng Việt vượt quá độ dài tối đa " + MAX_WORD_MEANING_LENGTH + " ký tự"));
                wordError = true;
            }
        }

        if (!wordError && currentKanji != null) {
            currentKanji.addWord(new KanjiWordRow(rowNumber, valG, valH, valI));
        }
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

        public void addWord(KanjiWordRow word) {
            words.add(word);
        }

        public KanjiRow build() {
            return new KanjiRow(rowNumber, topic, kanji, hanViet, meaningVi, onReading, kunReading, words);
        }
    }
}
