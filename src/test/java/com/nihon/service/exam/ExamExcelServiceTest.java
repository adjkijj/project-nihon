package com.nihon.service.exam;

import com.nihon.dto.exam.ExamImportPreviewReport;
import com.nihon.dto.exam.ExamPaperForm;
import com.nihon.dto.exam.ExamQuestionForm;
import com.nihon.dto.exam.ExamSectionForm;
import com.nihon.entity.ExamCategory;
import com.nihon.entity.ExamPaper;
import com.nihon.entity.ExamQuestionType;
import com.nihon.entity.ExamSection;
import com.nihon.entity.ImportAuditLog;
import com.nihon.repository.ImportAuditLogRepository;
import com.nihon.service.excel.UploadRejectedException;
import jakarta.persistence.EntityManager;
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
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ExamExcelServiceTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ExamExcelService examExcelService;

    @Autowired
    private ExamService examService;

    @Autowired
    private ImportAuditLogRepository auditLogRepository;

    @Test
    @DisplayName("Xem trước tệp mẫu Excel: 3 câu hỏi mẫu đều hợp lệ")
    void testPreviewTemplateExcel() throws IOException {
        byte[] templateBytes = examExcelService.generateTemplate();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "ExamTemplate.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                templateBytes
        );

        ExamImportPreviewReport report = examExcelService.preview(file, "127.0.0.1");

        assertThat(report).isNotNull();
        assertThat(report.isValid()).isTrue();
        assertThat(report.getPaperCode()).isEqualTo("N5-EXAM-01");
        assertThat(report.getCategory()).isEqualTo("MOCK");
        assertThat(report.getDurationMinutes()).isEqualTo(50);
        assertThat(report.getSectionsCount()).isEqualTo(2);
        assertThat(report.getQuestionsCount()).isEqualTo(3);
        assertThat(report.getErrors()).isEmpty();
        assertThat(report.getToken()).isNotBlank();
    }

    @Test
    @DisplayName("Xác nhận nhập (commit) đề thi từ Excel tạo đầy đủ dữ liệu trong CSDL và ghi log audit")
    void testCommitExcelImport() throws IOException {
        byte[] templateBytes = examExcelService.generateTemplate();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "ExamTemplate.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                templateBytes
        );

        ExamImportPreviewReport report = examExcelService.preview(file, "192.168.1.10");
        assertThat(report.isValid()).isTrue();

        ExamPaper paper = examExcelService.commit(report.getToken(), "192.168.1.10");

        assertThat(paper).isNotNull();
        assertThat(paper.getId()).isNotNull();
        assertThat(paper.getCode()).isEqualTo("N5-EXAM-01");

        entityManager.flush();
        entityManager.clear();
        ExamPaper savedPaper = examService.getExamWithDetails(paper.getId());
        assertThat(savedPaper.getSections()).hasSize(2);
        assertThat(savedPaper.getTotalQuestions()).isEqualTo(3);

        // Kiểm tra audit log
        List<ImportAuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(l -> "192.168.1.10".equals(l.getUserId())
                && l.getFileName().contains("ExamTemplate.xlsx"));
    }

    @Test
    @DisplayName("Từ chối tệp tin chứa phần mở rộng macro .xlsm")
    void testRejectMacroFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "MaliciousExam.xlsm",
                "application/vnd.ms-excel.sheet.macroEnabled.12",
                new byte[]{0x50, 0x4B, 0x03, 0x04}
        );

        assertThatThrownBy(() -> examExcelService.preview(file, "127.0.0.1"))
                .isInstanceOf(UploadRejectedException.class)
                .hasMessageContaining(".xlsx");
    }

    @Test
    @DisplayName("Phát hiện và báo lỗi khi tệp tin chứa công thức nguy hiểm (Formula Injection)")
    void testRejectFormulaInjection() throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Exam");
        Row header = sheet.createRow(0);
        for (int i = 0; i < 15; i++) {
            header.createCell(i).setCellValue("Header " + i);
        }

        Row row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue("FORMULA-CODE");
        row1.createCell(1).setCellValue("=CMD|'/c calc'!A0"); // Công thức nguy hiểm
        row1.createCell(2).setCellValue(60);
        row1.createCell(3).setCellValue("MOCK");
        row1.createCell(4).setCellValue("Phần 1");
        row1.createCell(5).setCellValue("MULTIPLE_CHOICE");
        row1.createCell(6).setCellValue("日本語");
        row1.createCell(7).setCellValue("Đề tiếng Việt");
        row1.createCell(8).setCellValue(1.0);
        row1.createCell(9).setCellValue("A");
        row1.createCell(10).setCellValue("B");
        row1.createCell(13).setCellValue("1");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        workbook.write(baos);
        workbook.close();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "FormulaInjection.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                baos.toByteArray()
        );

        ExamImportPreviewReport report = examExcelService.preview(file, "127.0.0.1");
        assertThat(report.isValid()).isFalse();
        assertThat(report.getErrors()).anyMatch(err -> err.message().contains("công thức"));
    }

    @Test
    @DisplayName("Xuất đề thi ra Excel và xem trước lại đảm bảo tính toàn vẹn (round-trip test)")
    void testExportAndReimportRoundTrip() throws IOException {
        ExamPaper paper = examService.createExam(ExamPaperForm.builder()
                .code("ROUNDTRIP-01")
                .title("Đề thi kiểm tra Round-trip")
                .category(ExamCategory.MIDTERM.name())
                .durationMinutes(50)
                .build());

        ExamSection section = examService.addSection(paper.getId(), ExamSectionForm.builder()
                .paperId(paper.getId())
                .title("Phần nghe hiểu")
                .build());

        examService.addQuestion(section.getId(), ExamQuestionForm.builder()
                .sectionId(section.getId())
                .type(ExamQuestionType.MULTIPLE_CHOICE.name())
                .points(BigDecimal.valueOf(2.00))
                .promptJa("これはペンですか。")
                .choice1("はい、ペンです")
                .choice2("いいえ、ちがいます")
                .correctChoiceIndex(1)
                .build());

        entityManager.flush();
        entityManager.clear();

        byte[] exportedBytes = examExcelService.exportExam(paper.getId());
        assertThat(exportedBytes).isNotEmpty();

        // Round-trip preview (sau khi xóa đề ban đầu để không bị trùng code)
        examService.deleteExam(paper.getId());
        entityManager.flush();
        entityManager.clear();

        MockMultipartFile reimportFile = new MockMultipartFile(
                "file",
                "Reimport.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                exportedBytes
        );

        ExamImportPreviewReport report = examExcelService.preview(reimportFile, "127.0.0.1");
        assertThat(report.isValid()).isTrue();
        assertThat(report.getPaperCode()).isEqualTo("ROUNDTRIP-01");
        assertThat(report.getQuestionsCount()).isEqualTo(1);
    }
}
