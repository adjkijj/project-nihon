package com.nihon.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cấu hình Web MVC: Đăng ký các Interceptor bảo mật hệ thống:
 * 1. AdminAccessInterceptor: Kiểm soát truy cập các endpoint quản trị và thao tác thay đổi học liệu.
 * 2. ImportSecurityInterceptor: Kiểm soát an ninh mạng cho tính năng Import học liệu.
 * 3. CsrfInterceptor: Chống tấn công giả mạo yêu cầu chéo trang (CSRF).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CsrfInterceptor csrfInterceptor;
    private final ImportSecurityInterceptor importSecurityInterceptor;
    private final AdminAccessInterceptor adminAccessInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    public WebConfig(
            CsrfInterceptor csrfInterceptor,
            @Autowired(required = false) ImportSecurityInterceptor importSecurityInterceptor,
            @Autowired(required = false) AdminAccessInterceptor adminAccessInterceptor,
            @Autowired(required = false) RateLimitInterceptor rateLimitInterceptor) {
        this.csrfInterceptor = csrfInterceptor;
        this.importSecurityInterceptor = importSecurityInterceptor;
        this.adminAccessInterceptor = adminAccessInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. Kiểm soát quyền quản trị: HTTP Basic, IP/CIDR whitelist, Rate limiting và cờ bật/tắt
        if (adminAccessInterceptor != null) {
            registry.addInterceptor(adminAccessInterceptor)
                    .addPathPatterns(
                            "/exams/admin",
                            "/exams/admin/**",
                            "/grammar/admin",
                            "/grammar/admin/**",
                            "/import",
                            "/import/**",
                            "/vocabulary/new",
                            "/vocabulary/*/edit",
                            "/vocabulary/*/delete",
                            "/vocabulary/bulk-delete",
                            "/vocabulary/move-topic",
                            "/kanji/new",
                            "/kanji/*/edit",
                            "/kanji/*/delete",
                            "/topics/new",
                            "/topics/*/edit",
                            "/topics/*/delete",
                            "/topics/*/move",
                            "/topics/*/move-content",
                            "/verbs/new",
                            "/verbs/*/edit",
                            "/verbs/*/delete"
                    );
        }

        // 2. Kiểm soát an ninh mạng cho tính năng import: kiểm tra enabled và dải IP được phép (allowed-ips)
        if (importSecurityInterceptor != null) {
            registry.addInterceptor(importSecurityInterceptor)
                    .addPathPatterns("/import", "/import/**", "/exams/admin/import/**", "/grammar/admin/import/**");
        }

        // 3. Giới hạn tần suất thao tác của người học (Rate Limiting)
        if (rateLimitInterceptor != null) {
            registry.addInterceptor(rateLimitInterceptor)
                    .addPathPatterns(
                            "/exams/*/start",
                            "/exams/attempt/*/answer",
                            "/exams/attempt/*/finish",
                            "/grammar/*/quiz/check",
                            "/history/delete-all",
                            "/history/exams/delete-all",
                            "/history/exams/*/delete"
                    );
        }

        // 4. Bảo vệ chống CSRF cho toàn bộ ứng dụng
        registry.addInterceptor(csrfInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/css/**", "/js/**", "/images/**", "/favicon.ico");
    }
}
