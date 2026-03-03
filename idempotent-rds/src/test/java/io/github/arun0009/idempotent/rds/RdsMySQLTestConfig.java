package io.github.arun0009.idempotent.rds;

import com.zaxxer.hikari.HikariDataSource;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;

@Testcontainers
@Configuration
class RdsMySQLTestConfig {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    static {
        mysql.start();
    }

    @Bean
    DataSource dataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(mysql.getJdbcUrl());
        ds.setDriverClassName(mysql.getDriverClassName());
        ds.setUsername(mysql.getUsername());
        ds.setPassword(mysql.getPassword());
        return ds;
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    IdempotentStore idempotentStore(JdbcTemplate jdbcTemplate) {
        return new RdsIdempotentStore(jdbcTemplate, "idempotent", JsonMapper.shared());
    }

    @Bean
    RdsCleanupTask rdsCleanupTask(JdbcTemplate jdbcTemplate) {
        RdsDialect dialect = RdsDialect.detect(jdbcTemplate);
        int batchSize = 1000; // Default batch size for tests
        return new RdsCleanupTask(jdbcTemplate, "idempotent", dialect, batchSize);
    }

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName);
    }
}
