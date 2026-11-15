package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.dto.grammar.GrammarTopicForm;
import com.nihon.entity.GrammarTopic;
import com.nihon.entity.Topic;
import com.nihon.history.LearnerIdentityFilter;
import com.nihon.repository.TopicRepository;
import com.nihon.service.grammar.GrammarService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử chống lỗ hổng Open Redirect tại endpoint toggle tiến độ ngữ pháp (SEC-004):
 * Đảm bảo các giá trị redirectUrl độc hại bị loại bỏ và chuyển hướng an toàn về /grammar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GrammarOpenRedirectSecurityTest {

    private static final String CSRF_TOKEN = "csrf-token-open-redirect-test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private GrammarService grammarService;

    private Long grammarTopicId;
    private Cookie learnerCookie;

    @BeforeEach
    void setUp() {
        Topic generalTopic = new Topic();
        generalTopic.setName("Bài học Test Ngữ pháp " + UUID.randomUUID());
        generalTopic.setSortOrder(99);
        generalTopic.setKanjiSection(false);
        generalTopic = topicRepository.save(generalTopic);

        GrammarTopicForm gForm = GrammarTopicForm.builder()
                .code("N5-TEST-REDIR-" + UUID.randomUUID().toString().substring(0, 8))
                .title("Cấu trúc Test Redirect")
                .category("GRAMMAR")
                .topicId(generalTopic.getId())
                .formula("A + B")
                .explanation("Test explanation")
                .notes("Test notes")
                .build();
        GrammarTopic gt = grammarService.createTopic(gForm);
        this.grammarTopicId = gt.getId();
        this.learnerCookie = new Cookie(LearnerIdentityFilter.COOKIE_NAME, UUID.randomUUID().toString());
    }

    @Test
    @DisplayName("1. Gửi redirectUrl='https://evil.com' -> Chặn chuyển hướng ngoài, an toàn về /grammar")
    void toggleProgress_absoluteHttps_redirectsToFallback() throws Exception {
        mockMvc.perform(post("/grammar/" + grammarTopicId + "/toggle-progress")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("redirectUrl", "https://evil.com")
                        .cookie(learnerCookie))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/grammar"));
    }

    @Test
    @DisplayName("2. Gửi redirectUrl='//evil.com' -> Chặn scheme-relative, an toàn về /grammar")
    void toggleProgress_schemeRelative_redirectsToFallback() throws Exception {
        mockMvc.perform(post("/grammar/" + grammarTopicId + "/toggle-progress")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("redirectUrl", "//evil.com")
                        .cookie(learnerCookie))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/grammar"));
    }

    @Test
    @DisplayName("3. Gửi redirectUrl='/\\\\evil.com' -> Chặn backslash bypass, an toàn về /grammar")
    void toggleProgress_backslashBypass_redirectsToFallback() throws Exception {
        mockMvc.perform(post("/grammar/" + grammarTopicId + "/toggle-progress")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("redirectUrl", "/\\evil.com")
                        .cookie(learnerCookie))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/grammar"));
    }

    @Test
    @DisplayName("4. Gửi redirectUrl='/%2F%2Fevil.com' -> Chặn encoded double slash, an toàn về /grammar")
    void toggleProgress_encodedDoubleSlash_redirectsToFallback() throws Exception {
        mockMvc.perform(post("/grammar/" + grammarTopicId + "/toggle-progress")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("redirectUrl", "/%2F%2Fevil.com")
                        .cookie(learnerCookie))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/grammar"));
    }

    @Test
    @DisplayName("5. Gửi redirectUrl='javascript:alert(1)' -> Chặn chèn giao thức JS, an toàn về /grammar")
    void toggleProgress_javascriptProtocol_redirectsToFallback() throws Exception {
        mockMvc.perform(post("/grammar/" + grammarTopicId + "/toggle-progress")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("redirectUrl", "javascript:alert(1)")
                        .cookie(learnerCookie))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/grammar"));
    }

    @Test
    @DisplayName("6. Gửi redirectUrl='/grammar/1' -> Chấp nhận đường dẫn nội bộ hợp lệ")
    void toggleProgress_validRelativeUrl_redirectsToProvidedPath() throws Exception {
        mockMvc.perform(post("/grammar/" + grammarTopicId + "/toggle-progress")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("redirectUrl", "/grammar/1")
                        .cookie(learnerCookie))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/grammar/1"));
    }
}
