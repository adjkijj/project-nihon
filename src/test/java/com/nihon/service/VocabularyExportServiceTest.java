package com.nihon.service;

import com.nihon.dto.ExportTextFormat;
import com.nihon.dto.VocabularyRowDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VocabularyExportServiceTest {

    private VocabularyExportService exportService;

    @BeforeEach
    void setUp() {
        exportService = new VocabularyExportService();
    }

    @Test
    @DisplayName("6. Tệp xuất phải có tiền tố UTF-8 BOM (0xEF, 0xBB, 0xBF)")
    void exportToText_containsUtf8Bom() {
        List<VocabularyRowDto> rows = List.of(
                VocabularyRowDto.builder()
                        .japaneseText("本")
                        .reading("ほん")
                        .meaningVi("Sách")
                        .build()
        );

        byte[] bytes = exportService.exportToText(rows, ExportTextFormat.TAB);

        assertThat(bytes.length).isGreaterThanOrEqualTo(3);
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
    }

    @Test
    @DisplayName("1. Xuất theo định dạng TAB: Tiếng Nhật \\t Cách đọc \\t Nghĩa")
    void exportToText_tabFormat() {
        List<VocabularyRowDto> rows = List.of(
                VocabularyRowDto.builder()
                        .japaneseText("私")
                        .reading("わたし")
                        .meaningVi("Tôi")
                        .build(),
                VocabularyRowDto.builder()
                        .japaneseText("猫")
                        .reading("ねこ")
                        .meaningVi("Con mèo")
                        .build()
        );

        byte[] bytes = exportService.exportToText(rows, ExportTextFormat.TAB);
        String content = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);

        String[] lines = content.split("\r\n");
        assertThat(lines).hasSize(2);
        assertThat(lines[0]).isEqualTo("私\tわたし\tTôi");
        assertThat(lines[1]).isEqualTo("猫\tねこ\tCon mèo");
    }

    @Test
    @DisplayName("Định dạng COLON: 'tiếng Nhật (cách đọc) : nghĩa' và không hiện () nếu thiếu cách đọc")
    void exportToText_colonFormat() {
        List<VocabularyRowDto> rows = List.of(
                VocabularyRowDto.builder()
                        .japaneseText("食べる")
                        .reading("たべる")
                        .meaningVi("Ăn")
                        .build(),
                VocabularyRowDto.builder()
                        .japaneseText("テーブル")
                        .reading(null)
                        .meaningVi("Cái bàn")
                        .build(),
                VocabularyRowDto.builder()
                        .japaneseText("見る")
                        .reading("みる")
                        .meaningVi(null)
                        .build()
        );

        byte[] bytes = exportService.exportToText(rows, ExportTextFormat.COLON);
        String content = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);

        String[] lines = content.split("\r\n");
        assertThat(lines).hasSize(3);
        assertThat(lines[0]).isEqualTo("食べる (たべる) : Ăn");
        assertThat(lines[1]).isEqualTo("テーブル : Cái bàn");
        assertThat(lines[2]).isEqualTo("見る (みる) : ");
    }

    @Test
    @DisplayName("2. Dữ liệu có tab, xuống dòng, ký tự điều khiển được làm sạch bằng khoảng trắng")
    void exportToText_sanitizesTabsAndNewlines() {
        List<VocabularyRowDto> rows = List.of(
                VocabularyRowDto.builder()
                        .japaneseText("私\t\n")
                        .reading("\r\tわたし\u0007")
                        .meaningVi("Tôi\t\r\nlà ai?")
                        .build()
        );

        byte[] bytes = exportService.exportToText(rows, ExportTextFormat.TAB);
        String content = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);

        String[] lines = content.split("\r\n");
        assertThat(lines).hasSize(1);
        assertThat(lines[0]).isEqualTo("私\tわたし\tTôi là ai?");
    }

    @Test
    @DisplayName("Tự động tách bỏ phần ngoặc cách đọc trong japaneseText nguồn (ví dụ 私（わたし） -> 私)")
    void exportToText_stripsParenthesesInJapaneseText() {
        List<VocabularyRowDto> rows = List.of(
                VocabularyRowDto.builder()
                        .japaneseText("日本酒（にほんしゅ）")
                        .reading("にほんしゅ")
                        .meaningVi("Rượu sake")
                        .build()
        );

        byte[] bytes = exportService.exportToText(rows, ExportTextFormat.TAB);
        String content = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);

        String[] lines = content.split("\r\n");
        assertThat(lines).hasSize(1);
        assertThat(lines[0]).isEqualTo("日本酒\tにほんしゅ\tRượu sake");
    }

    @Test
    @DisplayName("Dòng rỗng (tất cả các trường đều trống hoặc null) thì bỏ qua")
    void exportToText_skipsEmptyLines() {
        List<VocabularyRowDto> rows = Arrays.asList(
                VocabularyRowDto.builder()
                        .japaneseText("")
                        .reading(null)
                        .meaningVi("  \t\n  ")
                        .build(),
                VocabularyRowDto.builder()
                        .japaneseText("犬")
                        .reading("いぬ")
                        .meaningVi("Con chó")
                        .build(),
                null
        );

        byte[] bytes = exportService.exportToText(rows, ExportTextFormat.TAB);
        String content = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);

        String[] lines = content.split("\r\n");
        assertThat(lines).hasSize(1);
        assertThat(lines[0]).isEqualTo("犬\tいぬ\tCon chó");
    }

    @Test
    @DisplayName("3. format=abc hoặc null chuyển về mặc định TAB")
    void exportTextFormat_fallbackToTab() {
        assertThat(ExportTextFormat.fromString("abc")).isEqualTo(ExportTextFormat.TAB);
        assertThat(ExportTextFormat.fromString(null)).isEqualTo(ExportTextFormat.TAB);
        assertThat(ExportTextFormat.fromString("")).isEqualTo(ExportTextFormat.TAB);
        assertThat(ExportTextFormat.fromString("colon")).isEqualTo(ExportTextFormat.COLON);
        assertThat(ExportTextFormat.fromString("TAB")).isEqualTo(ExportTextFormat.TAB);
    }
}

