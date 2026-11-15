package com.nihon.security;

import com.nihon.config.AdminAccessInterceptor;
import com.nihon.config.ClientIpResolver;
import com.nihon.config.CsrfInterceptor;
import com.nihon.config.WebConfig;
import com.nihon.controller.HomeController;
import com.nihon.controller.VocabularyController;
import com.nihon.service.TopicService;
import com.nihon.service.VocabularyService;
import com.nihon.service.excel.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử an ninh phân hệ Quản trị (SEC-001 - AdminAccessSecurityTest):
 * 1. Không có thông tin xác thực thì trả về HTTP 401 Unauthorized kèm header WWW-Authenticate.
 * 2. Sai mật khẩu thì trả về HTTP 401 Unauthorized.
 * 3. Nhập sai quá 5 lần/phút/IP thì trả về HTTP 429 Too Many Requests (chống Brute-force).
 * 4. Nhập đúng mật khẩu quản trị thì được phép truy cập (HTTP 200).
 * 5. Khi cờ app.admin.enabled=false thì trả về HTTP 404 Not Found cho mọi endpoint quản trị.
 * 6. Địa chỉ IP không nằm trong app.admin.allowed-cidrs bị từ chối với HTTP 403 Forbidden.
 * 7. Các trang học công khai của người học (/vocabulary, /kanji, /grammar, /topics, /verbs)
 *    truy cập bình thường không cần mật khẩu.
 */
class AdminAccessSecurityTest {

    private static final String TEST_ADMIN_PASSWORD = "SecretAdminPassword123!";
    private static final String ALLOWED_IPS = "127.0.0.1,::1,192.168.1.0/24";

    // =========================================================================
    // 1. UNIT TEST TRỰC TIẾP AdminAccessInterceptor
    // =========================================================================
    @Nested
    @DisplayName("Kiểm thử đơn vị AdminAccessInterceptor")
    class InterceptorUnitTests {

        private RateLimiterService rateLimiterService;
        private ClientIpResolver clientIpResolver;
        private AdminAccessInterceptor interceptor;

        @BeforeEach
        void setUp() {
            rateLimiterService = new RateLimiterService();
            clientIpResolver = new ClientIpResolver();
            interceptor = new AdminAccessInterceptor(
                    true,
                    ALLOWED_IPS,
                    clientIpResolver,
                    rateLimiterService,
                    TEST_ADMIN_PASSWORD
            );
        }

        @Test
        @DisplayName("1. Không có tiêu đề Authorization -> Trả 401 kèm WWW-Authenticate header")
        void missingAuthHeader_returns401WithWwwAuthenticate() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/vocabulary/new");
            request.setRemoteAddr("127.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();

            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isFalse();
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Basic realm=\"Nihon Admin\"");
        }

