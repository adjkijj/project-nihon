package com.nihon.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NavbarTemplateTest {

    private static final Path TEMPLATES_DIR = Paths.get("src/main/resources/templates");
    private static final Path NAVBAR_FRAGMENT = Paths.get("src/main/resources/templates/fragments/navbar.html");

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Fragment navbar.html tồn tại và định nghĩa đầy đủ thuộc tính trợ năng aria-expanded, aria-controls")
    void testNavbarFragmentDefinesAriaAttributes() throws IOException {
        assertThat(Files.exists(NAVBAR_FRAGMENT)).isTrue();
        String content = Files.readString(NAVBAR_FRAGMENT, StandardCharsets.UTF_8);

        // Kiểm tra fragment headerNav
        assertThat(content).contains("th:fragment=\"headerNav(navItem)\"");

        // F02: Nút hamburger menu thu gọn có aria-expanded và aria-controls
        assertThat(content).contains("id=\"navToggle\"");
        assertThat(content).contains("aria-expanded=\"false\"");
        assertThat(content).contains("aria-controls=\"navDrawer\"");
        assertThat(content).contains("aria-label=\"Mở menu điều hướng\"");

        // F02: Dropdown menu Luyện tập
        assertThat(content).contains("id=\"practiceDropdownToggle\"");
        assertThat(content).contains("aria-haspopup=\"true\"");
        assertThat(content).contains("id=\"practiceDropdownMenu\"");

        // F02: Ngăn kéo di động và backdrop
        assertThat(content).contains("id=\"navDrawer\"");
        assertThat(content).contains("id=\"navBackdrop\"");
        assertThat(content).contains("id=\"navDrawerClose\"");

        // F04: Hộp thoại xác nhận accessible
        assertThat(content).contains("id=\"nihonConfirmDialog\"");
        assertThat(content).contains("aria-modal=\"true\"");
    }

    @Test
    @DisplayName("Tất cả các trang template người dùng đều dùng fragment navbar chung")
    void testAllUserFacingTemplatesUseNavbarFragment() throws IOException {
        List<Path> templateFiles;
        try (Stream<Path> stream = Files.walk(TEMPLATES_DIR)) {
            templateFiles = stream.filter(p -> p.toString().endsWith(".html"))
                    .filter(p -> !p.toString().contains("fragments"))
                    .collect(Collectors.toList());
        }

        assertThat(templateFiles).isNotEmpty();
        assertThat(templateFiles.size()).isEqualTo(30);

        for (Path file : templateFiles) {
            String templateContent = Files.readString(file, StandardCharsets.UTF_8);
            assertThat(templateContent)
                    .withFailMessage("Template %s chưa nhúng fragment navbar chung", file.getFileName())
                    .contains("fragments/navbar :: headerNav");
        }
    }

    @Test
    @DisplayName("GET / (Trang chủ) render thanh điều hướng chung có nút hamburger, dropdown và dialog")
    void testHomePageRendersSharedNavbar() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"navToggle\"")))
                .andExpect(content().string(containsString("aria-expanded=\"false\"")))
                .andExpect(content().string(containsString("aria-controls=\"navDrawer\"")))
                .andExpect(content().string(containsString("id=\"practiceDropdown\"")))
                .andExpect(content().string(containsString("id=\"practiceDropdownToggle\"")))
                .andExpect(content().string(containsString("id=\"nihonConfirmDialog\"")))
                .andExpect(content().string(containsString("ui-enhance.css")))
                .andExpect(content().string(containsString("ui-enhance.js")));
    }

    @Test
    @DisplayName("GET /vocabulary render thanh điều hướng chung và giữ nguyên nút bật tắt Furigana")
    void testVocabularyPageRendersSharedNavbar() throws Exception {
        mockMvc.perform(get("/vocabulary"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"navToggle\"")))
                .andExpect(content().string(containsString("id=\"furigana-toggle-btn\"")))
                .andExpect(content().string(containsString("id=\"practiceDropdownToggle\"")))
                .andExpect(content().string(containsString("ui-enhance.css")));
    }

    @Test
    @DisplayName("GET /practice render thanh điều hướng chung với menu Luyện tập")
    void testPracticePageRendersSharedNavbar() throws Exception {
        mockMvc.perform(get("/practice"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"navToggle\"")))
                .andExpect(content().string(containsString("id=\"practiceDropdown\"")))
                .andExpect(content().string(containsString("id=\"nihonConfirmDialog\"")));
    }
}

