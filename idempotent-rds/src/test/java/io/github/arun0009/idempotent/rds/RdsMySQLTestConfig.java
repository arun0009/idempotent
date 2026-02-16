package io.github.arun0009.idempotent.rds;

import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;

@Testcontainers
@Configuration
public class RdsMySQLTestConfig {

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
    public DataSource dataSource() {
        return DataSourceBuilder.create()
                .url(mysql.getJdbcUrl())
                .driverClassName(mysql.getDriverClassName())
                .username(mysql.getUsername())
                .password(mysql.getPassword())
                .build();
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public IdempotentStore idempotentStore(JdbcTemplate jdbcTemplate) {
        return new RdsIdempotentStore(jdbcTemplate, "idempotent");
    }

    @Bean
    public RdsCleanupTask rdsCleanupTask(JdbcTemplate jdbcTemplate) {
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
