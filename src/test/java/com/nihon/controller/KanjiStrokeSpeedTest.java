package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.config.WebConfig;
import com.nihon.dto.KanjiDetailDto;
import com.nihon.service.KanjiService;
import com.nihon.service.VocabularyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Kiểm thử tính năng F84: Tốc độ vẽ nét Kanji
 * - Thêm 3 nút 0.5x, 1x, 2x cạnh khung KanjiVG trên templates/kanji/detail.html.
 * - kanji-stroke-player.js chia thời gian mỗi nét cho hệ số tốc độ.
 * - Lưu localStorage khóa nihon_kanji_speed bọc trong try/catch.
 * - Giữ nguyên thông tin ghi công CC BY-SA 3.0.
 * - Tuân thủ CSP: không có JavaScript inline và không dùng onclick.
 */
@WebMvcTest(KanjiController.class)
@Import({WebConfig.class, CsrfInterceptor.class})
class KanjiStrokeSpeedTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KanjiService kanjiService;

    @MockitoBean
    private VocabularyService vocabularyService;

    private KanjiDetailDto kanjiWithStroke;
    private KanjiDetailDto kanjiWithoutStroke;

    @BeforeEach
    void setUp() {
        kanjiWithStroke = KanjiDetailDto.builder()
                .id(1L)
                .character("魚")
                .hanViet("NGƯ")
                .meaningVi("Cá")
                .onyomi("ギョ")
                .kunyomi("さかな")
                .topicId(5L)
                .topicName("Kanji Lesson 5")
                .sourceLine(1244)
                .strokeFileName("09b5a.svg")
                .hasStrokeData(true)
                .build();

        kanjiWithoutStroke = KanjiDetailDto.builder()
                .id(2L)
                .character("校")
                .hanViet("GIÁO, HIỆU")
                .meaningVi("Trường học")
                .onyomi("こう")
                .kunyomi("-")
                .topicId(5L)
                .topicName("Kanji Lesson 5")
                .sourceLine(1800)
                .strokeFileName(null)
                .hasStrokeData(false)
                .build();

        when(vocabularyService.findWordsContainingKanji(any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
    }

    @Test
    @DisplayName("F84: Trang chi tiết Kanji hiển thị 3 nút tốc độ 0.5x, 1x, 2x cạnh khung KanjiVG khi có dữ liệu nét")
    void testDetailWithStrokeDataRendersSpeedButtons() throws Exception {
        when(kanjiService.findDetailById(1L)).thenReturn(Optional.of(kanjiWithStroke));
        when(kanjiService.getRelatedVocabulary("魚", 5L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/kanji/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("kanji/detail"))
                .andExpect(content().string(containsString("id=\"stroke-order-box\"")))
                .andExpect(content().string(containsString("data-speed=\"0.5\"")))
                .andExpect(content().string(containsString("0.5x")))
                .andExpect(content().string(containsString("data-speed=\"1.0\"")))
                .andExpect(content().string(containsString("1x")))
                .andExpect(content().string(containsString("data-speed=\"2.0\"")))
                .andExpect(content().string(containsString("2x")))
                .andExpect(content().string(containsString("kanji-stroke-player.js")));
    }

    @Test
    @DisplayName("F84: Giữ nguyên thông tin ghi công CC BY-SA 3.0 cho KanjiVG trên templates/kanji/detail.html")
    void testDetailPreservesAttributionLicense() throws Exception {
        when(kanjiService.findDetailById(1L)).thenReturn(Optional.of(kanjiWithStroke));
        when(kanjiService.getRelatedVocabulary("魚", 5L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/kanji/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CC BY-SA 3.0")))
                .andExpect(content().string(containsString("KanjiVG")))
                .andExpect(content().string(containsString("https://kanjivg.tagaini.net/")));
    }

    @Test
    @DisplayName("F84: Trang chi tiết Kanji không hiển thị khung nét và nút tốc độ khi hasStrokeData == false")
    void testDetailWithoutStrokeDataHidesStrokeBoxAndSpeedButtons() throws Exception {
        when(kanjiService.findDetailById(2L)).thenReturn(Optional.of(kanjiWithoutStroke));
        when(kanjiService.getRelatedVocabulary("校", 5L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/kanji/2"))
                .andExpect(status().isOk())
                .andExpect(view().name("kanji/detail"))
                .andExpect(content().string(not(containsString("id=\"stroke-order-box\""))))
                .andExpect(content().string(not(containsString("data-speed=\"0.5\""))));
    }

    @Test
    @DisplayName("F84: Template templates/kanji/detail.html không chứa script inline hoặc onclick tuân thủ CSP")
    void testDetailTemplateHasNoInlineScriptOrOnclick() throws IOException {
        Path templatePath = Paths.get("src/main/resources/templates/kanji/detail.html");
        String content = Files.readString(templatePath, StandardCharsets.UTF_8)
                .replaceAll("(?s)<!--.*?-->", "");

        assertFalse(content.matches("(?s).*<script(?![^>]*\\ssrc=)(?![^>]*\\sth:src=)[^>]*>.*"),
                "detail.html không được chứa thẻ script inline!");
        assertFalse(content.contains("onclick="), "detail.html không được dùng onclick!");
    }

    @Test
    @DisplayName("F84: kanji-stroke-player.js lưu localStorage nihon_kanji_speed, bọc try/catch và chia thời gian cho tốc độ")
    void testScriptContainsSpeedLogic() throws IOException {
        Path scriptPath = Paths.get("src/main/resources/static/js/kanji-stroke-player.js");
        assertTrue(Files.exists(scriptPath), "kanji-stroke-player.js phải tồn tại!");

        String js = Files.readString(scriptPath, StandardCharsets.UTF_8);
        assertTrue(js.contains("nihon_kanji_speed"), "Script phải dùng khóa localStorage 'nihon_kanji_speed'!");
        assertTrue(js.contains("try") && js.contains("catch"), "Thao tác localStorage phải được bọc trong try/catch!");
        assertTrue(js.contains("/ speed"), "Script phải chia thời gian mỗi nét cho hệ số tốc độ (/ speed)!");
        assertTrue(js.contains("CC BY-SA 3.0"), "Script phải giữ nguyên thông tin ghi công CC BY-SA 3.0!");
    }
}

