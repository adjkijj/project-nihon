package com.nihon.security;

import com.nihon.config.CsrfInterceptor;
import com.nihon.config.WebConfig;
import com.nihon.controller.GlobalExceptionHandler;
import com.nihon.controller.HomeController;
import com.nihon.controller.VocabularyController;
import com.nihon.service.TopicService;
import com.nihon.service.VocabularyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {HomeController.class, VocabularyController.class, GlobalExceptionHandler.class})
@Import({WebConfig.class, CsrfInterceptor.class})
class SecurityReviewTest {

    private static final String VALID_CSRF_TOKEN = "valid-csrf-token-123";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VocabularyService vocabularyService;

    @MockitoBean
    private TopicService topicService;

    @Test
    @DisplayName("Security Headers: Kiểm tra tự động gắn X-Frame-Options, X-Content-Type-Options và Referrer-Policy")
    void testSecurityHeadersPresent() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test
    @DisplayName("HSTS: Khi kết nối an toàn (HTTPS / Reverse Proxy SSL), HSTS header được tự động gắn")
    void testHstsPresentOnSecureRequest() throws Exception {
        mockMvc.perform(get("/").secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000; includeSubDomains"));
    }


    @Test
    @DisplayName("CSRF Protection: POST không có CSRF token phải bị chặn với mã 403 Forbidden")
    void testPostWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/vocabulary/new")
                        .param("japaneseText", "テスト")
                        .param("romaji", "tesuto")
                        .param("meaningVi", "Kiểm tra")
                        .param("topicId", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CSRF Protection: POST với CSRF token sai lệch phải bị chặn với mã 403 Forbidden")
    void testPostWithInvalidCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/vocabulary/new")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, VALID_CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, "wrong-token-abc")
                        .param("japaneseText", "テスト")
                        .param("romaji", "tesuto")
                        .param("meaningVi", "Kiểm tra")
                        .param("topicId", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CSRF Protection: POST với Header X-CSRF-TOKEN hợp lệ được chấp nhận qua Interceptor")
    void testPostWithValidCsrfHeaderIsAccepted() throws Exception {
        mockMvc.perform(post("/vocabulary/new")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, VALID_CSRF_TOKEN)
                        .header(CsrfInterceptor.CSRF_HEADER_NAME, VALID_CSRF_TOKEN)
                        .param("japaneseText", "テスト")
                        .param("romaji", "tesuto")
                        .param("meaningVi", "Kiểm tra")
                        .param("topicId", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vocabulary"));
    }

    @Test
    @DisplayName("Type Mismatch: Tham số ID không hợp lệ trong URL được xử lý an toàn và không lộ stack trace")
    void testTypeMismatchHandlingWithoutStackTrace() throws Exception {
        mockMvc.perform(get("/vocabulary/not-a-number/edit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("errorMessage"))
                .andExpect(content().string(not(containsString("MethodArgumentTypeMismatchException"))))
                .andExpect(content().string(not(containsString("java.lang.NumberFormatException"))));
    }
}
