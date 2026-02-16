package io.github.arun0009.idempotent.rds;

import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

@Configuration
@EnableScheduling
@ConditionalOnBean(DataSource.class)
@AutoConfigureAfter(
        name = {
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration"
        })
public class RdsConfig {

    @Bean
    @ConditionalOnMissingBean
    public IdempotentStore idempotentStore(
            JdbcTemplate jdbcTemplate, @Value("${idempotent.rds.table.name:idempotent}") String tableName) {
        return new RdsIdempotentStore(jdbcTemplate, tableName);
    }

    @Bean
    @ConditionalOnMissingBean
    public RdsCleanupTask rdsCleanupTask(
            JdbcTemplate jdbcTemplate,
            @Value("${idempotent.rds.table.name:idempotent}") String tableName,
            @Value("${idempotent.rds.cleanup.batch.size:1000}") int batchSize) {
        RdsDialect dialect = RdsDialect.detect(jdbcTemplate);
        return new RdsCleanupTask(jdbcTemplate, tableName, dialect, batchSize);
    }
}
