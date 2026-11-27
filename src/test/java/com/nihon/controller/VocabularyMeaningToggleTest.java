package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.config.WebConfig;
import com.nihon.dto.VocabularyRowDto;
import com.nihon.entity.Topic;
import com.nihon.service.TopicService;
import com.nihon.service.VocabularyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử tính năng Ẩn nghĩa trên bảng từ vựng (templates/vocabulary/list.html):
 * - Nút "Ẩn nghĩa" có mặt trong thanh công cụ, có aria-pressed.
 * - Ô nghĩa có class "meaning-cell" và thuộc tính "tabindex=0".
 * - Tệp script static/js/vocabulary-meaning-toggle.js được liên kết, không có script inline.
 * - Mã JavaScript xử lý class hide-meaning trên bảng, lưu vào localStorage khóa 'nihon_hide_meaning' bọc try/catch.
 * - Mã CSS định nghĩa .hide-meaning .meaning-cell làm mờ bằng filter blur, hover/focus hiện lại.
 */
@WebMvcTest(controllers = {VocabularyController.class})
@Import({WebConfig.class, CsrfInterceptor.class})
class VocabularyMeaningToggleTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VocabularyService vocabularyService;

    @MockitoBean
    private TopicService topicService;

    @BeforeEach
    void setUp() {
        Topic topic = new Topic();
        topic.setId(1L);
        topic.setName("1. Chào hỏi");
        when(topicService.findAllOrdered()).thenReturn(List.of(topic));

        VocabularyRowDto rowDto = VocabularyRowDto.builder()
                .id(10L)
                .japaneseText("私（わたし）")
                .reading("わたし")
                .romaji("watashi")
                .meaningVi("Tôi")
                .topics(List.of(topic))
                .allIds(List.of(10L))
                .build();

        when(vocabularyService.search(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(rowDto)));
    }

    @Test
    @DisplayName("Template vocabulary/list.html chứa nút 'Ẩn nghĩa' với thuộc tính aria-pressed và script liên kết ngoài")
    void templateContainsHideMeaningButtonAndExternalScript() throws Exception {
        mockMvc.perform(get("/vocabulary"))
                .andExpect(status().isOk())
                // Có nút "Ẩn nghĩa"
                .andExpect(content().string(containsString("Ẩn nghĩa")))
                // Nút có id hoặc class nhận diện
                .andExpect(content().string(containsString("id=\"btn-hide-meaning\"")))
                // Có thuộc tính aria-pressed
                .andExpect(content().string(containsString("aria-pressed=\"false\"")))
                // Liên kết script ngoài vocabulary-meaning-toggle.js
                .andExpect(content().string(containsString("vocabulary-meaning-toggle.js")))
                // Bảng có id vocab-table
                .andExpect(content().string(containsString("id=\"vocab-table\"")));
    }

    @Test
    @DisplayName("Ô nghĩa tiếng Việt trên bảng có class meaning-cell và tabindex=0 để hỗ trợ bàn phím")
    void templateMeaningCellHasMeaningCellClassAndTabindex() throws Exception {
        mockMvc.perform(get("/vocabulary"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"meaning-cell\"")))
                .andExpect(content().string(containsString("tabindex=\"0\"")))
                .andExpect(content().string(containsString(">Tôi</td>")));
    }

    @Test
    @DisplayName("Template vocabulary/list.html không chứa script inline hoặc onclick tuân thủ CSP")
    void templateContainsNoInlineScriptOrOnclick() throws IOException {
        Path templatePath = Paths.get("src/main/resources/templates/vocabulary/list.html");
        String content = Files.readString(templatePath, StandardCharsets.UTF_8)
                .replaceAll("(?s)<!--.*?-->", "");

        // Không có script nội dòng (chỉ cho phép script có src hoặc th:src)
        assertTrue(!content.matches("(?s).*<script(?![^>]*\\ssrc=)(?![^>]*\\sth:src=)[^>]*>.*"),
                "list.html không được chứa thẻ script inline!");

        // Không có sự kiện on* (như onclick)
        assertTrue(!content.contains("onclick="), "list.html không được dùng onclick!");
    }

    @Test
    @DisplayName("Tệp script static/js/vocabulary-meaning-toggle.js sử dụng khóa nihon_hide_meaning và bọc trong try/catch")
    void scriptContainsLocalStorageTryCatchAndCorrectKey() throws IOException {
        Path scriptPath = Paths.get("src/main/resources/static/js/vocabulary-meaning-toggle.js");
        assertTrue(Files.exists(scriptPath), "Tệp static/js/vocabulary-meaning-toggle.js phải tồn tại!");

        String js = Files.readString(scriptPath, StandardCharsets.UTF_8);
        assertTrue(js.contains("nihon_hide_meaning"), "Script phải sử dụng khóa localStorage 'nihon_hide_meaning'!");
        assertTrue(js.contains("try"), "Thao tác localStorage phải được bọc trong khối try!");
        assertTrue(js.contains("catch"), "Thao tác localStorage phải có khối catch!");
        assertTrue(js.contains("hide-meaning"), "Script phải thao tác với class 'hide-meaning' trên bảng!");
        assertTrue(js.contains("aria-pressed"), "Script phải cập nhật thuộc tính 'aria-pressed'!");
    }

    @Test
    @DisplayName("Tệp CSS static/css/vocabulary-list.css định nghĩa filter blur cho .hide-meaning .meaning-cell và hover/focus")
    void cssContainsBlurFilterAndHoverFocusSelectors() throws IOException {
        Path cssPath = Paths.get("src/main/resources/static/css/vocabulary-list.css");
        assertTrue(Files.exists(cssPath), "Tệp static/css/vocabulary-list.css phải tồn tại!");

        String css = Files.readString(cssPath, StandardCharsets.UTF_8);
        assertTrue(css.contains(".hide-meaning .meaning-cell"), "CSS phải định nghĩa quy tắc cho '.hide-meaning .meaning-cell'!");
        assertTrue(css.contains("filter:") && css.contains("blur"), "CSS phải làm mờ ô nghĩa bằng filter blur!");
        assertTrue(css.contains(".hide-meaning .meaning-cell:hover"), "CSS phải hỗ trợ hover để hiện lại nghĩa!");
        assertTrue(css.contains(".hide-meaning .meaning-cell:focus"), "CSS phải hỗ trợ focus bàn phím để hiện lại nghĩa!");
    }
}

