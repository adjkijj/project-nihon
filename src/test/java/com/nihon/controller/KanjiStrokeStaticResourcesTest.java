package com.nihon.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.web.servlet.MockMvc;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class KanjiStrokeStaticResourcesTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("F19 - Test 4: Đường dẫn /kanjivg/ phục vụ file tĩnh đúng nguồn, file tồn tại trả về 200")
    void testServeStaticKanjivgFile_success() throws Exception {
        mockMvc.perform(get("/kanjivg/09b5a.svg"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/kanjivg/04e00.svg"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/kanjivg/0916f.svg"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("F19 - Test 4: Tên file lạ trong /kanjivg/ trả về 404 Not Found")
    void testServeStaticKanjivgFile_notFound() throws Exception {
        mockMvc.perform(get("/kanjivg/nonexistent_file_99999.svg"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("F19 - Test 4: Chặn Path Traversal ngoài thư mục kanjivg (thử ../)")
    void testServeStaticKanjivgFile_pathTraversalBlocked() throws Exception {
        mockMvc.perform(get("/kanjivg/../application.properties"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/kanjivg/..%2F..%2Fschema.sql"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("F19 - Test 5: Quét tự động toàn bộ file SVG trong static/kanjivg không chứa thẻ script hoặc thuộc tính on*")
    void testAllSvgFiles_containNoScriptNorOnEventAttributes() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:static/kanjivg/*.svg");

        assertThat(resources).isNotEmpty();

        Pattern scriptTagPattern = Pattern.compile("<\\s*script", Pattern.CASE_INSENSITIVE);
        Pattern onEventHandlerPattern = Pattern.compile("\\bon[a-zA-Z]+\\s*=", Pattern.CASE_INSENSITIVE);

        for (Resource res : resources) {
            String fileName = res.getFilename();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
                String content = reader.lines().collect(Collectors.joining("\n"));

                assertThat(scriptTagPattern.matcher(content).find())
                        .as("Tệp SVG '%s' không được chứa thẻ <script>", fileName)
                        .isFalse();

                assertThat(onEventHandlerPattern.matcher(content).find())
                        .as("Tệp SVG '%s' không được chứa thuộc tính on* (event handler)", fileName)
                        .isFalse();

                assertThat(content)
                        .as("Tệp SVG '%s' phải chứa thẻ mở <svg", fileName)
                        .contains("<svg");

                assertThat(content)
                        .as("Tệp SVG '%s' phải chứa ít nhất một thẻ <path", fileName)
                        .contains("<path");
            }
        }
    }

    @Test
    @DisplayName("F19 - Test 6: Kiểm tra có file LICENSE.txt trong static/kanjivg với nội dung CC BY-SA 3.0 và ghi nguồn KanjiVG")
    void testLicenseFileExists_andContainsKanjiVgAttribution() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:static/kanjivg/LICENSE.txt");

        assertThat(resources).hasSize(1);
        Resource licenseRes = resources[0];
        assertThat(licenseRes.exists()).isTrue();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(licenseRes.getInputStream(), StandardCharsets.UTF_8))) {
            String content = reader.lines().collect(Collectors.joining("\n"));
            assertThat(content).contains("KanjiVG");
            assertThat(content).contains("CC BY-SA 3.0");
            assertThat(content).contains("https://kanjivg.tagaini.net/");
        }
    }
}

