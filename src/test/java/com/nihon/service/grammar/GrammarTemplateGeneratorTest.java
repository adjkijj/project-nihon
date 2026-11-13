package com.nihon.service.grammar;

import com.nihon.dto.grammar.GrammarImportPreviewReport;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GrammarTemplateGeneratorTest {

    @Test
    void generateAndVerifyTemplateFile() throws Exception {
        GrammarExcelService excelService = new GrammarExcelService(
                mock(com.nihon.repository.GrammarTopicRepository.class),
                mock(com.nihon.repository.GrammarExampleRepository.class),
                mock(com.nihon.repository.GrammarQuizQuestionRepository.class),
                mock(com.nihon.repository.GrammarQuizChoiceRepository.class),
                mock(com.nihon.service.excel.ExcelUploadValidator.class),
                mock(com.nihon.service.excel.ImportSessionStorage.class),
                mock(com.nihon.service.excel.ImportAuditService.class)
        );

        byte[] templateBytes = excelService.generateTemplate();
        assertThat(templateBytes).isNotEmpty();

        // 1. Kiểm tra cấu trúc workbook
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(templateBytes))) {
            assertThat(wb.getSheet("Grammar")).isNotNull();
            assertThat(wb.getSheet("Quiz")).isNotNull();

            if (wb instanceof XSSFWorkbook xssf) {
                String creator = xssf.getProperties().getCoreProperties().getCreator();
                assertThat(creator).isEqualTo("Project Nihon Team");
            }
        }

        // 2. Lưu file vào src/main/resources/static/template/GrammarTemplate.xlsx
        Path targetPath = Paths.get("src/main/resources/static/template/GrammarTemplate.xlsx");
        Files.createDirectories(targetPath.getParent());
        Files.write(targetPath, templateBytes);

        assertThat(Files.exists(targetPath)).isTrue();
        assertThat(Files.size(targetPath)).isGreaterThan(1000);
    }
}

