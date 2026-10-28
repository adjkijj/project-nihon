package com.nihon.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cấu hình Web MVC: Đăng ký Interceptor bảo vệ kiểm soát mạng tính năng Import và bảo vệ CSRF.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CsrfInterceptor csrfInterceptor;
    private final ImportSecurityInterceptor importSecurityInterceptor;

    public WebConfig(CsrfInterceptor csrfInterceptor, ImportSecurityInterceptor importSecurityInterceptor) {
        this.csrfInterceptor = csrfInterceptor;
        this.importSecurityInterceptor = importSecurityInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. Kiểm soát an ninh mạng cho tính năng import: kiểm tra enabled và dải IP được phép (allowed-ips)
        registry.addInterceptor(importSecurityInterceptor)
                .addPathPatterns("/import/**");

        // 2. Bảo vệ chống CSRF cho toàn bộ ứng dụng
        registry.addInterceptor(csrfInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/css/**", "/js/**", "/images/**", "/favicon.ico");
    }
}
