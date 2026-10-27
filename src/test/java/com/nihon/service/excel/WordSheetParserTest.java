package com.nihon.service.excel;

import com.nihon.dto.excel.ParseResult;
import com.nihon.dto.excel.RowError;
import com.nihon.dto.excel.WordRow;
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
import java.text.Normalizer;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bộ kiểm thử toàn diện cho WordSheetParser:
 * - Đọc ký tự tiếng Nhật và tiếng Việt có dấu
 * - Kế thừa chủ đề và hai chủ đề liên tiếp
 * - Bỏ qua hàng trống
 * - Hàng thiếu cột (B, C, D) ghi lỗi nhưng không dừng toàn bộ
 * - Hàng đầu tiên có dữ liệu mà không có chủ đề
 * - Tiêu đề hàng 1 sai thì dừng và báo lỗi
 * - Ô có công thức bị từ chối và ghi RowError
 * - Vượt quá độ dài tối đa
 * - Tìm sheet "New Word" không phân biệt hoa thường
 * - Kiểm thử với file Update.xlsx thực tế
 */
class WordSheetParserTest {

    private WordSheetParser parser;

    @BeforeEach
    void setUp() {
        parser = new WordSheetParser();
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
    @DisplayName("Ca chuẩn: tiếng Nhật, tiếng Việt có dấu, hai chủ đề liên tiếp và kế thừa chủ đề")
    void testStandardFlowWithTwoConsecutiveTopicsAndUnicode() throws IOException {
        String[][] data = {
                {"Topic", "Tiếng Nhật", "Romaji", "Nghĩa tiếng Việt"},
                {"Chào hỏi", "こんにちは", "konnichiwa", "Xin chào (buổi trưa)"},
                {"", "ありがとう", "arigatou", "Cảm ơn rất nhiều"},
                {"Gia đình", "家族（かぞく）", "kazoku", "Gia đình thân yêu"},
                {"", "父（ちち）", "chichi", "Bố (của tôi)"}
        };

        try (InputStream in = createWorkbookStream("New Word", data)) {
            ParseResult<WordRow> result = parser.parse(in);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.errors()).isEmpty();
            List<WordRow> rows = result.rows();
            assertThat(rows).hasSize(4);

            // Kiểm tra hàng 2
            assertThat(rows.get(0).rowNumber()).isEqualTo(2);
            assertThat(rows.get(0).topic()).isEqualTo("Chào hỏi");
            assertThat(rows.get(0).japanese()).isEqualTo("こんにちは");
            assertThat(rows.get(0).romaji()).isEqualTo("konnichiwa");
            assertThat(rows.get(0).meaningVi()).isEqualTo("Xin chào (buổi trưa)");

            // Kiểm tra hàng 3 (kế thừa chủ đề từ hàng 2)
            assertThat(rows.get(1).rowNumber()).isEqualTo(3);
            assertThat(rows.get(1).topic()).isEqualTo("Chào hỏi");
            assertThat(rows.get(1).japanese()).isEqualTo("ありがとう");

            // Kiểm tra hàng 4 (chủ đề mới 'Gia đình')
            assertThat(rows.get(2).rowNumber()).isEqualTo(4);
            assertThat(rows.get(2).topic()).isEqualTo("Gia đình");
            assertThat(rows.get(2).japanese()).isEqualTo("家族（かぞく）");

            // Kiểm tra hàng 5 (kế thừa chủ đề 'Gia đình')
            assertThat(rows.get(3).rowNumber()).isEqualTo(5);
            assertThat(rows.get(3).topic()).isEqualTo("Gia đình");
            assertThat(rows.get(3).japanese()).isEqualTo("父（ちち）");
        }
    }

    @Test
    @DisplayName("Ca hàng trống: bỏ qua hàng trống và kế thừa đúng chủ đề qua các hàng trống")
    void testEmptyRowsAreSkipped() throws IOException {
        String[][] data = {
                {"Topic", "Tiếng Nhật", "Romaji", "Nghĩa tiếng Việt"},
                {"Chào hỏi", "おはよう", "ohayou", "Chào buổi sáng"},
                {"", "", "", ""}, // Hàng trống có cell rỗng
                null,              // Hàng null
                {"", "さようなら", "sayounara", "Tạm biệt"}
        };

        try (InputStream in = createWorkbookStream("New Word", data)) {
            ParseResult<WordRow> result = parser.parse(in);

            assertThat(result.isSuccess()).isTrue();
            List<WordRow> rows = result.rows();
            assertThat(rows).hasSize(2);

            assertThat(rows.get(0).rowNumber()).isEqualTo(2);
            assertThat(rows.get(0).japanese()).isEqualTo("おはよう");

            assertThat(rows.get(1).rowNumber()).isEqualTo(5);
            assertThat(rows.get(1).topic()).isEqualTo("Chào hỏi"); // Kế thừa qua các hàng trống
            assertThat(rows.get(1).japanese()).isEqualTo("さようなら");
        }
    }

