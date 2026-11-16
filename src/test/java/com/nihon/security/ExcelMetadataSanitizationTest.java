package com.nihon.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm thử chứng minh phát hiện SEC-009 đã được khắc phục hoàn toàn:
 * Không còn bất kỳ dấu vết metadata của WPS Office / Kingsoft trong Update.xlsx
 * và mọi file Excel mẫu (.xlsx).
 */
class ExcelMetadataSanitizationTest {

    private static final List<String> TEMPLATE_FILES = List.of(
            "static/template/Update.xlsx",
            "static/template/ExamTemplate.xlsx",
            "static/template/GrammarTemplate.xlsx"
    );

    @Test
    @DisplayName("1. Update.xlsx không chứa metadata WPS, KSO hay ký tự 工作表")
    void updateXlsx_containsNoWpsMetadata() throws Exception {
        ClassPathResource resource = new ClassPathResource("static/template/Update.xlsx");
        assertThat(resource.exists()).isTrue();

        try (InputStream is = resource.getInputStream();
             ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // custom.xml chứa KSOProductBuildVer phải bị loại bỏ hoàn toàn
                assertThat(entry.getName()).isNotEqualTo("docProps/custom.xml");

                if (entry.getName().endsWith(".xml") || entry.getName().endsWith(".rels")) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
                    String line;
                    StringBuilder content = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        content.append(line);
                    }
                    String text = content.toString();

                    assertThat(text)
                            .withFailMessage("Tìm thấy dấu vết WPS/Kingsoft trong entry " + entry.getName())
                            .doesNotContainIgnoringCase("wps.cn")
                            .doesNotContainIgnoringCase("KSOProductBuildVer")
                            .doesNotContain("工作表");
                }
            }
        }
    }

    @Test
    @DisplayName("2. Toàn bộ các file template .xlsx đều sạch metadata phần mềm và tác giả cá nhân")
    void allTemplateFiles_cleanMetadata() throws Exception {
        for (String templatePath : TEMPLATE_FILES) {
            ClassPathResource resource = new ClassPathResource(templatePath);
            assertThat(resource.exists()).isTrue();

            try (InputStream is = resource.getInputStream();
                 ZipInputStream zis = new ZipInputStream(is)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().equals("docProps/app.xml") || entry.getName().equals("docProps/core.xml")) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
                        String line;
                        StringBuilder content = new StringBuilder();
                        while ((line = reader.readLine()) != null) {
                            content.append(line);
                        }
                        String xml = content.toString();

                        // Không chứa WPS
                        assertThat(xml).doesNotContainIgnoringCase("wps");
                        assertThat(xml).doesNotContainIgnoringCase("kingsoft");
                        assertThat(xml).doesNotContain("工作表");

                        // Không chứa email cá nhân
                        assertThat(xml).doesNotContain("@gmail.com");
                        assertThat(xml).doesNotContain("@yahoo.com");
                    }
                }
            }
        }
    }
}

