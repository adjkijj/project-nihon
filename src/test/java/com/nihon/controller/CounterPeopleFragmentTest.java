package com.nihon.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Kiểm thử Fragment đếm số người (~人) từ 1 đến 10 người:
 * - Fragment templates/fragments/counter-people.html
 * - Nạp vào trang ngữ pháp chính: templates/grammar/index.html (/grammar)
 * - Mỗi ô là một button có data-speak
 * - Đánh dấu màu cam (highlight-orange) cho: ひとり, ふたり, よにん, ななにん
 * - Script static/js/counter-people.js gắn sự kiện, không dùng onclick, dùng thuần Web Speech API
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CounterPeopleFragmentTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("1. Trang /grammar nạp thành công fragment counter-people và các asset css, js")
    void testGrammarIndexContainsCounterPeopleFragment() throws Exception {
        mockMvc.perform(get("/grammar"))
                .andExpect(status().isOk())
                .andExpect(view().name("grammar/index"))
                .andExpect(content().string(containsString("id=\"counter-people-section\"")))
                .andExpect(content().string(containsString("Bảng Đếm Số Người")))
                .andExpect(content().string(containsString("counter-people.css")))
                .andExpect(content().string(containsString("counter-people.js")));
    }

    @Test
    @DisplayName("2. Fragment chứa đầy đủ 10 người dưới dạng thẻ button với thuộc tính data-speak")
    void testAllTenPeopleCountersPresentAsButtonsWithDataSpeak() throws Exception {
        mockMvc.perform(get("/grammar"))
                .andExpect(status().isOk())
                // 1 đến 10 người với data-speak
                .andExpect(content().string(containsString("data-speak=\"ひとり\"")))
                .andExpect(content().string(containsString("data-speak=\"ふたり\"")))
                .andExpect(content().string(containsString("data-speak=\"さんにん\"")))
                .andExpect(content().string(containsString("data-speak=\"よにん\"")))
                .andExpect(content().string(containsString("data-speak=\"ごにん\"")))
                .andExpect(content().string(containsString("data-speak=\"ろくにん\"")))
                .andExpect(content().string(containsString("data-speak=\"ななにん\"")))
                .andExpect(content().string(containsString("data-speak=\"はちにん\"")))
                .andExpect(content().string(containsString("data-speak=\"きゅうにん\"")))
                .andExpect(content().string(containsString("data-speak=\"じゅうにん\"")))
                // 7 người hiển thị cả ななにん và しちにん
                .andExpect(content().string(containsString("ななにん / しちにん")));
    }

    @Test
    @DisplayName("3. Đánh dấu màu cam (highlight-orange) cho đúng 4 trường hợp đặc biệt: ひとり, ふたり, よにん, ななにん")
    void testOrangeHighlightOnSpecialCounters() throws Exception {
        String html = mockMvc.perform(get("/grammar"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        // ひとり, ふたり, よにん, ななにん phải có class highlight-orange
        assertThat(html).contains("data-speak=\"ひとり\"");
        assertThat(html).contains("class=\"counter-person-btn highlight-orange\" data-speak=\"ひとり\"");
        assertThat(html).contains("class=\"counter-person-btn highlight-orange\" data-speak=\"ふたり\"");
        assertThat(html).contains("class=\"counter-person-btn highlight-orange\" data-speak=\"よにん\"");
        assertThat(html).contains("class=\"counter-person-btn highlight-orange\" data-speak=\"ななにん\"");

        // Các từ theo quy tắc thông thường không có highlight-orange
        assertThat(html).contains("class=\"counter-person-btn\" data-speak=\"さんにん\"");
        assertThat(html).contains("class=\"counter-person-btn\" data-speak=\"ごにん\"");
        assertThat(html).contains("class=\"counter-person-btn\" data-speak=\"ろくにん\"");
        assertThat(html).contains("class=\"counter-person-btn\" data-speak=\"はちにん\"");
        assertThat(html).contains("class=\"counter-person-btn\" data-speak=\"きゅうにん\"");
        assertThat(html).contains("class=\"counter-person-btn\" data-speak=\"じゅうにん\"");
    }

    @Test
    @DisplayName("4. Template fragment và trang grammar không chứa onclick hay inline script tuân thủ CSP")
    void testFragmentAndTemplateContainNoOnclickOrInlineScript() throws IOException {
        Path fragmentPath = Paths.get("src/main/resources/templates/fragments/counter-people.html");
        assertThat(Files.exists(fragmentPath)).isTrue();

        String fragmentHtml = Files.readString(fragmentPath, StandardCharsets.UTF_8)
                .replaceAll("(?s)<!--.*?-->", "");
        assertThat(fragmentHtml).doesNotContain("onclick=");
        assertThat(fragmentHtml).doesNotContain("javascript:");
        assertThat(fragmentHtml).doesNotMatch("(?s).*<script(?![^>]*\\ssrc=)(?![^>]*\\sth:src=)[^>]*>.*");

        Path grammarPath = Paths.get("src/main/resources/templates/grammar/index.html");
        String grammarHtml = Files.readString(grammarPath, StandardCharsets.UTF_8)
                .replaceAll("(?s)<!--.*?-->", "");
        assertThat(grammarHtml).doesNotContain("onclick=");
        assertThat(grammarHtml).doesNotMatch("(?s).*<script(?![^>]*\\ssrc=)(?![^>]*\\sth:src=)[^>]*>.*");
    }

    @Test
    @DisplayName("5. Tệp static/js/counter-people.js dùng thuần Web Speech API và gắn sự kiện click bằng Event Listener")
    void testCounterPeopleJsIntegrity() throws IOException {
        Path jsPath = Paths.get("src/main/resources/static/js/counter-people.js");
        assertThat(Files.exists(jsPath)).isTrue();

        String js = Files.readString(jsPath, StandardCharsets.UTF_8);
        assertThat(js).contains("speechSynthesis");
        assertThat(js).contains("SpeechSynthesisUtterance");
        assertThat(js).contains("data-speak");
        assertThat(js).contains("addEventListener");
        assertThat(js).doesNotContain("onclick");
        assertThat(js).doesNotContain("translate.google.com");
    }

    @Test
    @DisplayName("6. Tệp static/css/counter-people.css định nghĩa màu cam cho lớp highlight-orange")
    void testCounterPeopleCssContainsOrangeColor() throws IOException {
        Path cssPath = Paths.get("src/main/resources/static/css/counter-people.css");
        assertThat(Files.exists(cssPath)).isTrue();

        String css = Files.readString(cssPath, StandardCharsets.UTF_8);
        assertThat(css).contains(".highlight-orange");
        assertThat(css).contains("#f97316"); // Mã màu cam
    }
}

