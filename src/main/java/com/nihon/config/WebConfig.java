package com.nihon.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cấu hình Web MVC: Đăng ký Interceptor bảo vệ CSRF và các tài nguyên giao diện.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CsrfInterceptor csrfInterceptor;
    private final RoleAuthInterceptor roleAuthInterceptor;

    public WebConfig(CsrfInterceptor csrfInterceptor, RoleAuthInterceptor roleAuthInterceptor) {
        this.csrfInterceptor = csrfInterceptor;
        this.roleAuthInterceptor = roleAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. Kiểm tra phân quyền quản trị/giáo viên tại server cho tính năng import (HTTP 403)
        registry.addInterceptor(roleAuthInterceptor)
                .addPathPatterns("/import/**");

        // 2. Bảo vệ chống CSRF cho toàn bộ ứng dụng
        registry.addInterceptor(csrfInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/css/**", "/js/**", "/images/**", "/favicon.ico");
    }
}
