package com.nihon.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử tích hợp toàn diện cho cấu hình HTTPS và HTTP Security Headers:
 * 1. Kiểm tra sự hiện diện đầy đủ của các headers bảo vệ (CSP, X-Content-Type-Options, Referrer-Policy, Permissions-Policy).
 * 2. Kiểm tra header HSTS chỉ xuất hiện trên kết nối an toàn (HTTPS).
 * 3. Kiểm tra header Cache-Control: no-store trên trang upload và import.
 * 4. Kiểm tra cơ chế chuyển hướng toàn bộ HTTP sang HTTPS (HTTP 301 Moved Permanently).
 * 5. Kiểm tra cơ chế nhận diện forwarded headers (X-Forwarded-Proto) từ Reverse Proxy.
 */
class HttpsSecurityIntegrationTest {

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @DisplayName("Kiểm thử Headers bảo vệ và HSTS trong môi trường tiêu chuẩn")
    class StandardSecurityHeadersTest {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("1. Mọi phản hồi đều chứa đầy đủ Content-Security-Policy chuẩn")
        void responseContainsContentSecurityPolicyHeader() throws Exception {
            mockMvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Security-Policy",
                            equalTo("default-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'")));
        }

        @Test
        @DisplayName("1. Mọi phản hồi đều chứa X-Content-Type-Options: nosniff")
        void responseContainsXContentTypeOptionsHeader() throws Exception {
            mockMvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Content-Type-Options", equalTo("nosniff")));
        }

        @Test
        @DisplayName("1. Mọi phản hồi đều chứa Referrer-Policy: strict-origin-when-cross-origin")
        void responseContainsReferrerPolicyHeader() throws Exception {
            mockMvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Referrer-Policy", equalTo("strict-origin-when-cross-origin")));
        }

        @Test
        @DisplayName("1. Mọi phản hồi đều chứa Permissions-Policy tắt camera, microphone, geolocation")
        void responseContainsPermissionsPolicyHeader() throws Exception {
            mockMvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Permissions-Policy",
                            equalTo("camera=(), microphone=(), geolocation=()")));
        }

        @Test
        @DisplayName("2. Kết nối HTTPS nhận header HSTS: max-age=31536000; includeSubDomains")
        void httpsRequest_receivesHstsHeader() throws Exception {
            mockMvc.perform(get("/").secure(true))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Strict-Transport-Security",
                            equalTo("max-age=31536000; includeSubDomains")));
        }

        @Test
        @DisplayName("2. Kết nối HTTP thuần không nhận header HSTS (tuân thủ RFC 6797)")
        void httpRequest_doesNotReceiveHstsHeader() throws Exception {
            mockMvc.perform(get("/").secure(false))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("Strict-Transport-Security"));
        }

        @Test
        @DisplayName("3. Trang /import chứa header Cache-Control: no-store")
        void importPage_containsNoStoreCacheControl() throws Exception {
            // Lưu ý: /import yêu cầu quyền truy cập hoặc trả về 403, nhưng SecurityHeadersFilter chạy trước nên header vẫn được thiết lập
            mockMvc.perform(get("/import"))
                    .andExpect(header().string("Cache-Control", containsString("no-store")))
                    .andExpect(header().string("Pragma", equalTo("no-cache")));
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @TestPropertySource(properties = {
            "nihon.security.https-redirect.enabled=true",
            "server.ssl.port=8443"
    })
    @DisplayName("Kiểm thử chuyển hướng HTTP sang HTTPS khi bật chuyển hướng bắt buộc")
    class HttpsRedirectEnabledTest {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("4. Yêu cầu HTTP không an toàn bị chuyển hướng 301 Moved Permanently sang HTTPS")
        void insecureHttpRequest_redirectsToHttps() throws Exception {
            mockMvc.perform(get("/").secure(false))
                    .andExpect(status().isMovedPermanently())
                    .andExpect(header().string("Location", startsWith("https://")));
        }

        @Test
        @DisplayName("4. Reverse Proxy gửi X-Forwarded-Proto: http bị chuyển hướng 301 sang HTTPS")
        void reverseProxyHttp_redirectsToHttps() throws Exception {
            mockMvc.perform(get("/").header("X-Forwarded-Proto", "http"))
                    .andExpect(status().isMovedPermanently())
                    .andExpect(header().string("Location", startsWith("https://")));
        }

        @Test
        @DisplayName("5. Reverse Proxy gửi X-Forwarded-Proto: https được tiếp nhận an toàn và có HSTS")
        void reverseProxyHttps_passesDirectlyWithHsts() throws Exception {
            mockMvc.perform(get("/").header("X-Forwarded-Proto", "https"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Strict-Transport-Security",
                            equalTo("max-age=31536000; includeSubDomains")));
        }
    }
}
