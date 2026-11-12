package com.nihon.history;

import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class HistoryConfig {

    @Bean
    public Clock historyClock() {
        return Clock.systemDefaultZone();
    }

    /** Bean Filter đã tự đăng ký; chỉ đặt thứ tự sớm (sau SecurityHeaders nếu có). */
    @Bean
    public FilterRegistrationBean<Filter> learnerIdentityFilterRegistration(LearnerIdentityFilter filter) {
        FilterRegistrationBean<Filter> reg = new FilterRegistrationBean<>(filter);
        reg.setOrder(10);
        return reg;
    }
}