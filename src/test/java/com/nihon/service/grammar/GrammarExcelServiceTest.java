package com.nihon.service.grammar;

import com.nihon.dto.grammar.GrammarImportPreviewReport;
import com.nihon.entity.GrammarCategory;
import com.nihon.entity.GrammarTopic;
import com.nihon.repository.GrammarTopicRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class GrammarExcelServiceTest {

    @Autowired
    private GrammarExcelService excelService;

    @Autowired
    private GrammarTopicRepository topicRepository;

    @Autowired
    private GrammarService grammarService;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    @DisplayName("Xuất rồi nhập lại (Round-trip export & import): Dữ liệu bảo toàn nguyên vẹn")
    void testRoundTripExportAndImport() throws Exception {
        // 1. Tạo tệp mẫu từ hệ thống (chứa 2 chủ đề, mỗi chủ đề 2 ví dụ và 1 câu hỏi)
        byte[] templateBytes = excelService.generateTemplate();
        assertThat(templateBytes).isNotEmpty();

        MockMultipartFile file = new MockMultipartFile(
                "file", "GrammarTemplate.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                templateBytes
        );

        // 2. Preview kiểm tra
        GrammarImportPreviewReport report = excelService.preview(file, "127.0.0.1");
        assertThat(report.getErrors()).isEmpty();
        assertThat(report.getTotalTopics()).isEqualTo(2);
        assertThat(report.getTotalExamples()).isEqualTo(4);
        assertThat(report.getTotalQuestions()).isEqualTo(2);
        assertThat(report.canCommit()).isTrue();

        // 3. Commit vào CSDL
        int committed = excelService.commit(report.getToken(), "127.0.0.1");
        assertThat(committed).isEqualTo(2);

        entityManager.flush();
        entityManager.clear();

        GrammarTopic t1 = topicRepository.findByCode("N5-GR-01").orElse(null);
        assertThat(t1).isNotNull();
        assertThat(t1.getTitle()).contains("Cấu trúc 〜から");
        assertThat(t1.getExamples()).hasSize(2);
        assertThat(t1.getQuestions()).hasSize(1);
        assertThat(t1.getQuestions().get(0).getChoices()).hasSize(4);

        // 4. Xuất lại và kiểm tra metadata
        byte[] exportBytes = excelService.exportGrammar(null);
        assertThat(exportBytes).isNotEmpty();
    }

    @Test
    @DisplayName("Chặn trùng Code: Tệp Excel chứa mã đã tồn tại trong CSDL bị báo lỗi")
    void testDuplicateCodeBlocked() throws Exception {
        // Đã có mã N5-GR-01 từ trước
        GrammarTopic existing = GrammarTopic.builder()
                .code("N5-GR-01")
                .title("Đã tồn tại")
                .category(GrammarCategory.GRAMMAR)
                .build();
        topicRepository.save(existing);

        byte[] templateBytes = excelService.generateTemplate();
        MockMultipartFile file = new MockMultipartFile(
                "file", "Duplicate.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                templateBytes
        );

        GrammarImportPreviewReport report = excelService.preview(file, "127.0.0.1");
        assertThat(report.canCommit()).isFalse();
        assertThat(report.getErrors()).anyMatch(e -> e.contains("Mã 'N5-GR-01' đã tồn tại trong hệ thống"));
    }

    @Test
    @DisplayName("Chặn Formula Injection: Ô bắt đầu bằng =, +, @ bị phát hiện và từ chối")
    void testFormulaInjectionBlocked() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet gSheet = wb.createSheet("Grammar");
            Row header = gSheet.createRow(0);
            header.createCell(0).setCellValue("Code");
            header.createCell(1).setCellValue("Title");
            header.createCell(2).setCellValue("Category");

            Row r1 = gSheet.createRow(1);
            r1.createCell(0).setCellValue("=cmd|'/C calc'!A0"); // Độc hại
            r1.createCell(1).setCellValue("+1+2");
            r1.createCell(2).setCellValue("GRAMMAR");

            Sheet qSheet = wb.createSheet("Quiz");
            qSheet.createRow(0).createCell(0).setCellValue("Code");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);

            MockMultipartFile file = new MockMultipartFile(
                    "file", "Malicious.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    baos.toByteArray()
            );

            GrammarImportPreviewReport report = excelService.preview(file, "127.0.0.1");
            assertThat(report.canCommit()).isFalse();
            assertThat(report.getErrors()).anyMatch(e -> e.contains("Phát hiện ký tự khởi đầu công thức bị cấm"));
        }
    }

    @Test
    @DisplayName("Cho phép dấu gạch đầu dòng '- ' trong cột Giải thích")
    void testBulletPointInExplanationAllowed() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet gSheet = wb.createSheet("Grammar");
            Row header = gSheet.createRow(0);
            header.createCell(0).setCellValue("Code");
            header.createCell(1).setCellValue("Tiêu đề");
            header.createCell(2).setCellValue("Loại");
            header.createCell(3).setCellValue("Công thức");
            header.createCell(4).setCellValue("Giải thích");
            header.createCell(5).setCellValue("Ghi chú");
            header.createCell(6).setCellValue("Ví dụ tiếng Nhật");
            header.createCell(7).setCellValue("Cách đọc");
            header.createCell(8).setCellValue("Nghĩa tiếng Việt");

            Row r1 = gSheet.createRow(1);
            r1.createCell(0).setCellValue("VALID-01");
            r1.createCell(1).setCellValue("Tiêu đề hợp lệ");
            r1.createCell(2).setCellValue("GRAMMAR");
            r1.createCell(3).setCellValue("Công thức");
            r1.createCell(4).setCellValue("- Dòng 1\n- Dòng 2"); // Gạch đầu dòng hợp lệ
            r1.createCell(5).setCellValue("Ghi chú");
            r1.createCell(6).setCellValue("これは本です。");
            r1.createCell(7).setCellValue("これはほんです。");
            r1.createCell(8).setCellValue("Đây là quyển sách.");

            Sheet qSheet = wb.createSheet("Quiz");
            Row qHeader = qSheet.createRow(0);
            qHeader.createCell(0).setCellValue("Mã");
            qHeader.createCell(1).setCellValue("Đề");
            qHeader.createCell(2).setCellValue("A");
            qHeader.createCell(3).setCellValue("B");
            qHeader.createCell(4).setCellValue("C");
            qHeader.createCell(5).setCellValue("D");
            qHeader.createCell(6).setCellValue("Đúng");
            qHeader.createCell(7).setCellValue("Giải thích");

            Row qr = qSheet.createRow(1);
            qr.createCell(0).setCellValue("VALID-01");
            qr.createCell(1).setCellValue("Đề bài");
            qr.createCell(2).setCellValue("Đáp án 1");
            qr.createCell(3).setCellValue("Đáp án 2");
            qr.createCell(6).setCellValue("1");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);

            MockMultipartFile file = new MockMultipartFile(
                    "file", "ValidBullet.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    baos.toByteArray()
            );

            GrammarImportPreviewReport report = excelService.preview(file, "127.0.0.1");
            assertThat(report.getErrors()).isEmpty();
            assertThat(report.canCommit()).isTrue();
        }
    }

    @Test
    @DisplayName("Các ca lỗi cấu trúc: Thiếu sheet bắt buộc")
    void testMissingRequiredSheets() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet("OnlySheet");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);

            MockMultipartFile file = new MockMultipartFile(
                    "file", "MissingSheet.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    baos.toByteArray()
            );

            GrammarImportPreviewReport report = excelService.preview(file, "127.0.0.1");
            assertThat(report.canCommit()).isFalse();
            assertThat(report.getErrors()).anyMatch(e -> e.contains("thiếu trang tính bắt buộc"));
        }
    }
}

