package com.nihon.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import java.util.UUID;

/**
 * Interceptor kiểm tra và bảo vệ CSRF cho các phương thức thay đổi dữ liệu (POST, PUT, DELETE, PATCH).
 * Token được lưu an toàn trong HttpSession và nhúng vào Model view để render trong form.
 * Đồng thời tự động thiết lập các Security Headers chuẩn để chống Clickjacking và MIME-sniffing.
 */
@Component
public class CsrfInterceptor implements HandlerInterceptor {

    public static final String CSRF_SESSION_ATTR = "CSRF_TOKEN";
    public static final String CSRF_PARAM_NAME = "_csrf";
    public static final String CSRF_HEADER_NAME = "X-CSRF-TOKEN";

    private static final Logger log = LoggerFactory.getLogger(CsrfInterceptor.class);
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. Thiết lập Security Headers bảo vệ ứng dụng (OWASP A05:2021 Security Misconfiguration)
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // HSTS (HTTP Strict Transport Security): Chỉ gửi khi kết nối an toàn (HTTPS), không áp đặt lên local dev HTTP
        if (request.isSecure()) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }

        // 2. Khởi tạo / lấy CSRF Token trong Session
        HttpSession session = request.getSession(true);
        String sessionToken = (String) session.getAttribute(CSRF_SESSION_ATTR);
        if (sessionToken == null) {
            sessionToken = UUID.randomUUID().toString();
            session.setAttribute(CSRF_SESSION_ATTR, sessionToken);
        }

        request.setAttribute(CSRF_PARAM_NAME, sessionToken);

        // 3. Kiểm tra token với các phương thức thay đổi dữ liệu (OWASP A01:2021 Broken Access Control)
        if (!SAFE_METHODS.contains(request.getMethod().toUpperCase())) {
            String requestToken = request.getParameter(CSRF_PARAM_NAME);
            if (requestToken == null || requestToken.isEmpty()) {
                requestToken = request.getHeader(CSRF_HEADER_NAME);
            }

            boolean isValid = false;
            if (requestToken != null && sessionToken != null) {
                // So sánh chuỗi theo thời gian hằng số (Constant-time comparison) để chống Timing Attack
                isValid = MessageDigest.isEqual(
                        requestToken.getBytes(StandardCharsets.UTF_8),
                        sessionToken.getBytes(StandardCharsets.UTF_8)
                );
            }

            if (!isValid) {
                log.warn("CSRF token validation failed for method [{}] path [{}]", request.getMethod(), request.getRequestURI());
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid or missing CSRF token");
                return false;
            }
        }

        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
        if (modelAndView != null && modelAndView.hasView()) {
            String viewName = modelAndView.getViewName();
            boolean isRedirect = (viewName != null && viewName.startsWith("redirect:"))
                    || (modelAndView.getView() instanceof org.springframework.web.servlet.view.RedirectView);
            if (!isRedirect) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    String token = (String) session.getAttribute(CSRF_SESSION_ATTR);
                    if (token != null) {
                        modelAndView.addObject(CSRF_PARAM_NAME, token);
                    }
                }
            }
        }
    }
}
