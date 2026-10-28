package com.nihon.service.excel;

import com.nihon.dto.excel.KanjiRow;
import com.nihon.dto.excel.KanjiWordRow;
import com.nihon.dto.excel.ParseResult;
import com.nihon.dto.excel.RowError;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bộ kiểm thử cho KanjiSheetParser:
 * - Đọc đúng dữ liệu mẫu: 道 (3 từ), 通 (0 từ)
 * - Đọc trực tiếp từ file Update.xlsx của repo
 * - Kanji trùng lặp trong file
 * - Hàng từ mồ côi (từ cần nhớ xuất hiện khi chưa có Kanji nào)
 * - Kanji có nhiều ký tự hoặc không thuộc Han script
 * - Thiếu các cột bắt buộc (Hán Việt, Ý nghĩa, On, Kun, Từ cần nhớ)
 * - Chuẩn hóa chủ đề có nhiều ký tự xuống dòng
 * - Hàng trống hoàn toàn được bỏ qua
 * - Ô chứa công thức bị từ chối
 * - Tiêu đề hàng 1 hoặc hàng 2 bị sai
 */
class KanjiSheetParserTest {

    private KanjiSheetParser parser;

    @BeforeEach
    void setUp() {
        parser = new KanjiSheetParser();
    }

    private InputStream createWorkbookStream(String sheetName, String[][] rows, CellFormulaSpec... formulaSpecs) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(sheetName);
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r);
                String[] cols = rows[r];
                if (cols != null) {
                    for (int c = 0; c < cols.length; c++) {
                        if (cols[c] != null) {
                            row.createCell(c).setCellValue(cols[c]);
                        }
                    }
                }
            }
            if (formulaSpecs != null) {
                for (CellFormulaSpec f : formulaSpecs) {
                    Row row = sheet.getRow(f.row);
                    if (row == null) {
                        row = sheet.createRow(f.row);
                    }
                    Cell cell = row.getCell(f.col);
                    if (cell == null) {
                        cell = row.createCell(f.col);
                    }
                    cell.setCellFormula(f.formula);
                }
            }
            wb.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private record CellFormulaSpec(int row, int col, String formula) {}

    @Test
    @DisplayName("Ca chuẩn dữ liệu mẫu: 道 với 3 từ cần nhớ, 通 không có từ cần nhớ")
    void testSampleDataWithMichiAndTooru() throws IOException {
        String[][] data = {
                {"Topic", "Kanji", "Hán Việt", "Ý nghĩa", "On", "Kun", "Word", null, null},
                {null, null, null, null, null, null, "Tiếng Nhật", "Romaji", "Nghĩa tiếng Việt"},
                {"Kanji A2-1\n Lesson 6・まっすぐ 行ってください", "道", "ĐẠO, ĐÁO", "Đường, đạo lý", "どう、とう", "みち", "道（みち）", "michi", "Đường"},
                {null, null, null, null, null, null, "茶道（さどう）", "sadō", "Trà đạo"},
                {null, null, null, null, null, null, "道路（どうろ）", "dōro", "Đường (đường cái, quốc lộ)"},
                {null, "通", "THÔNG", "Thông suốt", "つう、つ", "とお-ります、どお-り、かよ-います", null, null, null}
        };

        try (InputStream in = createWorkbookStream("Kanji", data)) {
            ParseResult<KanjiRow> result = parser.parse(in);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.errors()).isEmpty();
            List<KanjiRow> list = result.rows();
            assertThat(list).hasSize(2);

            // Kiểm tra Kanji 1: 道
            KanjiRow kanji1 = list.get(0);
            assertThat(kanji1.rowNumber()).isEqualTo(3);
            assertThat(kanji1.topic()).isEqualTo("Kanji A2-1\nLesson 6・まっすぐ 行ってください");
            assertThat(kanji1.kanji()).isEqualTo("道");
            assertThat(kanji1.hanViet()).isEqualTo("ĐẠO, ĐÁO");
            assertThat(kanji1.meaningVi()).isEqualTo("Đường, đạo lý");
            assertThat(kanji1.onReading()).isEqualTo("どう、とう");
            assertThat(kanji1.kunReading()).isEqualTo("みち");

            List<KanjiWordRow> words1 = kanji1.words();
            assertThat(words1).hasSize(3);
            assertThat(words1.get(0).rowNumber()).isEqualTo(3);
            assertThat(words1.get(0).japanese()).isEqualTo("道（みち）");
            assertThat(words1.get(0).romaji()).isEqualTo("michi");
            assertThat(words1.get(0).meaningVi()).isEqualTo("Đường");

            assertThat(words1.get(1).rowNumber()).isEqualTo(4);
            assertThat(words1.get(1).japanese()).isEqualTo("茶道（さどう）");
            assertThat(words1.get(1).romaji()).isEqualTo("sadō");
            assertThat(words1.get(1).meaningVi()).isEqualTo("Trà đạo");

            assertThat(words1.get(2).rowNumber()).isEqualTo(5);
            assertThat(words1.get(2).japanese()).isEqualTo("道路（どうろ）");
            assertThat(words1.get(2).romaji()).isEqualTo("dōro");
            assertThat(words1.get(2).meaningVi()).isEqualTo("Đường (đường cái, quốc lộ)");

            // Kiểm tra Kanji 2: 通 (kế thừa chủ đề, không có từ cần nhớ)
            KanjiRow kanji2 = list.get(1);
            assertThat(kanji2.rowNumber()).isEqualTo(6);
            assertThat(kanji2.topic()).isEqualTo("Kanji A2-1\nLesson 6・まっすぐ 行ってください");
            assertThat(kanji2.kanji()).isEqualTo("通");
            assertThat(kanji2.hanViet()).isEqualTo("THÔNG");
            assertThat(kanji2.meaningVi()).isEqualTo("Thông suốt");
            assertThat(kanji2.onReading()).isEqualTo("つう、つ");
            assertThat(kanji2.kunReading()).isEqualTo("とお-ります、どお-り、かよ-います");
            assertThat(kanji2.words()).isEmpty();
        }
    }

    @Test
    @DisplayName("Kiểm tra trực tiếp với file Update.xlsx thực tế trong repo")
    void testWithActualUpdateXlsxFile() throws IOException {
        Path path = Path.of("Update.xlsx");
        if (Files.exists(path)) {
            try (InputStream in = new FileInputStream(path.toFile())) {
                ParseResult<KanjiRow> result = parser.parse(in);

                assertThat(result.isSuccess()).isTrue();
                assertThat(result.errors()).isEmpty();
                List<KanjiRow> list = result.rows();
                assertThat(list).hasSize(14);
                // Kiểm tra 14 Kanji
                assertThat(list.get(0).kanji()).isEqualTo("酒");
                assertThat(list.get(1).kanji()).isEqualTo("作");
                assertThat(list.get(2).kanji()).isEqualTo("物");
                // Hàng 11: 茶 (Kun trống)
                assertThat(list.get(3).kanji()).isEqualTo("茶");
                assertThat(list.get(3).kunReading()).isNull();
                assertThat(list.get(3).words()).hasSize(2);
                assertThat(list.get(3).words().get(0).japanese()).isEqualTo("お茶（おちゃ）");
                assertThat(list.get(3).words().get(1).japanese()).isEqualTo("喫茶店（きっさてん）");

                assertThat(list.get(4).kanji()).isEqualTo("持");
                assertThat(list.get(5).kanji()).isEqualTo("願");

                // Hàng 18: 料 (Kun trống)
                assertThat(list.get(6).kanji()).isEqualTo("料");
                assertThat(list.get(6).kunReading()).isNull();
                assertThat(list.get(6).words()).hasSize(2);
                assertThat(list.get(6).words().get(0).japanese()).isEqualTo("資料（しりょう）");
                assertThat(list.get(6).words().get(1).japanese()).isEqualTo("料金（りょうきん）");

                assertThat(list.get(7).kanji()).isEqualTo("理");
                assertThat(list.get(8).kanji()).isEqualTo("味");
                assertThat(list.get(9).kanji()).isEqualTo("色");

                // Hàng 27: 野 (không có từ)
                assertThat(list.get(10).kanji()).isEqualTo("野");
                assertThat(list.get(10).words()).isEmpty();

                assertThat(list.get(11).kanji()).isEqualTo("菜");
                assertThat(list.get(12).kanji()).isEqualTo("中");
                assertThat(list.get(13).kanji()).isEqualTo("入");

                // Tổng số từ cần nhớ là 34 khớp số hàng có cột G
                int totalWords = list.stream().mapToInt(k -> k.words().size()).sum();
                assertThat(totalWords).isEqualTo(34);
            }
        }
    }

    @Test
    @DisplayName("Ca Kanji trùng: hai hàng cùng ký tự Kanji thì ghi RowError ở hàng xuất hiện sau")
    void testDuplicateKanjiRecordsErrorAtSecondOccurrence() throws IOException {
        String[][] data = {
                {"Topic", "Kanji", "Hán Việt", "Ý nghĩa", "On", "Kun", "Word", null, null},
                {null, null, null, null, null, null, "Tiếng Nhật", "Romaji", "Nghĩa tiếng Việt"},
                {"Bài 1", "日", "NHẬT", "Mặt trời", "にち", "ひ", null, null, null},
                {null, "日", "NHẬT", "Ngày", "にち", "ひ", null, null, null} // Trùng lặp tại hàng 4
        };

        try (InputStream in = createWorkbookStream("Kanji", data)) {
            ParseResult<KanjiRow> result = parser.parse(in);

            assertThat(result.hasErrors()).isTrue();
            assertThat(result.errors()).hasSize(1);
            RowError err = result.errors().get(0);
            assertThat(err.rowNumber()).isEqualTo(4);
            assertThat(err.column()).isEqualTo("Kanji");
            assertThat(err.message()).contains("bị trùng lặp");

            // Hàng 3 vẫn hợp lệ
            assertThat(result.rows()).hasSize(1);
            assertThat(result.rows().get(0).kanji()).isEqualTo("日");
        }
    }

    @Test
    @DisplayName("Ca hàng từ mồ côi: cột B trống, cột G có giá trị nhưng chưa có Kanji nào phía trên")
    void testOrphanWordRowRecordsError() throws IOException {
        String[][] data = {
                {"Topic", "Kanji", "Hán Việt", "Ý nghĩa", "On", "Kun", "Word", null, null},
                {null, null, null, null, null, null, "Tiếng Nhật", "Romaji", "Nghĩa tiếng Việt"},
                {"Bài 1", null, null, null, null, null, "猫", "neko", "Con mèo"} // Mồ côi tại hàng 3
        };

        try (InputStream in = createWorkbookStream("Kanji", data)) {
            ParseResult<KanjiRow> result = parser.parse(in);

            assertThat(result.hasErrors()).isTrue();
            assertThat(result.errors()).hasSize(1);
            RowError err = result.errors().get(0);
            assertThat(err.rowNumber()).isEqualTo(3);
            assertThat(err.column()).isEqualTo("Word");
            assertThat(err.message()).contains("chưa có Kanji nào phía trên");
            assertThat(result.rows()).isEmpty();
        }
    }

    @Test
    @DisplayName("Ca Kanji nhiều ký tự hoặc không phải Han script: ghi RowError")
    void testKanjiCharacterValidation() throws IOException {
        String[][] data = {
                {"Topic", "Kanji", "Hán Việt", "Ý nghĩa", "On", "Kun", "Word", null, null},
                {null, null, null, null, null, null, "Tiếng Nhật", "Romaji", "Nghĩa tiếng Việt"},
                {"Bài 1", "日本", "NHẬT BẢN", "Nhật Bản", "にほん", "-", null, null, null}, // 2 ký tự
                {null, "A", "A", "Chữ cái", "-", "-", null, null, null},                     // Không phải Han script
                {null, "あ", "A", "Hiragana", "-", "-", null, null, null}                     // Hiragana không phải Han
        };

        try (InputStream in = createWorkbookStream("Kanji", data)) {
            ParseResult<KanjiRow> result = parser.parse(in);

            assertThat(result.hasErrors()).isTrue();
            assertThat(result.errors()).hasSize(3);
            assertThat(result.errors().get(0).rowNumber()).isEqualTo(3);
            assertThat(result.errors().get(0).message()).contains("đúng 1 ký tự");

            assertThat(result.errors().get(1).rowNumber()).isEqualTo(4);
            assertThat(result.errors().get(1).message()).contains("chữ Hán (Han script)");

            assertThat(result.errors().get(2).rowNumber()).isEqualTo(5);
            assertThat(result.errors().get(2).message()).contains("chữ Hán (Han script)");

            assertThat(result.rows()).isEmpty();
        }
    }

    @Test
    @DisplayName("Kanji không có Kun: các hàng từ bên dưới vẫn gắn đúng vào Kanji đó, không có lỗi mồ côi")
    void testKanjiWithoutKunWordsAttachCorrectly() throws IOException {
        String[][] data = {
                {"Topic", "Kanji", "Hán Việt", "Ý nghĩa", "On", "Kun", "Word", null, null},
                {null, null, null, null, null, null, "Tiếng Nhật", "Romaji", "Nghĩa tiếng Việt"},
                {"Bài 11", "茶", "TRÀ", "Trà", "ちゃ、さ", null, null, null, null},
                {null, null, null, null, null, null, "お茶", "ocha", "Trà"},
                {null, null, null, null, null, null, "喫茶店", "kissaten", "Quán giải khát"}
        };
        try (InputStream in = createWorkbookStream("Kanji", data)) {
            ParseResult<KanjiRow> result = parser.parse(in);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.errors()).isEmpty();
            assertThat(result.rows()).hasSize(1);
            KanjiRow kanji = result.rows().get(0);
            assertThat(kanji.kanji()).isEqualTo("茶");
            assertThat(kanji.kunReading()).isNull();
            assertThat(kanji.words()).hasSize(2);
            assertThat(kanji.words().get(0).japanese()).isEqualTo("お茶");
            assertThat(kanji.words().get(1).japanese()).isEqualTo("喫茶店");
        }
    }

    @Test
    @DisplayName("Kanji không có On, không có Hán Việt, không có Ý nghĩa: hợp lệ")
    void testKanjiWithoutOnHanVietMeaningIsValid() throws IOException {
        String[][] data = {
                {"Topic", "Kanji", "Hán Việt", "Ý nghĩa", "On", "Kun", "Word", null, null},
                {null, null, null, null, null, null, "Tiếng Nhật", "Romaji", "Nghĩa tiếng Việt"},
                {"Bài 1", "木", null, null, null, "き", null, null, null}
        };
        try (InputStream in = createWorkbookStream("Kanji", data)) {
            ParseResult<KanjiRow> result = parser.parse(in);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.errors()).isEmpty();
            assertThat(result.rows()).hasSize(1);
            KanjiRow kanji = result.rows().get(0);
            assertThat(kanji.kanji()).isEqualTo("木");
            assertThat(kanji.hanViet()).isNull();
            assertThat(kanji.meaningVi()).isNull();
            assertThat(kanji.onReading()).isNull();
            assertThat(kanji.kunReading()).isEqualTo("き");
        }
    }

    @Test
    @DisplayName("Kanji không có từ cần nhớ (như 野): hợp lệ")
    void testKanjiWithoutWordsIsValid() throws IOException {
        String[][] data = {
                {"Topic", "Kanji", "Hán Việt", "Ý nghĩa", "On", "Kun", "Word", null, null},
                {null, null, null, null, null, null, "Tiếng Nhật", "Romaji", "Nghĩa tiếng Việt"},
                {"Bài 12", "野", "DÃ", "Cánh đồng", "や", null, null, null, null}
        };
        try (InputStream in = createWorkbookStream("Kanji", data)) {
            ParseResult<KanjiRow> result = parser.parse(in);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.errors()).isEmpty();
            assertThat(result.rows()).hasSize(1);
            assertThat(result.rows().get(0).kanji()).isEqualTo("野");
            assertThat(result.rows().get(0).words()).isEmpty();
        }
    }

    @Test
    @DisplayName("Từ cần nhớ thiếu Romaji hoặc thiếu Nghĩa: hợp lệ. Thiếu cột G: lỗi")
    void testWordMissingRomajiOrMeaningIsValidMissingGIsError() throws IOException {
        String[][] data = {
                {"Topic", "Kanji", "Hán Việt", "Ý nghĩa", "On", "Kun", "Word", null, null},
                {null, null, null, null, null, null, "Tiếng Nhật", "Romaji", "Nghĩa tiếng Việt"},
                {"Bài 1", "日", "NHẬT", "Mặt trời", "にち", "ひ", "日", null, "Ngày"},       // thiếu Romaji: hợp lệ
                {null, null, null, null, null, null, "日光", "nikkō", null},                 // thiếu Nghĩa: hợp lệ
                {null, null, null, null, null, null, null, "nihon", "Nhật Bản"}              // thiếu cột G: lỗi
        };
        try (InputStream in = createWorkbookStream("Kanji", data)) {
            ParseResult<KanjiRow> result = parser.parse(in);
            assertThat(result.hasErrors()).isTrue();
            assertThat(result.errors()).hasSize(1);
            assertThat(result.errors().get(0).rowNumber()).isEqualTo(5);
            assertThat(result.errors().get(0).column()).isEqualTo("Tiếng Nhật");
            assertThat(result.rows()).hasSize(1);
            assertThat(result.rows().get(0).words()).hasSize(2);
            assertThat(result.rows().get(0).words().get(0).romaji()).isNull();
            assertThat(result.rows().get(0).words().get(1).meaningVi()).isNull();
        }
    }

    @Test
    @DisplayName("Cột B nhiều ký tự: lỗi ở hàng đó và lỗi mồ côi ở hàng từ bên dưới kèm giải thích hàng nguyên nhân")
    void testInvalidKanjiColumnBCausesOrphanWithExplanation() throws IOException {
        String[][] data = {
                {"Topic", "Kanji", "Hán Việt", "Ý nghĩa", "On", "Kun", "Word", null, null},
                {null, null, null, null, null, null, "Tiếng Nhật", "Romaji", "Nghĩa tiếng Việt"},
                {"Bài 1", "日本", "NHẬT BẢN", "Nhật Bản", "にほん", "-", null, null, null}, // Hàng 3: sai Cột B
                {null, null, null, null, null, null, "日本", "nihon", "Nước Nhật"}          // Hàng 4: từ mồ côi do hàng 3
        };
        try (InputStream in = createWorkbookStream("Kanji", data)) {
            ParseResult<KanjiRow> result = parser.parse(in);
            assertThat(result.hasErrors()).isTrue();
            assertThat(result.errors()).hasSize(2);
            assertThat(result.errors().get(0).rowNumber()).isEqualTo(3);
            assertThat(result.errors().get(0).column()).isEqualTo("Kanji");
            assertThat(result.errors().get(1).rowNumber()).isEqualTo(4);
            assertThat(result.errors().get(1).column()).isEqualTo("Word");
            assertThat(result.errors().get(1).message()).contains("Kanji ở hàng 3 không hợp lệ");
            assertThat(result.rows()).isEmpty();
        }
    }

    @Test
    @DisplayName("Ca chủ đề có nhiều ký tự xuống dòng: chuẩn hóa thành một dấu xuống dòng duy nhất")
    void testTopicWithMultipleNewlinesNormalized() throws IOException {
        String rawTopic = "  Bài học Kanji A2  \r\n\r\n\r\n   Lesson 1: Bắt đầu   ";

        String[][] data = {
                {"Topic", "Kanji", "Hán Việt", "Ý nghĩa", "On", "Kun", "Word", null, null},
                {null, null, null, null, null, null, "Tiếng Nhật", "Romaji", "Nghĩa tiếng Việt"},
                {rawTopic, "月", "NGUYỆT", "Mặt trăng", "げつ", "つき", null, null, null}
        };

        try (InputStream in = createWorkbookStream("Kanji", data)) {
            ParseResult<KanjiRow> result = parser.parse(in);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.rows()).hasSize(1);
            assertThat(result.rows().get(0).topic()).isEqualTo("Bài học Kanji A2\nLesson 1: Bắt đầu");
        }
    }

    @Test
    @DisplayName("Ca hàng trống hoàn toàn: tự động bỏ qua mà không sinh lỗi")
    void testEmptyRowsAreSkipped() throws IOException {
        String[][] data = {
                {"Topic", "Kanji", "Hán Việt", "Ý nghĩa", "On", "Kun", "Word", null, null},
                {null, null, null, null, null, null, "Tiếng Nhật", "Romaji", "Nghĩa tiếng Việt"},
                {"Bài 1", "山", "SƠN", "Núi", "さん", "やま", null, null, null},
                {null, null, null, null, null, null, null, null, null}, // Hàng trống
                null,                                                   // Hàng null
                {null, "川", "XUYÊN", "Sông", "せん", "かわ", null, null, null}
        };

        try (InputStream in = createWorkbookStream("Kanji", data)) {
            ParseResult<KanjiRow> result = parser.parse(in);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.rows()).hasSize(2);
            assertThat(result.rows().get(0).kanji()).isEqualTo("山");
            assertThat(result.rows().get(1).kanji()).isEqualTo("川");
            assertThat(result.rows().get(1).topic()).isEqualTo("Bài 1"); // Kế thừa qua hàng trống
        }
    }

    @Test
    @DisplayName("Ca ô có công thức: bị từ chối và ghi RowError")
    void testFormulaCellIsRejected() throws IOException {
        String[][] data = {
                {"Topic", "Kanji", "Hán Việt", "Ý nghĩa", "On", "Kun", "Word", null, null},
                {null, null, null, null, null, null, "Tiếng Nhật", "Romaji", "Nghĩa tiếng Việt"},
                {"Bài 1", "山", "SƠN", "Núi", "さん", "やま", null, null, null}
        };

        // Gắn công thức vào cột Kanji (hàng index 2, cột index 1)
        CellFormulaSpec formula = new CellFormulaSpec(2, 1, "\"A\" & \"B\"");

        try (InputStream in = createWorkbookStream("Kanji", data, formula)) {
            ParseResult<KanjiRow> result = parser.parse(in);

            assertThat(result.hasErrors()).isTrue();
            assertThat(result.errors()).hasSize(1);
            assertThat(result.errors().get(0).rowNumber()).isEqualTo(3);
            assertThat(result.errors().get(0).column()).isEqualTo("Kanji");
            assertThat(result.errors().get(0).message()).contains("công thức không được phép");
            assertThat(result.rows()).isEmpty();
        }
    }

    @Test
    @DisplayName("Ca tiêu đề hàng 1 hoặc hàng 2 sai: dừng và ghi RowError ngay tại tiêu đề")
    void testInvalidHeadersStopProcessing() throws IOException {
        // Hàng 1 sai cột Kanji
        String[][] wrongHeader1 = {
                {"Topic", "WrongColumn", "Hán Việt", "Ý nghĩa", "On", "Kun", "Word", null, null},
                {null, null, null, null, null, null, "Tiếng Nhật", "Romaji", "Nghĩa tiếng Việt"}
        };
        try (InputStream in = createWorkbookStream("Kanji", wrongHeader1)) {
            ParseResult<KanjiRow> result = parser.parse(in);
            assertThat(result.hasErrors()).isTrue();
            assertThat(result.errors().get(0).rowNumber()).isEqualTo(1);
            assertThat(result.errors().get(0).column()).isEqualTo("Header");
        }

        // Hàng 2 sai cột Tiếng Nhật
        String[][] wrongHeader2 = {
                {"Topic", "Kanji", "Hán Việt", "Ý nghĩa", "On", "Kun", "Word", null, null},
                {null, null, null, null, null, null, "WrongWord", "Romaji", "Nghĩa tiếng Việt"}
        };
        try (InputStream in = createWorkbookStream("Kanji", wrongHeader2)) {
            ParseResult<KanjiRow> result = parser.parse(in);
            assertThat(result.hasErrors()).isTrue();
            assertThat(result.errors().get(0).rowNumber()).isEqualTo(2);
            assertThat(result.errors().get(0).column()).isEqualTo("Header");
        }
    }

    @Test
    @DisplayName("Tìm sheet 'Kanji' không phân biệt hoa thường và báo lỗi khi thiếu sheet")
    void testCaseInsensitiveSheetAndMissingSheet() throws IOException {
        String[][] data = {
                {"Topic", "Kanji", "Hán Việt", "Ý nghĩa", "On", "Kun", "Word", null, null},
                {null, null, null, null, null, null, "Tiếng Nhật", "Romaji", "Nghĩa tiếng Việt"},
                {"Bài 1", "山", "SƠN", "Núi", "さん", "やま", null, null, null}
        };

        // Tìm thành công với chữ thường
        try (InputStream in = createWorkbookStream("kanji", data)) {
            ParseResult<KanjiRow> result = parser.parse(in);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.rows()).hasSize(1);
        }

        // Tìm thành công với chữ hoa
        try (InputStream in = createWorkbookStream("KANJI", data)) {
            ParseResult<KanjiRow> result = parser.parse(in);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.rows()).hasSize(1);
        }

        // Thiếu sheet Kanji
        try (InputStream in = createWorkbookStream("OtherSheet", data)) {
            ParseResult<KanjiRow> result = parser.parse(in);
            assertThat(result.hasErrors()).isTrue();
            assertThat(result.errors().get(0).column()).isEqualTo("Sheet");
        }
    }
}
