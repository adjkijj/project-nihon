package com.nihon.service.exam;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class ExamTemplateGeneratorTest {

    @Test
    @DisplayName("Tạo tệp mẫu ExamTemplate.xlsx và ghi vào static/template/")
    void testGenerateTemplateAndSaveToStatic() throws IOException {
        ExamExcelService service = new ExamExcelService(null, null, null, null, null, null, null, null);
        byte[] bytes = service.generateTemplate();
        assertThat(bytes).isNotNull().isNotEmpty();

        Path targetDir = Paths.get("target");
        Files.createDirectories(targetDir);
        Path targetFile = targetDir.resolve("ExamTemplate.xlsx");

        Files.write(targetFile, bytes);
        assertThat(Files.exists(targetFile)).isTrue();
        assertThat(Files.size(targetFile)).isGreaterThan(1000L);
    }
}

