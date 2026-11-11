package com.nihon.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử toàn diện các đầu vào xấu (bad inputs), bảo mật và khả năng phục hồi
 * đối với toàn bộ các tham số và endpoint mới (F31, F29, F05, F25, F23, F58, F19, F26).
 * Tuyệt đối không trả về HTTP 500 và không làm lộ thông tin nội bộ / stack trace.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NewFeaturesSecurityAndBadInputTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String LONG_STRING_10K = "A".repeat(10000);
    private static final String EMOJI_INPUT = "🎌🍣🇯🇵😀🎉🚀🔥";
    private static final String SPECIAL_CHARS = "<script>alert('xss')</script>'\";!--=\"";

    @Test
    @DisplayName("F29 Export Text: tham số format lạ, âm, emoji -> an toàn (mặc định TAB hoặc 400/200, không 500)")
    void testExportText_badFormatInputs() throws Exception {
        String[] badFormats = {"", "   ", "XML", "CSV", "JSON", "-1", "999999", EMOJI_INPUT, SPECIAL_CHARS, "format_x"};
        for (String fmt : badFormats) {
            MvcResult result = mockMvc.perform(get("/vocabulary/export-text").param("format", fmt)).andReturn();
            int s = result.getResponse().getStatus();
            assertThat(s).isNotEqualTo(500);
            assertThat(result.getResponse().getContentAsString()).doesNotContain("NullPointerException");
        }
    }

    @Test
    @DisplayName("F29 Export Text: chuỗi format dài 10000 ký tự -> không 500")
    void testExportText_extremelyLongFormat() throws Exception {
        MvcResult result = mockMvc.perform(get("/vocabulary/export-text").param("format", LONG_STRING_10K)).andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(500);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("Internal Server Error");
    }

    @Test
    @DisplayName("F29 Export Text: tham số topicId xấu (âm, chữ, emoji, rất lớn) -> không 500")
    void testExportText_badTopicId() throws Exception {
        String[] badTopicIds = {"-1", "0", "abc", EMOJI_INPUT, "99999999999999999999"};
        for (String tid : badTopicIds) {
            MvcResult result = mockMvc.perform(get("/vocabulary/export-text").param("topicId", tid)).andReturn();
            assertThat(result.getResponse().getStatus()).isNotEqualTo(500);
            assertThat(result.getResponse().getContentAsString()).doesNotContain("NullPointerException");
        }
    }

    @Test
    @DisplayName("F29 Export Text: keyword chứa ký tự đặc biệt, emoji, 10000 ký tự -> không 500")
    void testExportText_badKeyword() throws Exception {
        MvcResult res1 = mockMvc.perform(get("/vocabulary/export-text").param("keyword", SPECIAL_CHARS)).andReturn();
        assertThat(res1.getResponse().getStatus()).isNotEqualTo(500);

        MvcResult res2 = mockMvc.perform(get("/vocabulary/export-text").param("keyword", EMOJI_INPUT)).andReturn();
        assertThat(res2.getResponse().getStatus()).isNotEqualTo(500);

        MvcResult res3 = mockMvc.perform(get("/vocabulary/export-text").param("keyword", LONG_STRING_10K)).andReturn();
        assertThat(res3.getResponse().getStatus()).isNotEqualTo(500);
    }

    @Test
    @DisplayName("F25 Flashcards: tham số side lạ (không phải JA hoặc VI) -> an toàn, mặc định JA")
    void testFlashcards_badSide() throws Exception {
        String[] badSides = {"", "EN", "FR", "-1", EMOJI_INPUT, SPECIAL_CHARS, LONG_STRING_10K};
        for (String side : badSides) {
            MvcResult result = mockMvc.perform(get("/flashcards").param("side", side)).andReturn();
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            assertThat(result.getResponse().getContentAsString()).doesNotContain("NullPointerException");
        }
    }

    @Test
    @DisplayName("F25 Flashcards: tham số page âm, rất lớn, chữ, emoji -> không 500")
    void testFlashcards_badPage() throws Exception {
        String[] badPages = {"-5", "abc", EMOJI_INPUT, "999999999999999999"};
        for (String page : badPages) {
            MvcResult result = mockMvc.perform(get("/flashcards").param("page", page)).andReturn();
            assertThat(result.getResponse().getStatus()).isNotEqualTo(500);
            assertThat(result.getResponse().getContentAsString()).doesNotContain("Internal Server Error");
        }
    }

    @Test
    @DisplayName("F25 Flashcards: tham số shuffle xấu -> không 500 (chỉ true/false)")
    void testFlashcards_badShuffle() throws Exception {
        MvcResult res1 = mockMvc.perform(get("/flashcards").param("shuffle", "invalid_value")).andReturn();
        assertThat(res1.getResponse().getStatus()).isEqualTo(200);

        MvcResult res2 = mockMvc.perform(get("/flashcards").param("shuffle", EMOJI_INPUT)).andReturn();
        assertThat(res2.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("F23 Multiple Choice: tham số direction xấu (khác JA_VI, VI_JA) -> mặc định JA_VI, không 500")
    void testPracticeChoice_badDirection() throws Exception {
        String[] badDirs = {"", "xyz", "-1", EMOJI_INPUT, SPECIAL_CHARS, LONG_STRING_10K};
        for (String dir : badDirs) {
            MvcResult result = mockMvc.perform(get("/practice/choice").param("direction", dir)).andReturn();
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            assertThat(result.getResponse().getContentAsString()).doesNotContain("NullPointerException");
        }
    }

    @Test
    @DisplayName("F23 Multiple Choice Question: direction xấu -> không 500")
    void testPracticeChoiceQuestion_badDirection() throws Exception {
        MvcResult result = mockMvc.perform(get("/practice/choice/question").param("direction", "INVALID_DIR")).andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(500);
    }

    @Test
    @DisplayName("F23 Multiple Choice Check: form submission thiếu CSRF hoặc dữ liệu xấu -> chặn an toàn, không 500")
    void testPracticeChoiceCheck_badPostData() throws Exception {
        MvcResult res1 = mockMvc.perform(post("/practice/choice/check")
                        .param("selectedOptionIndex", "-1")
                        .param("direction", "JA_VI")).andReturn();
        assertThat(res1.getResponse().getStatus()).isNotEqualTo(500);

        MvcResult res2 = mockMvc.perform(post("/practice/choice/check")
                        .param("selectedOptionIndex", "999999")
                        .param("direction", "JA_VI")).andReturn();
        assertThat(res2.getResponse().getStatus()).isNotEqualTo(500);
    }

    @Test
    @DisplayName("F26 Retry Wrong: retryWrongOnly nhận chuỗi xấu, 10000 ký tự, emoji -> không 500")
    void testRetryWrongOnly_badInputs() throws Exception {
        String[] badInputs = {"abc", "null", "-1", EMOJI_INPUT, SPECIAL_CHARS, LONG_STRING_10K};
        for (String input : badInputs) {
            MvcResult res1 = mockMvc.perform(get("/practice/question").param("retryWrongOnly", input)).andReturn();
            assertThat(res1.getResponse().getStatus()).isNotEqualTo(500);

            MvcResult res2 = mockMvc.perform(get("/practice/vi-ja/question").param("retryWrongOnly", input)).andReturn();
            assertThat(res2.getResponse().getStatus()).isNotEqualTo(500);
        }
    }

    @Test
    @DisplayName("F58 Vocab/Kanji Detail: id không tồn tại, âm, chữ, emoji -> trả 404/400/302, không 500")
    void testVocabAndKanjiDetail_badIdInputs() throws Exception {
        String[] badIds = {"-1", "0", "abc", EMOJI_INPUT, "9999999"};
        for (String id : badIds) {
            MvcResult res1 = mockMvc.perform(get("/vocabulary/" + id)).andReturn();
            assertThat(res1.getResponse().getStatus()).isNotEqualTo(500);

            MvcResult res2 = mockMvc.perform(get("/kanji/" + id)).andReturn();
            assertThat(res2.getResponse().getStatus()).isNotEqualTo(500);
        }
    }

    @Test
    @DisplayName("F19 Stroke Order: Path Traversal và tên file bất thường trên /kanjivg -> chặn an toàn, không lộ file hệ thống")
    void testStrokeOrder_pathTraversal() throws Exception {
        String[] maliciousPaths = {
                "/kanjivg/../secret.txt",
                "/kanjivg/../../pom.xml",
                "/kanjivg/..%2f..%2fpom.xml",
                "/kanjivg/04e00.svg.bak",
                "/kanjivg/invalid_name.svg",
                "/kanjivg/04E00.svg",
                "/kanjivg/04e00.exe"
        };
        for (String path : maliciousPaths) {
            MvcResult result = mockMvc.perform(get(path)).andReturn();
            assertThat(result.getResponse().getStatus()).isNotEqualTo(500);
            assertThat(result.getResponse().getContentAsString()).doesNotContain("Internal Server Error");
        }
    }

    @Test
    @DisplayName("F19 Stroke Order: File hợp lệ trả về đúng định dạng SVG và mã 200")
    void testStrokeOrder_validSvg() throws Exception {
        mockMvc.perform(get("/kanjivg/04e00.svg"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/svg+xml"))
                .andExpect(content().string(containsString("<svg")));
    }
}