    @Test
    @DisplayName("Ca thiếu cột (B, C, D): ghi RowError nhưng không dừng toàn bộ")
    void testMissingColumnsRecordsErrorAndDoesNotStop() throws IOException {
        String[][] data = {
                {"Topic", "Tiếng Nhật", "Romaji", "Nghĩa tiếng Việt"},
                {"Chủ đề 1", "", "tesuto", "Kiểm tra"},            // Thiếu cột B (Tiếng Nhật)
                {"", "本", "", "Sách"},                             // Thiếu cột C (Romaji)
                {"", "車", "kuruma", ""},                           // Thiếu cột D (Nghĩa tiếng Việt)
                {"", "猫", "neko", "Con mèo"}                       // Hàng hợp lệ
        };

        try (InputStream in = createWorkbookStream("New Word", data)) {
            ParseResult<WordRow> result = parser.parse(in);

            assertThat(result.hasErrors()).isTrue();
            assertThat(result.errors()).hasSize(3);

            assertThat(result.errors().get(0).rowNumber()).isEqualTo(2);
            assertThat(result.errors().get(0).column()).isEqualTo("Tiếng Nhật");

            assertThat(result.errors().get(1).rowNumber()).isEqualTo(3);
            assertThat(result.errors().get(1).column()).isEqualTo("Romaji");

            assertThat(result.errors().get(2).rowNumber()).isEqualTo(4);
            assertThat(result.errors().get(2).column()).isEqualTo("Nghĩa tiếng Việt");

            // Hàng 5 vẫn được phân tích thành công
            assertThat(result.rows()).hasSize(1);
            assertThat(result.rows().get(0).rowNumber()).isEqualTo(5);
            assertThat(result.rows().get(0).topic()).isEqualTo("Chủ đề 1");
            assertThat(result.rows().get(0).japanese()).isEqualTo("猫");
        }
    }

    @Test
    @DisplayName("Ca hàng đầu tiên có dữ liệu mà không có chủ đề: ghi RowError")
    void testFirstDataRowMissingTopicRecordsError() throws IOException {
        String[][] data = {
                {"Topic", "Tiếng Nhật", "Romaji", "Nghĩa tiếng Việt"},
                {"", "犬", "inu", "Con chó"},                     // Hàng đầu không có topic
                {"Động vật", "猫", "neko", "Con mèo"}              // Hàng sau có topic
        };

        try (InputStream in = createWorkbookStream("New Word", data)) {
            ParseResult<WordRow> result = parser.parse(in);

            assertThat(result.hasErrors()).isTrue();
            assertThat(result.errors()).hasSize(1);
            assertThat(result.errors().get(0).rowNumber()).isEqualTo(2);
            assertThat(result.errors().get(0).column()).isEqualTo("Topic");

            assertThat(result.rows()).hasSize(1);
            assertThat(result.rows().get(0).rowNumber()).isEqualTo(3);
            assertThat(result.rows().get(0).topic()).isEqualTo("Động vật");
        }
    }

    @Test
    @DisplayName("Ca tiêu đề sai: dừng và báo lỗi ngay tại hàng 1, không đọc dữ liệu tiếp")
    void testInvalidHeaderStopsProcessing() throws IOException {
        String[][] data = {
                {"Chủ đề", "Tiếng Nhật", "Romaji", "Nghĩa tiếng Việt"}, // Sai cột 0
                {"Bài 1", "私", "watashi", "Tôi"}
        };

        try (InputStream in = createWorkbookStream("New Word", data)) {
            ParseResult<WordRow> result = parser.parse(in);

            assertThat(result.hasErrors()).isTrue();
            assertThat(result.rows()).isEmpty();
            assertThat(result.errors()).hasSize(1);
            assertThat(result.errors().get(0).rowNumber()).isEqualTo(1);
            assertThat(result.errors().get(0).column()).isEqualTo("Header");
        }
    }

    @Test
    @DisplayName("Ca ô có công thức: bị từ chối và ghi RowError, không thực thi công thức")
    void testFormulaCellIsRejected() throws IOException {
        String[][] data = {
                {"Topic", "Tiếng Nhật", "Romaji", "Nghĩa tiếng Việt"},
                {"Bài 1", "私", "watashi", "Tôi"}
        };

        // Gắn công thức vào ô (hàng index 1, cột index 1 - "Tiếng Nhật")
        CellFormulaSpec formula = new CellFormulaSpec(1, 1, "CONCATENATE(\"A\", \"B\")");

        try (InputStream in = createWorkbookStream("New Word", data, formula)) {
            ParseResult<WordRow> result = parser.parse(in);

            assertThat(result.hasErrors()).isTrue();
            assertThat(result.rows()).isEmpty();
            assertThat(result.errors()).hasSize(1);
            assertThat(result.errors().get(0).rowNumber()).isEqualTo(2);
            assertThat(result.errors().get(0).column()).isEqualTo("Tiếng Nhật");
            assertThat(result.errors().get(0).message()).contains("công thức không được phép");
        }
    }

