package com.nihon.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import java.util.UUID;

/**
 * Interceptor kiểm tra và bảo vệ CSRF cho các phương thức thay đổi dữ liệu (POST, PUT, DELETE, PATCH).
 * Hỗ trợ cơ chế Double Submit Cookie kết hợp Session token:
 * - Khi không có phiên đăng nhập, token được phát hành qua cookie "CSRF-TOKEN" (SameSite=Lax, Path=/) và gắn vào Form view.
 * - Khi có session, token được đồng bộ an toàn qua HttpSession.
 * - Kiểm tra so sánh hằng số thời gian (Constant-time comparison) chống Timing Attacks.
 * - Tự động thiết lập các HTTP Security Headers chuẩn.
 */
@Component
public class CsrfInterceptor implements HandlerInterceptor {

    public static final String CSRF_COOKIE_NAME = "CSRF-TOKEN";
    public static final String CSRF_SESSION_ATTR = "CSRF_TOKEN";
    public static final String CSRF_PARAM_NAME = "_csrf";
    public static final String CSRF_HEADER_NAME = "X-CSRF-TOKEN";

    private static final Logger log = LoggerFactory.getLogger(CsrfInterceptor.class);
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. Thiết lập Security Headers bảo vệ ứng dụng
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        if (request.isSecure()) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }

        // 2. Lấy CSRF token từ Cookie hoặc Session
        String cookieToken = extractCookieToken(request);
        HttpSession existingSession = request.getSession(false);
        String sessionToken = (existingSession != null) ? (String) existingSession.getAttribute(CSRF_SESSION_ATTR) : null;

        String activeToken = null;
        if (cookieToken != null && !cookieToken.isBlank()) {
            activeToken = cookieToken.trim();
        } else if (sessionToken != null && !sessionToken.isBlank()) {
            activeToken = sessionToken.trim();
        }

        // Nếu chưa có token, phát sinh token ngẫu nhiên mới
        if (activeToken == null) {
            activeToken = UUID.randomUUID().toString();
        }

        // Đảm bảo Cookie CSRF-TOKEN luôn được thiết lập cho client
        if (cookieToken == null || !cookieToken.equals(activeToken)) {
            ResponseCookie csrfCookie = ResponseCookie.from(CSRF_COOKIE_NAME, activeToken)
                    .path("/")
                    .sameSite("Lax")
                    .httpOnly(false)
                    .secure(request.isSecure())
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie.toString());
        }

        // Đồng bộ vào request attribute để render view
        request.setAttribute(CSRF_PARAM_NAME, activeToken);

        // Đồng bộ vào Session nếu session đang tồn tại
        if (existingSession != null) {
            existingSession.setAttribute(CSRF_SESSION_ATTR, activeToken);
        }

        // 3. Kiểm tra token đối với các phương thức thay đổi dữ liệu (POST, PUT, DELETE, PATCH)
        if (!SAFE_METHODS.contains(request.getMethod().toUpperCase())) {
            String requestToken = request.getParameter(CSRF_PARAM_NAME);
            if (requestToken == null || requestToken.isBlank()) {
                requestToken = request.getHeader(CSRF_HEADER_NAME);
            }

            // Expected token lấy từ Cookie hoặc Session
            String expectedToken = (cookieToken != null && !cookieToken.isBlank()) ? cookieToken : sessionToken;

            boolean isValid = false;
            if (requestToken != null && expectedToken != null) {
                isValid = MessageDigest.isEqual(
                        requestToken.trim().getBytes(StandardCharsets.UTF_8),
                        expectedToken.trim().getBytes(StandardCharsets.UTF_8)
                );
            }

            if (!isValid) {
                String safeMethod = com.nihon.service.excel.ExcelCellUtils.sanitizeForLog(request.getMethod());
                String safeUri = com.nihon.service.excel.ExcelCellUtils.sanitizeForLog(request.getRequestURI());
                log.warn("CSRF token validation failed for method [{}] path [{}]", safeMethod, safeUri);
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
                Object tokenObj = request.getAttribute(CSRF_PARAM_NAME);
                if (tokenObj != null) {
                    modelAndView.addObject(CSRF_PARAM_NAME, tokenObj.toString());
                } else {
                    String cookieToken = extractCookieToken(request);
                    if (cookieToken != null) {
                        modelAndView.addObject(CSRF_PARAM_NAME, cookieToken);
                    }
                }
            }
        }
    }

    private String extractCookieToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (CSRF_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
