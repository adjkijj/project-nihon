package com.nihon.security;

import com.nihon.config.SecurityHeadersFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CacheControlAndAllowedHostsSecurityTest {

    // =========================================================================
    // 1. Kiểm thử Cache-Control: no-store (SEC-011)
    // =========================================================================
    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @DisplayName("Kiểm thử Cache-Control: no-store trên /exams/**, /history/** và các trang quản trị")
    class CacheControlNoStoreTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private SecurityHeadersFilter securityHeadersFilter;

        @Test
        @DisplayName("1. /exams và /exams/** luôn chứa Cache-Control: no-store và Pragma: no-cache")
        void examsPages_containNoStore() throws Exception {
            // Trang danh sách bài thi
            mockMvc.perform(get("/exams"))
                    .andExpect(header().string("Cache-Control", containsString("no-store")))
                    .andExpect(header().string("Cache-Control", containsString("no-cache")))
                    .andExpect(header().string("Pragma", equalTo("no-cache")));

            // Chi tiết đề thi
            mockMvc.perform(get("/exams/non-existent-code"))
                    .andExpect(header().string("Cache-Control", containsString("no-store")))
                    .andExpect(header().string("Pragma", equalTo("no-cache")));

            // Phòng thi / phòng làm bài
            mockMvc.perform(get("/exams/attempt/00000000-0000-0000-0000-000000000000"))
                    .andExpect(header().string("Cache-Control", containsString("no-store")))
                    .andExpect(header().string("Pragma", equalTo("no-cache")));
        }

        @Test
        @DisplayName("2. /history và /history/** luôn chứa Cache-Control: no-store và Pragma: no-cache")
        void historyPages_containNoStore() throws Exception {
            // Trang lịch sử học tập
            mockMvc.perform(get("/history"))
                    .andExpect(header().string("Cache-Control", containsString("no-store")))
                    .andExpect(header().string("Pragma", equalTo("no-cache")));
        }

        @Test
        @DisplayName("3. Toàn bộ các trang và thao tác quản trị chứa Cache-Control: no-store")
        void adminPages_containNoStore() throws Exception {
            // Quản trị bài thi
            mockMvc.perform(get("/exams/admin"))
                    .andExpect(header().string("Cache-Control", containsString("no-store")))
                    .andExpect(header().string("Pragma", equalTo("no-cache")));

            // Quản trị ngữ pháp
            mockMvc.perform(get("/grammar/admin"))
                    .andExpect(header().string("Cache-Control", containsString("no-store")))
                    .andExpect(header().string("Pragma", equalTo("no-cache")));

            // Quản trị import
            mockMvc.perform(get("/import"))
                    .andExpect(header().string("Cache-Control", containsString("no-store")))
                    .andExpect(header().string("Pragma", equalTo("no-cache")));

            // Màn hình thêm mới từ vựng, Kanji, chủ đề, động từ
            mockMvc.perform(get("/vocabulary/new"))
                    .andExpect(header().string("Cache-Control", containsString("no-store")));

            mockMvc.perform(get("/kanji/new"))
                    .andExpect(header().string("Cache-Control", containsString("no-store")));

            mockMvc.perform(get("/topics/new"))
                    .andExpect(header().string("Cache-Control", containsString("no-store")));

            mockMvc.perform(get("/verbs/new"))
                    .andExpect(header().string("Cache-Control", containsString("no-store")));

            // Thao tác chỉnh sửa học liệu
            mockMvc.perform(get("/vocabulary/1/edit"))
                    .andExpect(header().string("Cache-Control", containsString("no-store")));

            mockMvc.perform(get("/kanji/1/edit"))
                    .andExpect(header().string("Cache-Control", containsString("no-store")));

            mockMvc.perform(get("/topics/1/edit"))
                    .andExpect(header().string("Cache-Control", containsString("no-store")));

            mockMvc.perform(get("/verbs/1/edit"))
                    .andExpect(header().string("Cache-Control", containsString("no-store")));
        }

        @Test
        @DisplayName("4. Các trang học công khai không bị áp đặt no-store vô lý")
        void publicLearningPages_doNotContainNoStore() throws Exception {
            mockMvc.perform(get("/"))
                    .andExpect(header().doesNotExist("Cache-Control"));

            mockMvc.perform(get("/vocabulary"))
                    .andExpect(header().doesNotExist("Cache-Control"));

            mockMvc.perform(get("/kanji"))
                    .andExpect(header().doesNotExist("Cache-Control"));

            mockMvc.perform(get("/grammar"))
                    .andExpect(header().doesNotExist("Cache-Control"));

            mockMvc.perform(get("/topics"))
                    .andExpect(header().doesNotExist("Cache-Control"));

            mockMvc.perform(get("/verbs"))
                    .andExpect(header().doesNotExist("Cache-Control"));
        }

        @Test
        @DisplayName("5. Kiểm thử đơn vị hàm shouldApplyNoStore với các URL đặc thù")
        void unitTestShouldApplyNoStore() {
            assertThat(securityHeadersFilter.shouldApplyNoStore(null)).isFalse();
            assertThat(securityHeadersFilter.shouldApplyNoStore("")).isFalse();
            assertThat(securityHeadersFilter.shouldApplyNoStore("/")).isFalse();
            assertThat(securityHeadersFilter.shouldApplyNoStore("/vocabulary")).isFalse();
            assertThat(securityHeadersFilter.shouldApplyNoStore("/kanji")).isFalse();

            // Exams
            assertThat(securityHeadersFilter.shouldApplyNoStore("/exams")).isTrue();
            assertThat(securityHeadersFilter.shouldApplyNoStore("/exams/")).isTrue();
            assertThat(securityHeadersFilter.shouldApplyNoStore("/exams/N5-01")).isTrue();
            assertThat(securityHeadersFilter.shouldApplyNoStore("/exams/attempt/abc/answer")).isTrue();
            assertThat(securityHeadersFilter.shouldApplyNoStore("/exams/admin")).isTrue();
            assertThat(securityHeadersFilter.shouldApplyNoStore("/exams/admin/new")).isTrue();

            // History
            assertThat(securityHeadersFilter.shouldApplyNoStore("/history")).isTrue();
            assertThat(securityHeadersFilter.shouldApplyNoStore("/history/")).isTrue();
            assertThat(securityHeadersFilter.shouldApplyNoStore("/history/delete-all")).isTrue();
            assertThat(securityHeadersFilter.shouldApplyNoStore("/history/exams/delete-all")).isTrue();

            // Admin
            assertThat(securityHeadersFilter.shouldApplyNoStore("/import")).isTrue();
            assertThat(securityHeadersFilter.shouldApplyNoStore("/import/preview")).isTrue();
            assertThat(securityHeadersFilter.shouldApplyNoStore("/grammar/admin")).isTrue();
            assertThat(securityHeadersFilter.shouldApplyNoStore("/grammar/admin/topics/new")).isTrue();
            assertThat(securityHeadersFilter.shouldApplyNoStore("/vocabulary/new")).isTrue();
            assertThat(securityHeadersFilter.shouldApplyNoStore("/vocabulary/123/edit")).isTrue();
            assertThat(securityHeadersFilter.shouldApplyNoStore("/vocabulary/123/delete")).isTrue();
            assertThat(securityHeadersFilter.shouldApplyNoStore("/vocabulary/bulk-delete")).isTrue();
            assertThat(securityHeadersFilter.shouldApplyNoStore("/vocabulary/move-topic")).isTrue();
            assertThat(securityHeadersFilter.shouldApplyNoStore("/kanji/new")).isTrue();
            assertThat(securityHeadersFilter.shouldApplyNoStore("/kanji/99/edit")).isTrue();
            assertThat(securityHeadersFilter.shouldApplyNoStore("/kanji/99/delete")).isTrue();
            assertThat(securityHeadersFilter.shouldApplyNoStore("/topics/new")).isTrue();
            assertThat(securityHeadersFilter.shouldApplyNoStore("/topics/1/edit")).isTrue();
            assertThat(securityHeadersFilter.shouldApplyNoStore("/topics/1/move")).isTrue();
            assertThat(securityHeadersFilter.shouldApplyNoStore("/topics/1/move-content")).isTrue();
            assertThat(securityHeadersFilter.shouldApplyNoStore("/verbs/new")).isTrue();
            assertThat(securityHeadersFilter.shouldApplyNoStore("/verbs/5/edit")).isTrue();
            assertThat(securityHeadersFilter.shouldApplyNoStore("/verbs/5/delete")).isTrue();
        }
    }

    // =========================================================================
    // 2. Kiểm thử Chuyển hướng HTTPS và Danh sách Host Cho phép (SEC-012)
    // =========================================================================
    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @TestPropertySource(properties = {
            "nihon.security.https-redirect.enabled=true",
            "server.ssl.port=8443",
            "app.allowed-hosts=nihon.edu.vn,localhost,127.0.0.1"
    })
    @DisplayName("Kiểm thử Chuyển hướng HTTPS với Danh sách Host Cho phép (app.allowed-hosts)")
    class HttpsRedirectAllowedHostsTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private SecurityHeadersFilter securityHeadersFilter;

        @Test
        @DisplayName("1. Host nằm trong danh sách cho phép (nihon.edu.vn) -> Chuyển hướng đến đúng host đó")
        void allowedHost_redirectsToThatHost() throws Exception {
            mockMvc.perform(get("/").header("Host", "nihon.edu.vn"))
                    .andExpect(status().isMovedPermanently())
                    .andExpect(header().string("Location", equalTo("https://nihon.edu.vn/")));
        }

        @Test
        @DisplayName("2. Host localhost nằm trong danh sách cho phép -> Chuyển hướng đến localhost")
        void allowedHostLocalhost_redirectsToLocalhost() throws Exception {
            mockMvc.perform(get("/exams").header("Host", "localhost"))
                    .andExpect(status().isMovedPermanently())
                    .andExpect(header().string("Location", equalTo("https://localhost/exams")));
        }

        @Test
        @DisplayName("3. Host chứa cổng hợp lệ (nihon.edu.vn:8080) -> Bóc tách cổng và chuyển hướng chính xác")
        void allowedHostWithPort_normalizesAndRedirects() throws Exception {
            mockMvc.perform(get("/topics").header("Host", "nihon.edu.vn:8080"))
                    .andExpect(status().isMovedPermanently())
                    .andExpect(header().string("Location", equalTo("https://nihon.edu.vn/topics")));
        }

        @Test
        @DisplayName("4. Tấn công Host Header Injection (Host: evil.com) -> Location KHÔNG chứa evil.com, fallback về host cho phép")
        void hostHeaderInjection_doesNotRedirectToAttackerHost() throws Exception {
            mockMvc.perform(get("/").header("Host", "evil.com"))
                    .andExpect(status().isMovedPermanently())
                    .andExpect(header().string("Location", not(containsString("evil.com"))))
                    .andExpect(header().string("Location", startsWith("https://nihon.edu.vn/")));
        }

        @Test
        @DisplayName("5. Tấn công Host Header Injection (Host: attacker-site.net:8080) kèm Query String")
        void hostHeaderInjectionWithQuery_neutralized() throws Exception {
            mockMvc.perform(get("/history?page=2").header("Host", "attacker-site.net:8080"))
                    .andExpect(status().isMovedPermanently())
                    .andExpect(header().string("Location", not(containsString("attacker-site.net"))))
                    .andExpect(header().string("Location", equalTo("https://nihon.edu.vn/history?page=2")));
        }

        @Test
        @DisplayName("6. Kiểm thử đơn vị resolveTargetHost với whitelist và giá trị bất thường")
        void unitTestResolveTargetHost() {
            // Khớp chính xác
            assertThat(securityHeadersFilter.resolveTargetHost("nihon.edu.vn")).isEqualTo("nihon.edu.vn");
            assertThat(securityHeadersFilter.resolveTargetHost("NIHON.EDU.VN")).isEqualTo("nihon.edu.vn");
            assertThat(securityHeadersFilter.resolveTargetHost("localhost")).isEqualTo("localhost");
            assertThat(securityHeadersFilter.resolveTargetHost("127.0.0.1")).isEqualTo("127.0.0.1");

            // Có cổng
            assertThat(securityHeadersFilter.resolveTargetHost("nihon.edu.vn:8080")).isEqualTo("nihon.edu.vn");
            assertThat(securityHeadersFilter.resolveTargetHost("localhost:80")).isEqualTo("localhost");

            // Host độc hại / không hợp lệ -> Fallback về host hợp lệ đầu tiên
            assertThat(securityHeadersFilter.resolveTargetHost("evil.com")).isEqualTo("nihon.edu.vn");
            assertThat(securityHeadersFilter.resolveTargetHost("attacker.io:9090")).isEqualTo("nihon.edu.vn");
            assertThat(securityHeadersFilter.resolveTargetHost(null)).isEqualTo("nihon.edu.vn");
            assertThat(securityHeadersFilter.resolveTargetHost("")).isEqualTo("nihon.edu.vn");
            assertThat(securityHeadersFilter.resolveTargetHost("   ")).isEqualTo("nihon.edu.vn");
        }
    }
}

