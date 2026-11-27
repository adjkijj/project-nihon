package com.nihon.repository;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FlywayMySqlTestcontainersIntegrationTest {

    @Test
    @DisplayName("Flyway migrations V1 đến V15 chạy thành công trên container MySQL thật qua Testcontainers")
    void testFlywayOnRealMySqlContainer() throws Exception {
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            dockerAvailable = false;
        }

        assumeTrue(dockerAvailable,
                "Docker daemon không khả dụng trên môi trường hiện tại. Kiểm thử với MySQL Testcontainers được bỏ qua (chưa kiểm chứng trên Docker).");

        try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.36")
                .withDatabaseName("nihon_db")
                .withUsername("nihon")
                .withPassword("secret123")) {
            mysql.start();

            Flyway flyway = Flyway.configure()
                    .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                    .locations("classpath:db/migration")
                    .load();

            MigrateResult result = flyway.migrate();
            assertThat(result.success).isTrue();
            assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(15);

            try (Connection conn = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SHOW TABLES")) {
                List<String> tables = new ArrayList<>();
                while (rs.next()) {
                    tables.add(rs.getString(1).toLowerCase());
                }
                assertThat(tables).contains(
                        "topic", "vocabulary", "kanji_entry", "verb",
                        "exam_paper", "exam_attempt", "grammar_topic", "study_session"
                );
            }
        }
    }
}

