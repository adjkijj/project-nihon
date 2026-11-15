package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.config.WebConfig;
import com.nihon.dto.KanjiPracticeMode;
import com.nihon.dto.TopicGroupDto;
import com.nihon.entity.Topic;
import com.nihon.service.KanjiService;
import com.nihon.service.StudyProgressService;
import com.nihon.service.TopicService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Kiểm thử tính năng Typing Studio - Tổ hợp Luyện gõ Tiếng Nhật Đa chế độ (F53).
 * Xác minh:
 * 1. Màn hình /practice/studio có thanh chuyển chế độ (tabs).
 * 2. Gọi lại chính xác các khối màn hình /practice, /practice/vi-ja, /practice/kanji.
 * 3. Hỗ trợ query parameter ?mode=ja-vi, ?mode=vi-ja, ?mode=kanji.
 * 4. Không làm thay đổi hay phá vỡ các URL cũ.
 * 5. Script practice-studio.js tuân thủ CSP strict.
 */
@WebMvcTest(controllers = {
        PracticeStudioController.class,
        PracticeController.class,
        PracticeViJaController.class,
        KanjiPracticeController.class
})
@Import({WebConfig.class, CsrfInterceptor.class})
class PracticeStudioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TopicService topicService;

    @MockitoBean
    private KanjiService kanjiService;

    @MockitoBean
    private StudyProgressService studyProgressService;

    @MockitoBean
    private com.nihon.service.PracticeService practiceService;

    @MockitoBean
    private com.nihon.service.KanjiPracticeService kanjiPracticeService;

    private Topic testTopic;

    @BeforeEach
    void setUp() {
        testTopic = new Topic();
        testTopic.setId(10L);
        testTopic.setName("Bài 1: Chào hỏi");
        testTopic.setSortOrder(1);

        when(topicService.findAllOrdered()).thenReturn(List.of(testTopic));
        when(topicService.findById(10L)).thenReturn(java.util.Optional.of(testTopic));

        when(kanjiService.getKanjiTopics()).thenReturn(List.of(testTopic));
        TopicGroupDto groupDto = new TopicGroupDto("Nhóm bài 1", List.of(testTopic));
        when(kanjiService.getKanjiTopicGroups()).thenReturn(List.of(groupDto));

        when(studyProgressService.getWrongVocabIds(any(), eq("JA_VI"), any())).thenReturn(Set.of());
        when(studyProgressService.getWrongVocabIds(any(), eq("VI_JA"), any())).thenReturn(Set.of());
        when(studyProgressService.getWrongKanjiIds(any(), any(), any())).thenReturn(Set.of());
    }

    @Test
    @DisplayName("GET /practice/studio mặc định: Hiển thị chế độ Nhật -> Việt với thanh chuyển chế độ")
    void testStudioDefaultMode_rendersJaVi() throws Exception {
        mockMvc.perform(get("/practice/studio"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/studio"))
                .andExpect(model().attribute("studioMode", "ja-vi"))
                .andExpect(model().attributeExists("topics", "topicGroups", "wrongCount"))
                .andExpect(content().string(containsString("Tổ Hợp Luyện Gõ Tiếng Nhật Đa Chế Độ")))
                .andExpect(content().string(containsString("🎙️ Typing Studio (F53)")))
                .andExpect(content().string(containsString("🇯🇵 → 🇻🇳 Nhật → Việt")))
                .andExpect(content().string(containsString("🇻🇳 → 🇯🇵 Việt → Nhật")))
                .andExpect(content().string(containsString("漢 Luyện tập Kanji")))
                // Gọi lại khối form từ /practice
                .andExpect(content().string(containsString("Kiểm Tra Từ Vựng (Nhật → Việt)")))
                .andExpect(content().string(containsString("action=\"/practice/question\"")))
                .andExpect(content().string(containsString("id=\"panel-ja-vi\"")))
                // Có các link chuyển màn hình độc lập
                .andExpect(content().string(containsString("href=\"/practice\"")))
                .andExpect(content().string(containsString("href=\"/practice/vi-ja\"")))
                .andExpect(content().string(containsString("href=\"/practice/kanji\"")));
    }

    @Test
    @DisplayName("GET /practice/studio?mode=vi-ja: Chuyển sang chế độ Việt -> Nhật")
    void testStudioMode_viJa() throws Exception {
        mockMvc.perform(get("/practice/studio").param("mode", "vi-ja"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/studio"))
                .andExpect(model().attribute("studioMode", "vi-ja"))
                .andExpect(content().string(containsString("Kiểm Tra Từ Vựng (Việt → Nhật)")))
                .andExpect(content().string(containsString("action=\"/practice/vi-ja/question\"")))
                .andExpect(content().string(containsString("id=\"panel-vi-ja\"")));
    }

    @Test
    @DisplayName("GET /practice/studio?mode=kanji: Chuyển sang chế độ Luyện tập Kanji")
    void testStudioMode_kanji() throws Exception {
        mockMvc.perform(get("/practice/studio").param("mode", "kanji"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/studio"))
                .andExpect(model().attribute("studioMode", "kanji"))
                .andExpect(content().string(containsString("Chế Độ Luyện Tập Chữ Hán (Kanji)")))
                .andExpect(content().string(containsString("action=\"/practice/kanji/question\"")))
                .andExpect(content().string(containsString("id=\"panel-kanji\"")));
    }

    @Test
    @DisplayName("GET /practice/studio?mode=unknown: Tự động fallback về ja-vi")
    void testStudioMode_unknown_fallbacksToJaVi() throws Exception {
        mockMvc.perform(get("/practice/studio").param("mode", "unknown_mode"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/studio"))
                .andExpect(model().attribute("studioMode", "ja-vi"));
    }

    @Test
    @DisplayName("Giữ nguyên các URL cũ: /practice, /practice/vi-ja, /practice/kanji vẫn hoạt động bình thường")
    void testLegacyPracticeUrls_remainIntact() throws Exception {
        mockMvc.perform(get("/practice"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/index"))
                .andExpect(content().string(containsString("Kiểm Tra Từ Vựng (Nhật → Việt)")))
                .andExpect(content().string(containsString("href=\"/practice/studio\"")));

        mockMvc.perform(get("/practice/vi-ja"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/vi-ja-index"))
                .andExpect(content().string(containsString("Kiểm Tra Từ Vựng (Việt → Nhật)")))
                .andExpect(content().string(containsString("href=\"/practice/studio\"")));

        mockMvc.perform(get("/practice/kanji"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/kanji-index"))
                .andExpect(content().string(containsString("Chế Độ Luyện Tập Chữ Hán (Kanji)")))
                .andExpect(content().string(containsString("href=\"/practice/studio\"")));
    }

    @Test
    @DisplayName("Kiểm tra script practice-studio.js tuân thủ CSP: không chứa inline JS hay eval")
    void testStudioScriptExistsAndCompliesWithCsp() throws Exception {
        Path scriptPath = Paths.get("src/main/resources/static/js/practice-studio.js");
        assertThat(Files.exists(scriptPath)).isTrue();

        String content = Files.readString(scriptPath);
        assertThat(content).doesNotContain("javascript:");
        assertThat(content).doesNotContain("eval(");
        assertThat(content).doesNotContain("onclick");
        assertThat(content).contains("addEventListener");
    }
}

