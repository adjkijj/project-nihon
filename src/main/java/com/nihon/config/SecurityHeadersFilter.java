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

/**
 * Filter bảo vệ ứng dụng với các tiêu chuẩn bảo mật HTTP Security Headers (OWASP & W3C):
 * <ul>
 *   <li>Content-Security-Policy: default-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'</li>
 *   <li>X-Content-Type-Options: nosniff</li>
 *   <li>Referrer-Policy: strict-origin-when-cross-origin</li>
 *   <li>Permissions-Policy: camera=(), microphone=(), geolocation=() (tắt camera, micro, vị trí)</li>
 *   <li>X-Frame-Options: DENY (chống Clickjacking)</li>
 *   <li>Cache-Control: no-store cho toàn bộ trang và kết quả tải lên/nhập học liệu (/import/**)</li>
 *   <li>Strict-Transport-Security: max-age=31536000; includeSubDomains (khi kết nối HTTPS)</li>
 *   <li>Chuyển hướng toàn bộ lưu lượng HTTP sang HTTPS với mã 301 Moved Permanently khi được kích hoạt</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SecurityHeadersFilter.class);

    public static final String CSP_VALUE = "default-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'";
    public static final String HSTS_VALUE = "max-age=31536000; includeSubDomains";
    public static final String PERMISSIONS_POLICY_VALUE = "camera=(), microphone=(), geolocation=()";

    @Value("${nihon.security.https-redirect.enabled:false}")
    private boolean httpsRedirectEnabled;

    @Value("${server.ssl.port:8443}")
    private int httpsPort;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        boolean isHttps = request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));

        // 1. Chuyển hướng toàn bộ HTTP sang HTTPS nếu tính năng chuyển hướng được bật
        if (httpsRedirectEnabled && !isHttps) {
            String serverName = request.getServerName();
            String uri = request.getRequestURI();
            String queryString = request.getQueryString();

            StringBuilder targetHttpsUrl = new StringBuilder("https://").append(serverName != null ? serverName : "localhost");
            if (httpsPort != 443 && httpsPort != 8443 && httpsPort > 0) {
                targetHttpsUrl.append(":").append(httpsPort);
            }
            if (uri != null) {
                targetHttpsUrl.append(uri);
            }
            if (queryString != null && !queryString.isBlank()) {
                targetHttpsUrl.append("?").append(queryString);
            }

            log.info("Chuyển hướng HTTP sang HTTPS: [{}] -> [{}]", request.getRequestURL(), targetHttpsUrl);
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

        // 8. Cache-Control: no-store cho trang upload và kết quả import (/import/**)
        String requestUri = request.getRequestURI();
        if (requestUri != null && (requestUri.equals("/import") || requestUri.startsWith("/import/"))) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
            response.setHeader("Pragma", "no-cache");
        }

        filterChain.doFilter(request, response);
    }
}
