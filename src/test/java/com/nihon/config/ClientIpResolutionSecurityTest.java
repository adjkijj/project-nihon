package com.nihon.config;

import com.nihon.controller.GlobalExceptionHandler;
import com.nihon.controller.ImportController;
import com.nihon.service.excel.ImportService;
import com.nihon.service.excel.ImportSessionStorage;
import com.nihon.service.excel.RateLimiterService;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.RemoteIpValve;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử bảo mật cơ chế phân giải IP (ClientIpResolver) và chống giả mạo IP (SEC-002):
 * 1. ClientIpResolver sử dụng request.getRemoteAddr(), hoàn toàn bỏ qua tiêu đề X-Forwarded-For giả mạo từ client.
 * 2. Tomcat RemoteIpValve chỉ tin cậy và xử lý X-Forwarded-For khi kết nối đến từ internal-proxies.
 * 3. Chống giả mạo IP qua mặt kiểm soát mạng ImportSecurityInterceptor (trả HTTP 403 Forbidden).
 * 4. Chống giả mạo IP xoay vòng tiêu đề X-Forwarded-For để vô hiệu hóa RateLimiterService (trả HTTP 429 sau 10 lần).
 */
@WebMvcTest(ImportController.class)
@Import({WebConfig.class, CsrfInterceptor.class, ImportSecurityInterceptor.class, ClientIpResolver.class, ImportSessionStorage.class, RateLimiterService.class, GlobalExceptionHandler.class})
class ClientIpResolutionSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImportService importService;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private ImportSessionStorage sessionStorage;

    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        clientIpResolver = new ClientIpResolver();
        rateLimiterService.reset();
        sessionStorage.clear();
    }

    // =========================================================================
    // PHẦN 1: UNIT TEST CLIENT IP RESOLVER
    // =========================================================================

    @Test
    @DisplayName("1. ClientIpResolver: Gửi X-Forwarded-For: 127.0.0.1 từ client trực tiếp -> IP vẫn là remoteAddr thật")
    void resolveClientIp_ignoresXForwardedForFromDirectConnection() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.195");
        request.addHeader("X-Forwarded-For", "127.0.0.1");

        String resolvedIp = clientIpResolver.resolveClientIp(request);

        assertThat(resolvedIp).isEqualTo("203.0.113.195");
    }

    @Test
    @DisplayName("2. ClientIpResolver: Khi request đến từ proxy đã được phân giải an toàn -> lấy remoteAddr của proxy unwrapped")
    void resolveClientIp_usesPreResolvedRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // Giả lập sau khi RemoteIpValve của Tomcat đã phân giải an toàn và ghi vào remoteAddr:
        request.setRemoteAddr("198.51.100.25");
        request.addHeader("X-Forwarded-For", "198.51.100.25");

        String resolvedIp = clientIpResolver.resolveClientIp(request);

        assertThat(resolvedIp).isEqualTo("198.51.100.25");
    }

    @Test
    @DisplayName("3. ClientIpResolver: Request rỗng hoặc null -> trả về 'anonymous'")
    void resolveClientIp_nullOrBlank_returnsAnonymous() {
        assertThat(clientIpResolver.resolveClientIp(null)).isEqualTo("anonymous");

        MockHttpServletRequest emptyRequest = new MockHttpServletRequest();
        emptyRequest.setRemoteAddr("");
        assertThat(clientIpResolver.resolveClientIp(emptyRequest)).isEqualTo("anonymous");
    }

    // =========================================================================
    // PHẦN 2: KIỂM THỬ TOMCAT REMOTE IP VALVE & INTERNAL PROXIES
    // =========================================================================

    @Test
    @DisplayName("4. Tomcat RemoteIpValve: Kết nối ngoài internal-proxies gửi X-Forwarded-For: 127.0.0.1 -> không bao giờ ghi đè thành 127.0.0.1")
    void tomcatRemoteIpValve_outsideInternalProxies_doesNotOverrideRemoteAddr() throws Exception {
        RemoteIpValve valve = new RemoteIpValve();
        valve.setRemoteIpHeader("X-Forwarded-For");
        valve.setInternalProxies("10\\.0\\.0\\.1|192\\.168\\.1\\.1"); // Chỉ tin cậy 10.0.0.1 và 192.168.1.1
        valve.setNext(mock(Valve.class));

        Request tomcatRequest = mock(Request.class);
        Response tomcatResponse = mock(Response.class);
        org.apache.coyote.Request coyoteRequest = new org.apache.coyote.Request();
        when(tomcatRequest.getCoyoteRequest()).thenReturn(coyoteRequest);
        when(tomcatRequest.getConnector()).thenReturn(mock(org.apache.catalina.connector.Connector.class));

        // Kẻ tấn công ngoài Internet (203.0.113.195) cố tình mạo danh 127.0.0.1
        when(tomcatRequest.getRemoteAddr()).thenReturn("203.0.113.195");
        when(tomcatRequest.getHeaders("X-Forwarded-For")).thenReturn(Collections.enumeration(List.of("127.0.0.1")));
        when(tomcatRequest.getHeaders("x-forwarded-for")).thenReturn(Collections.enumeration(List.of("127.0.0.1")));

        valve.invoke(tomcatRequest, tomcatResponse);

        // Valve tuyệt đối không bao giờ thiết lập remoteAddr thành IP mạo danh 127.0.0.1
        verify(tomcatRequest, never()).setRemoteAddr("127.0.0.1");
    }

    @Test
    @DisplayName("5. Tomcat RemoteIpValve: Kết nối từ internal-proxies gửi X-Forwarded-For -> dùng IP client thật")
    void tomcatRemoteIpValve_fromInternalProxies_usesRealClientIp() throws Exception {
        RemoteIpValve valve = new RemoteIpValve();
        valve.setRemoteIpHeader("X-Forwarded-For");
        valve.setInternalProxies("10\\.0\\.0\\.1"); // Proxy tin cậy
        valve.setNext(mock(Valve.class));

        Request tomcatRequest = mock(Request.class);
        Response tomcatResponse = mock(Response.class);
        org.apache.coyote.Request coyoteRequest = new org.apache.coyote.Request();
        when(tomcatRequest.getCoyoteRequest()).thenReturn(coyoteRequest);
        when(tomcatRequest.getConnector()).thenReturn(mock(org.apache.catalina.connector.Connector.class));

        // Kết nối đến từ proxy nội bộ 10.0.0.1, chuyển tiếp client thật 198.51.100.50
        when(tomcatRequest.getRemoteAddr()).thenReturn("10.0.0.1");
        when(tomcatRequest.getHeaders("X-Forwarded-For")).thenReturn(Collections.enumeration(List.of("198.51.100.50")));
        when(tomcatRequest.getHeaders("x-forwarded-for")).thenReturn(Collections.enumeration(List.of("198.51.100.50")));

        valve.invoke(tomcatRequest, tomcatResponse);

        // Valve cập nhật remoteAddr thành client IP thật được unwrapped từ proxy tin cậy
        verify(tomcatRequest).setRemoteAddr("198.51.100.50");
    }

    @Test
    @DisplayName("6. Tomcat RemoteIpValve: internal-proxies rỗng (mặc định) -> không tin cậy bất kỳ proxy nào")
    void tomcatRemoteIpValve_emptyInternalProxies_trustsNoProxy() throws Exception {
        RemoteIpValve valve = new RemoteIpValve();
        valve.setRemoteIpHeader("X-Forwarded-For");
        valve.setInternalProxies(""); // Mặc định rỗng khi chưa cấu hình TOMCAT_INTERNAL_PROXIES
        valve.setNext(mock(Valve.class));

        Request tomcatRequest = mock(Request.class);
        Response tomcatResponse = mock(Response.class);
        org.apache.coyote.Request coyoteRequest = new org.apache.coyote.Request();
        when(tomcatRequest.getCoyoteRequest()).thenReturn(coyoteRequest);
        when(tomcatRequest.getConnector()).thenReturn(mock(org.apache.catalina.connector.Connector.class));

        when(tomcatRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(tomcatRequest.getHeaders("X-Forwarded-For")).thenReturn(Collections.enumeration(List.of("198.51.100.50")));
        when(tomcatRequest.getHeaders("x-forwarded-for")).thenReturn(Collections.enumeration(List.of("198.51.100.50")));

        valve.invoke(tomcatRequest, tomcatResponse);

        // Do internal-proxies rỗng, header X-Forwarded-For bị bỏ qua, không bao giờ gán thành 198.51.100.50
        verify(tomcatRequest, never()).setRemoteAddr("198.51.100.50");
    }

    // =========================================================================
    // PHẦN 3: KIỂM SOÁT AN NINH MẠNG (IMPORT SECURITY INTERCEPTOR)
    // =========================================================================

    @Test
    @DisplayName("7. Kẻ tấn công ngoài dải IP gửi X-Forwarded-For: 127.0.0.1 -> Bị từ chối HTTP 403 Forbidden")
    void untrustedIp_spoofingLocalhostHeader_deniedByImportSecurityInterceptor() throws Exception {
        mockMvc.perform(get("/import")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.195"); // Địa chỉ ngoài Internet
                            return request;
                        })
                        .header("X-Forwarded-For", "127.0.0.1")) // Cố tình giả mạo localhost
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // PHẦN 4: GIỚI HẠN TẦN SUẤT (RATE LIMITER SERVICE)
    // =========================================================================

    @Test
    @DisplayName("8. Giả mạo IP xoay vòng qua X-Forwarded-For KHÔNG vô hiệu hóa được RateLimiterService (trả 429 sau 10 lần)")
    void rotatingXForwardedFor_cannotBypassRateLimiter_blockedAfterLimit() throws Exception {
        when(importService.exportToExcel()).thenReturn(new byte[]{0x50, 0x4B, 0x03, 0x04});

        // Kẻ tấn công từ IP kết nối 127.0.0.1 gửi liên tục 10 yêu cầu với các header X-Forwarded-For giả mạo khác nhau
        for (int i = 1; i <= RateLimiterService.MAX_UPLOADS_PER_MINUTE; i++) {
            mockMvc.perform(get("/import/export")
                            .with(request -> {
                                request.setRemoteAddr("127.0.0.1"); // IP kết nối thực tế giữ nguyên
                                return request;
                            })
                            .header("X-Forwarded-For", "10.0.0." + i)) // Cố tình xoay vòng IP hòng né bộ đếm
                    .andExpect(status().isOk());
        }

        // Yêu cầu thứ 11 với IP giả mạo mới: Hệ thống vẫn nhận diện đúng IP thật 127.0.0.1 và chặn với HTTP 429
        mockMvc.perform(get("/import/export")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        })
                        .header("X-Forwarded-For", "10.0.0.99"))
                .andExpect(status().isTooManyRequests());
    }
}