        @Test
        @DisplayName("2. Tiêu đề Authorization sai kiểu (không phải Basic) -> Trả 401")
        void nonBasicAuthHeader_returns401() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/exams/admin");
            request.setRemoteAddr("127.0.0.1");
            request.addHeader("Authorization", "Bearer some-token");
            MockHttpServletResponse response = new MockHttpServletResponse();

            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isFalse();
            assertThat(response.getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("3. Sai mật khẩu quản trị -> Trả 401 và ghi nhận lỗi vào RateLimiterService")
        void wrongPassword_returns401_andRecordsFailure() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/exams/admin");
            request.setRemoteAddr("127.0.0.1");
            String auth = "Basic " + Base64.getEncoder().encodeToString("admin:WrongPassword".getBytes(StandardCharsets.UTF_8));
            request.addHeader("Authorization", auth);
            MockHttpServletResponse response = new MockHttpServletResponse();

            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isFalse();
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Basic realm=\"Nihon Admin\"");
        }

        @Test
        @DisplayName("4. Sai mật khẩu 5 lần -> Lần thứ 6 bị chặn ngay với HTTP 429 Too Many Requests")
        void bruteForce_fiveFailedAttempts_sixthAttemptReturns429() throws Exception {
            String authWrong = "Basic " + Base64.getEncoder().encodeToString("admin:WrongPass".getBytes(StandardCharsets.UTF_8));

            for (int i = 1; i <= 5; i++) {
                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/exams/admin");
                request.setRemoteAddr("127.0.0.1");
                request.addHeader("Authorization", authWrong);
                MockHttpServletResponse response = new MockHttpServletResponse();

                boolean result = interceptor.preHandle(request, response, new Object());
                assertThat(result).isFalse();
                assertThat(response.getStatus()).isEqualTo(401);
            }

            // Lần thứ 6: Ngay cả khi đúng mật khẩu cũng bị chặn 429 do đã quá ngưỡng sai trong 1 phút
            String authCorrect = "Basic " + Base64.getEncoder().encodeToString(("admin:" + TEST_ADMIN_PASSWORD).getBytes(StandardCharsets.UTF_8));
            MockHttpServletRequest blockedRequest = new MockHttpServletRequest("GET", "/exams/admin");
            blockedRequest.setRemoteAddr("127.0.0.1");
            blockedRequest.addHeader("Authorization", authCorrect);
            MockHttpServletResponse blockedResponse = new MockHttpServletResponse();

            boolean blockedResult = interceptor.preHandle(blockedRequest, blockedResponse, new Object());

            assertThat(blockedResult).isFalse();
            assertThat(blockedResponse.getStatus()).isEqualTo(429);
            assertThat(blockedResponse.getContentAsString()).contains("Too Many Requests");
        }

        @Test
        @DisplayName("5. Đúng mật khẩu quản trị -> Cho phép truy cập (trả true)")
        void correctPassword_allowsAccess() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/exams/admin");
            request.setRemoteAddr("127.0.0.1");
            String auth = "Basic " + Base64.getEncoder().encodeToString(("admin:" + TEST_ADMIN_PASSWORD).getBytes(StandardCharsets.UTF_8));
            request.addHeader("Authorization", auth);
            MockHttpServletResponse response = new MockHttpServletResponse();

            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isTrue();
            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("6. Cờ app.admin.enabled=false -> Trả HTTP 404 Not Found")
        void adminDisabled_returns404() throws Exception {
            AdminAccessInterceptor disabledInterceptor = new AdminAccessInterceptor(
                    false,
                    ALLOWED_IPS,
                    clientIpResolver,
                    rateLimiterService,
                    TEST_ADMIN_PASSWORD
            );

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/exams/admin");
            request.setRemoteAddr("127.0.0.1");
            String auth = "Basic " + Base64.getEncoder().encodeToString(("admin:" + TEST_ADMIN_PASSWORD).getBytes(StandardCharsets.UTF_8));
            request.addHeader("Authorization", auth);
            MockHttpServletResponse response = new MockHttpServletResponse();

            boolean result = disabledInterceptor.preHandle(request, response, new Object());

            assertThat(result).isFalse();
            assertThat(response.getStatus()).isEqualTo(404);
        }

        @Test
        @DisplayName("7. IP ngoài danh sách allowed-cidrs -> Trả HTTP 403 Forbidden")
        void ipOutsideAllowedCidrs_returns403() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/exams/admin");
            request.setRemoteAddr("203.0.113.50"); // IP ngoài dải
            String auth = "Basic " + Base64.getEncoder().encodeToString(("admin:" + TEST_ADMIN_PASSWORD).getBytes(StandardCharsets.UTF_8));
            request.addHeader("Authorization", auth);
            MockHttpServletResponse response = new MockHttpServletResponse();

            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isFalse();
            assertThat(response.getStatus()).isEqualTo(403);
        }

        @Test
        @DisplayName("8. IP trong dải CIDR 192.168.1.0/24 -> Được phép xác thực")
        void ipInCidrRange_isAllowed() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/exams/admin");
            request.setRemoteAddr("192.168.1.42");
            String auth = "Basic " + Base64.getEncoder().encodeToString(("admin:" + TEST_ADMIN_PASSWORD).getBytes(StandardCharsets.UTF_8));
            request.addHeader("Authorization", auth);
            MockHttpServletResponse response = new MockHttpServletResponse();

            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("9. Profile prod nhưng thiếu APP_ADMIN_PASSWORD -> Trả 401 từ chối an toàn")
        void prodProfileWithoutPassword_rejectsWith401() throws Exception {
            AdminAccessInterceptor prodInterceptorWithoutPass = new AdminAccessInterceptor(
                    true,
                    ALLOWED_IPS,
                    clientIpResolver,
                    rateLimiterService,
                    null,
                    true // isProdProfile = true
            );

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/exams/admin");
            request.setRemoteAddr("127.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();

            boolean result = prodInterceptorWithoutPass.preHandle(request, response, new Object());

            assertThat(result).isFalse();
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }

    // =========================================================================
    // 2. INTEGRATION TEST VỚI MOCK MVC: KIỂM TRA BẢO VỆ ENDPOINTS
    // =========================================================================
    @Nested
    @WebMvcTest(controllers = {VocabularyController.class, HomeController.class})
    @Import({WebConfig.class, CsrfInterceptor.class, AdminAccessInterceptor.class, ClientIpResolver.class, RateLimiterService.class})
    @TestPropertySource(properties = {
            "app.admin.enabled=true",
            "APP_ADMIN_PASSWORD=" + TEST_ADMIN_PASSWORD
    })
    @DisplayName("Kiểm thử tích hợp Spring MockMvc cho Admin & Learner Routes")
    class MockMvcAdminSecurityTests {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private VocabularyService vocabularyService;

        @MockitoBean
        private TopicService topicService;

        private String validAuthHeader;
        private String wrongAuthHeader;

        @BeforeEach
        void setUp() {
            validAuthHeader = "Basic " + Base64.getEncoder().encodeToString(("admin:" + TEST_ADMIN_PASSWORD).getBytes(StandardCharsets.UTF_8));
            wrongAuthHeader = "Basic " + Base64.getEncoder().encodeToString("admin:WrongPass".getBytes(StandardCharsets.UTF_8));
            when(vocabularyService.search(any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(Collections.emptyList()));
            when(topicService.findAllOrdered()).thenReturn(Collections.emptyList());
        }

        @Test
        @DisplayName("Admin route (/vocabulary/new): Không xác thực -> HTTP 401")
        void adminEndpoint_noAuth_returns401() throws Exception {
            mockMvc.perform(get("/vocabulary/new"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", "Basic realm=\"Nihon Admin\""));
        }

        @Test
        @DisplayName("Admin route (/vocabulary/new): Sai mật khẩu -> HTTP 401")
        void adminEndpoint_wrongPassword_returns401() throws Exception {
            mockMvc.perform(get("/vocabulary/new")
                            .header("Authorization", wrongAuthHeader))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Admin route (/vocabulary/new): Đúng mật khẩu quản trị -> HTTP 200")
        void adminEndpoint_correctPassword_returns200() throws Exception {
            mockMvc.perform(get("/vocabulary/new")
                            .header("Authorization", validAuthHeader))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Learner route (/vocabulary): Học viên tra cứu từ vựng KHÔNG cần mật khẩu -> HTTP 200")
        void learnerEndpoint_vocabulary_accessibleWithoutAuth() throws Exception {
            mockMvc.perform(get("/vocabulary"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Learner route (/): Trang chủ học viên KHÔNG cần mật khẩu -> HTTP 200")
        void learnerEndpoint_home_accessibleWithoutAuth() throws Exception {
            mockMvc.perform(get("/"))
                    .andExpect(status().isOk());
        }
    }

    // =========================================================================
    // 3. INTEGRATION TEST KHI CỜ app.admin.enabled=false -> TRẢ 404
    // =========================================================================
    @Nested
    @WebMvcTest(controllers = {VocabularyController.class, HomeController.class})
    @Import({WebConfig.class, CsrfInterceptor.class, AdminAccessInterceptor.class, ClientIpResolver.class, RateLimiterService.class})
    @TestPropertySource(properties = {
            "app.admin.enabled=false",
            "APP_ADMIN_PASSWORD=" + TEST_ADMIN_PASSWORD
    })
    @DisplayName("Kiểm thử khi app.admin.enabled=false")
    class AdminDisabledTests {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private VocabularyService vocabularyService;

        @MockitoBean
        private TopicService topicService;

        @Test
        @DisplayName("Khi app.admin.enabled=false: Endpoint quản trị trả về HTTP 404")
        void adminEndpoint_whenAdminDisabled_returns404() throws Exception {
            String auth = "Basic " + Base64.getEncoder().encodeToString(("admin:" + TEST_ADMIN_PASSWORD).getBytes(StandardCharsets.UTF_8));
            mockMvc.perform(get("/vocabulary/new")
                            .header("Authorization", auth))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Khi app.admin.enabled=false: Trang người học vẫn mở bình thường -> HTTP 200")
        void learnerEndpoint_stillAccessible_whenAdminDisabled() throws Exception {
            when(vocabularyService.search(any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(Collections.emptyList()));
            when(topicService.findAllOrdered()).thenReturn(Collections.emptyList());
            mockMvc.perform(get("/vocabulary"))
                    .andExpect(status().isOk());
        }
    }
}
