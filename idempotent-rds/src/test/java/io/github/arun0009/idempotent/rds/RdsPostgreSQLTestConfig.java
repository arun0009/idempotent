package io.github.arun0009.idempotent.rds;

import com.zaxxer.hikari.HikariDataSource;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;

@Testcontainers
@Configuration
public class RdsPostgreSQLTestConfig {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    static {
        postgres.start();
    }

    @Bean
    public DataSource dataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(postgres.getJdbcUrl());
        ds.setDriverClassName(postgres.getDriverClassName());
        ds.setUsername(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        return ds;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public IdempotentStore idempotentStore(JdbcTemplate jdbcTemplate) {
        return new RdsIdempotentStore(jdbcTemplate, "idempotent", JsonMapper.shared());
    }

    @Bean
    public RdsCleanupTask rdsCleanupTask(JdbcTemplate jdbcTemplate) {
        RdsDialect dialect = RdsDialect.detect(jdbcTemplate);
        int batchSize = 1000; // Default batch size for tests
        return new RdsCleanupTask(jdbcTemplate, "idempotent", dialect, batchSize);
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }
}
