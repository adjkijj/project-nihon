package com.nihon.history;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/**
 * Cấp và đọc cookie ẩn danh {@code nihon_learner} (UUID ngẫu nhiên). Không lưu tên, email, IP.
 * Cookie sai định dạng sẽ bị thay bằng cookie mới. Bỏ qua tài nguyên tĩnh.
 */
@Component
public class LearnerIdentityFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "nihon_learner";
    public static final Duration MAX_AGE = Duration.ofDays(365);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && path.startsWith(ctx)) {
            path = path.substring(ctx.length());
        }
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/kanjivg/")
                || path.startsWith("/images/") || path.startsWith("/fonts/") || path.startsWith("/webjars/")
                || path.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String valid = null;
        boolean hadCookie = false;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (COOKIE_NAME.equals(c.getName())) {
                    hadCookie = true;
                    valid = LearnerContext.normalizeUuid(c.getValue());
                    break;
                }
            }
        }
        if (valid == null) {
            String newId = UUID.randomUUID().toString();
            ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, newId)
                    .httpOnly(true)
                    .secure(request.isSecure())
                    .sameSite("Lax")
                    .path("/")
                    .maxAge(MAX_AGE)
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
            valid = newId;
        }
        LearnerContext.set(valid);
        try {
            chain.doFilter(request, response);
        } finally {
            LearnerContext.clear();
        }
    }
}