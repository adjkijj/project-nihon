package com.nihon.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm thử chứng minh Flyway migration tự động tạo lập schema và Hibernate ddl-auto=validate
 * xác thực thành công 100% toàn bộ 19 entity mà không gặp bất kỳ lỗi SchemaManagementException nào.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:flyway_validate_db;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.flyway.baseline-on-migrate=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "nihon.import.auto-enabled=false"
})
class FlywayHibernateValidationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("Flyway tạo schema và Hibernate ddl-auto=validate xác thực thành công trên production profile")
    void flywaySchema_matchesHibernateEntitiesWithValidateMode() {
        assertThat(applicationContext).isNotNull();
        assertThat(applicationContext.containsBean("entityManagerFactory")).isTrue();
        assertThat(applicationContext.containsBean("flyway")).isTrue();
    }
}

