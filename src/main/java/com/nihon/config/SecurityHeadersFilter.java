package com.nihon.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Filter bảo vệ ứng dụng với các tiêu chuẩn bảo mật HTTP Security Headers (OWASP & W3C):
 * <ul>
 *   <li>Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'
 *       (CSS/JS nằm trong /css và /js, không dùng 'unsafe-inline' — template không được chứa style/script/onclick nhúng)</li>
 *   <li>X-Content-Type-Options: nosniff</li>
 *   <li>Referrer-Policy: strict-origin-when-cross-origin</li>
 *   <li>Permissions-Policy: camera=(), microphone=(), geolocation=() (tắt camera, micro, vị trí)</li>
 *   <li>X-Frame-Options: DENY (chống Clickjacking)</li>
 *   <li>Cache-Control: no-store cho /exams/**, /history/** và toàn bộ các trang/thao tác quản trị</li>
 *   <li>Strict-Transport-Security: max-age=31536000; includeSubDomains (khi kết nối HTTPS)</li>
 *   <li>Chuyển hướng HTTP sang HTTPS an toàn sử dụng whitelist tên miền app.allowed-hosts (phòng chống Host Header Injection)</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SecurityHeadersFilter.class);

    public static final String CSP_VALUE = "default-src 'self'; script-src 'self'; style-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'";
    public static final String HSTS_VALUE = "max-age=31536000; includeSubDomains";
    public static final String PERMISSIONS_POLICY_VALUE = "camera=(), microphone=(), geolocation=()";

    @Value("${nihon.security.https-redirect.enabled:false}")
    private boolean httpsRedirectEnabled;

    @Value("${server.ssl.port:8443}")
    private int httpsPort;

    @Value("${app.allowed-hosts:localhost,127.0.0.1,::1,[::1]}")
    private String allowedHostsConfig;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        boolean isHttps = request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));

        // 1. Chuyển hướng toàn bộ HTTP sang HTTPS nếu tính năng chuyển hướng được bật
        if (httpsRedirectEnabled && !isHttps) {
            String targetHost = resolveTargetHost(request.getServerName());
            String uri = request.getRequestURI();
            String queryString = request.getQueryString();

            StringBuilder targetHttpsUrl = new StringBuilder("https://").append(targetHost);
            if (httpsPort != 443 && httpsPort != 8443 && httpsPort > 0) {
                targetHttpsUrl.append(":").append(httpsPort);
            }
            if (uri != null) {
                targetHttpsUrl.append(uri);
            }
            if (queryString != null && !queryString.isBlank()) {
                targetHttpsUrl.append("?").append(queryString);
            }

            log.info("Chuyển hướng HTTP sang HTTPS: [{}] -> [{}] (host đích: {})", request.getRequestURL(), targetHttpsUrl, targetHost);
            response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY); // 301
            response.setHeader(HttpHeaders.LOCATION, targetHttpsUrl.toString());
            return;
        }

        // 2. HTTP Strict Transport Security (HSTS): Chỉ gửi qua kết nối HTTPS
        if (isHttps) {
            response.setHeader("Strict-Transport-Security", HSTS_VALUE);
        }

        // 3. Content Security Policy (CSP)
        response.setHeader("Content-Security-Policy", CSP_VALUE);

        // 4. Chống MIME-sniffing
        response.setHeader("X-Content-Type-Options", "nosniff");

        // 5. Kiểm soát thông tin nguồn gửi (Referrer-Policy)
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // 6. Permissions-Policy: Tắt camera, microphone, geolocation
        response.setHeader("Permissions-Policy", PERMISSIONS_POLICY_VALUE);

        // 7. X-Frame-Options: Chống tấn công Clickjacking
        response.setHeader("X-Frame-Options", "DENY");

        // 8. Cache-Control: no-store cho /exams/**, /history/** và các trang/thao tác quản trị
        String requestUri = request.getRequestURI();
        if (shouldApplyNoStore(requestUri)) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
            response.setHeader("Pragma", "no-cache");
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Xác định tên miền/host hợp lệ cho chuyển hướng HTTPS dựa trên danh sách trắng app.allowed-hosts.
     * Tuyệt đối không tin cậy trực tiếp request.getServerName() do client gửi lên.
     */
    public String resolveTargetHost(String requestedServerName) {
        List<String> allowed = getAllowedHostsList();
        if (allowed.isEmpty()) {
            return "localhost";
        }
        if (requestedServerName != null && !requestedServerName.isBlank()) {
            String normalized = requestedServerName.trim().toLowerCase();
            // Loại bỏ cổng nếu có dạng host:port (ngoại trừ IPv6 đóng mở ngoặc vuông)
            int colonIdx = normalized.lastIndexOf(':');
            if (colonIdx > 0 && !normalized.endsWith("]")) {
                String potentialPort = normalized.substring(colonIdx + 1);
                if (potentialPort.matches("\\d+")) {
                    normalized = normalized.substring(0, colonIdx);
                }
            }
            for (String allowedHost : allowed) {
                if (allowedHost.equalsIgnoreCase(normalized)) {
                    return allowedHost;
                }
            }
        }
        // Fallback: nếu host không nằm trong danh sách cho phép, sử dụng host hợp lệ đầu tiên
        return allowed.get(0);
    }

    private List<String> getAllowedHostsList() {
        if (allowedHostsConfig == null || allowedHostsConfig.isBlank()) {
            return List.of("localhost");
        }
        return Arrays.stream(allowedHostsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .toList();
    }

    /**
     * Thẩm định xem URL có phải là khu vực nhạy cảm cần cấm lưu cache trình duyệt hay không:
     * - /exams/**: Toàn bộ bài thi, kết quả, phòng thi và quản trị đề thi
     * - /history/**: Toàn bộ lịch sử học tập của người học
     * - Các trang quản trị: /import/**, /grammar/admin/**, và các thao tác tạo/sửa/xóa học liệu.
     */
    public boolean shouldApplyNoStore(String uri) {
        if (uri == null || uri.isBlank()) {
            return false;
        }

        String normalized = uri;
        if (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        // 1. Phân hệ bài thi (/exams/**)
        if (normalized.equals("/exams") || normalized.startsWith("/exams/")) {
            return true;
        }

        // 2. Lịch sử học tập (/history/**)
        if (normalized.equals("/history") || normalized.startsWith("/history/")) {
            return true;
        }

        // 3. Phân hệ nhập/xuất học liệu (/import/**)
        if (normalized.equals("/import") || normalized.startsWith("/import/")) {
            return true;
        }

        // 4. Phân hệ quản trị ngữ pháp (/grammar/admin/**)
        if (normalized.equals("/grammar/admin") || normalized.startsWith("/grammar/admin/")) {
            return true;
        }

        // 5. Các thao tác và trang quản trị học liệu khác (từ vựng, Kanji, chủ đề, động từ)
        if (normalized.equals("/vocabulary/new") || normalized.equals("/vocabulary/bulk-delete") || normalized.equals("/vocabulary/move-topic")
                || (normalized.startsWith("/vocabulary/") && (normalized.endsWith("/edit") || normalized.endsWith("/delete")))) {
            return true;
        }

        if (normalized.equals("/kanji/new")
                || (normalized.startsWith("/kanji/") && (normalized.endsWith("/edit") || normalized.endsWith("/delete")))) {
            return true;
        }

        if (normalized.equals("/topics/new")
                || (normalized.startsWith("/topics/") && (normalized.endsWith("/edit") || normalized.endsWith("/delete") || normalized.endsWith("/move") || normalized.endsWith("/move-content")))) {
            return true;
        }

        if (normalized.equals("/verbs/new")
                || (normalized.startsWith("/verbs/") && (normalized.endsWith("/edit") || normalized.endsWith("/delete")))) {
            return true;
        }

        return false;
    }

    public String getAllowedHostsConfig() {
        return allowedHostsConfig;
    }

    public void setAllowedHostsConfig(String allowedHostsConfig) {
        this.allowedHostsConfig = allowedHostsConfig;
    }

    public boolean isHttpsRedirectEnabled() {
        return httpsRedirectEnabled;
    }

    public void setHttpsRedirectEnabled(boolean httpsRedirectEnabled) {
        this.httpsRedirectEnabled = httpsRedirectEnabled;
    }

    public int getHttpsPort() {
        return httpsPort;
    }

    public void setHttpsPort(int httpsPort) {
        this.httpsPort = httpsPort;
    }
}