    @Test
    @DisplayName("Ca giới hạn độ dài: vượt quá ký tự tối đa thì ghi RowError")
    void testFieldLengthExceeded() throws IOException {
        String longTopic = "T".repeat(256);
        String longJapanese = "J".repeat(256);
        String longRomaji = "R".repeat(256);
        String longMeaning = "M".repeat(501);

        String[][] data = {
                {"Topic", "Tiếng Nhật", "Romaji", "Nghĩa tiếng Việt"},
                {longTopic, "私", "watashi", "Tôi"},
                {"Bài 1", longJapanese, "watashi", "Tôi"},
                {"Bài 1", "私", longRomaji, "Tôi"},
                {"Bài 1", "私", "watashi", longMeaning}
        };

        try (InputStream in = createWorkbookStream("New Word", data)) {
            ParseResult<WordRow> result = parser.parse(in);

            assertThat(result.hasErrors()).isTrue();
            assertThat(result.rows()).isEmpty();
            assertThat(result.errors()).hasSize(4);
            assertThat(result.errors().get(0).column()).isEqualTo("Topic");
            assertThat(result.errors().get(1).column()).isEqualTo("Tiếng Nhật");
            assertThat(result.errors().get(2).column()).isEqualTo("Romaji");
            assertThat(result.errors().get(3).column()).isEqualTo("Nghĩa tiếng Việt");
        }
    }

    @Test
    @DisplayName("Chuẩn hóa văn bản: trim, nén nhiều khoảng trắng và chuẩn hóa Unicode NFC")
    void testTextNormalization() throws IOException {
        // Chuỗi NFD của từ "Tiếng Việt" kèm khoảng trắng thừa
        String decomposed = Normalizer.normalize("Tiếng", Normalizer.Form.NFD) + "   "
                + Normalizer.normalize("Việt", Normalizer.Form.NFD);

        String[][] data = {
                {"Topic", "Tiếng Nhật", "Romaji", "Nghĩa tiếng Việt"},
                {"  Chủ đề    1  ", "  私（わたし）  ", "  watashi   desu  ", decomposed}
        };

        try (InputStream in = createWorkbookStream("New Word", data)) {
            ParseResult<WordRow> result = parser.parse(in);

            assertThat(result.isSuccess()).isTrue();
            WordRow row = result.rows().get(0);
            assertThat(row.topic()).isEqualTo("Chủ đề 1");
            assertThat(row.japanese()).isEqualTo("私（わたし）");
            assertThat(row.romaji()).isEqualTo("watashi desu");
            // Đảm bảo là dạng chuẩn hóa NFC
            assertThat(Normalizer.isNormalized(row.meaningVi(), Normalizer.Form.NFC)).isTrue();
            assertThat(row.meaningVi()).isEqualTo("Tiếng Việt");
        }
    }

    @Test
    @DisplayName("Tìm sheet 'New Word' không phân biệt hoa thường")
    void testCaseInsensitiveSheetName() throws IOException {
        String[][] data = {
                {"Topic", "Tiếng Nhật", "Romaji", "Nghĩa tiếng Việt"},
                {"Bài 1", "私", "watashi", "Tôi"}
        };

        try (InputStream in = createWorkbookStream("new word", data)) {
            ParseResult<WordRow> result = parser.parse(in);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.rows()).hasSize(1);
        }

        try (InputStream in = createWorkbookStream("NEW WORD", data)) {
            ParseResult<WordRow> result = parser.parse(in);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.rows()).hasSize(1);
        }
    }

    @Test
    @DisplayName("Sheet 'New Word' không tồn tại thì ghi lỗi rõ ràng")
    void testMissingSheetName() throws IOException {
        String[][] data = {
                {"Topic", "Tiếng Nhật", "Romaji", "Nghĩa tiếng Việt"}
        };

        try (InputStream in = createWorkbookStream("VocabularySheet", data)) {
            ParseResult<WordRow> result = parser.parse(in);
            assertThat(result.hasErrors()).isTrue();
            assertThat(result.errors()).hasSize(1);
            assertThat(result.errors().get(0).column()).isEqualTo("Sheet");
            assertThat(result.errors().get(0).message()).contains("Không tìm thấy sheet 'New Word'");
        }
    }

    @Test
    @DisplayName("Kiểm tra với file Update.xlsx có sẵn trong repo")
    void testWithActualUpdateXlsxFile() throws IOException {
        Path path = Path.of("Update.xlsx");
        if (Files.exists(path)) {
            try (InputStream in = new FileInputStream(path.toFile())) {
                ParseResult<WordRow> result = parser.parse(in);
                // Sheet "New Word" trong Update.xlsx có 2 chủ đề, mỗi chủ đề 3 từ (tổng 6 từ)
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.rows()).hasSize(6);
            }
        }
    }

    @Test
    @DisplayName("File không đúng định dạng .xlsx hoặc InputStream null phải báo lỗi rõ nghĩa")
    void testInvalidFileOrNullInputStream() {
        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(IllegalArgumentException.class);

        byte[] invalidData = "Đây không phải file Excel zip OOXML".getBytes();
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(invalidData)))
                .isInstanceOf(ExcelParseException.class);
    }
}
