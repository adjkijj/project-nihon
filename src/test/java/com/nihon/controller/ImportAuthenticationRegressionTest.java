package com.nihon.controller;

import com.nihon.config.ClientIpResolver;
import com.nihon.config.CsrfInterceptor;
import com.nihon.config.ImportSecurityInterceptor;
import com.nihon.config.WebConfig;
import com.nihon.service.excel.ImportService;
import com.nihon.service.excel.ImportSessionStorage;
import com.nihon.service.excel.RateLimiterService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Kiểm thử an ninh và hồi quy cho tính năng Import học liệu:
 * 1. GET /import không có tài khoản (unauthenticated) trả 200 OK và hiện form upload.
 * 2. POST thiếu token CSRF bị từ chối với HTTP 403 Forbidden.
 * 3. Địa chỉ IP ngoài danh sách allowed-ips nhận HTTP 403 Forbidden.
 */
@WebMvcTest(ImportController.class)
@Import({WebConfig.class, CsrfInterceptor.class, ImportSecurityInterceptor.class, ClientIpResolver.class, ImportSessionStorage.class, RateLimiterService.class})
class ImportAuthenticationRegressionTest {

    private static final String CSRF_TOKEN = "test-csrf-token-abc";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImportService importService;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private ImportSessionStorage sessionStorage;

    @BeforeEach
    void setUp() {
        rateLimiterService.reset();
        sessionStorage.clear();
    }

    @Test
    @DisplayName("1. GET /import không có tài khoản trả 200 OK và hiển thị form upload")
    void unauthenticated_accessGetImport_returns200OkWithUploadForm() throws Exception {
        mockMvc.perform(get("/import"))
                .andExpect(status().isOk())
                .andExpect(view().name("import"))
                .andExpect(content().string(containsString("previewForm")))
                .andExpect(content().string(containsString("Bước 1: Chọn Tệp Excel &amp; Xem Trước")));
    }

    @Test
    @DisplayName("2. POST preview thiếu token CSRF bị từ chối với HTTP 403 Forbidden")
    void missingCsrfToken_postPreview_returns403Forbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{0x50, 0x4B, 0x03, 0x04});

        mockMvc.perform(multipart("/import/preview").file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("3. IP ngoài danh sách allowed-ips truy cập GET /import nhận HTTP 403 Forbidden")
    void ipOutsideAllowedIps_getImport_returns403Forbidden() throws Exception {
        mockMvc.perform(get("/import")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.195");
                            return request;
                        }))
                .andExpect(status().isForbidden());
    }
}
