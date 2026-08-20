package io.github.arun0009.idempotent.rds;

import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.github.arun0009.idempotent.core.serialization.IdempotentPayloadCodec;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;

import javax.sql.DataSource;

@AutoConfiguration(
        afterName = {
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration"
        })
@ConditionalOnClass({
    DataSource.class,
    JdbcTemplate.class,
    DatabaseInitializationMode.class,
    EmbeddedDatabaseConnection.class
})
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "idempotent.rds", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(RdsIdempotentProperties.class)
public class RdsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @DependsOnDatabaseInitialization
    public RdsSchemaInitializer rdsSchemaInitializer(
            DataSource dataSource, JdbcTemplate jdbcTemplate, RdsIdempotentProperties properties) {
        return new RdsSchemaInitializer(
                dataSource, jdbcTemplate, properties.tableName(), properties.initializeSchema());
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotentStore idempotentStore(
            JdbcTemplate jdbcTemplate,
            RdsIdempotentProperties properties,
            IdempotentPayloadCodec idempotentPayloadCodec) {
        return new RdsIdempotentStore(jdbcTemplate, properties.tableName(), idempotentPayloadCodec);
    }

    @Bean
    @ConditionalOnMissingBean
    @SuppressWarnings("FutureReturnValueIgnored")
    @ConditionalOnProperty(prefix = "idempotent.rds.cleanup", name = "enabled", matchIfMissing = true)
    public RdsCleanupTask rdsCleanupTask(
            JdbcTemplate jdbcTemplate, RdsIdempotentProperties properties, TaskScheduler rdsCleanupTaskScheduler) {
        RdsDialect dialect = RdsDialect.detect(jdbcTemplate);
        var cleanupTask = new RdsCleanupTask(
                jdbcTemplate,
                properties.tableName(),
                dialect,
                properties.cleanup().batchSize());
        rdsCleanupTaskScheduler.scheduleWithFixedDelay(
                cleanupTask::cleanup, properties.cleanup().fixedDelay());
        return cleanupTask;
    }

    @Bean
    @ConditionalOnMissingBean(name = "rdsCleanupTaskScheduler")
    @ConditionalOnProperty(prefix = "idempotent.rds.cleanup", name = "enabled", matchIfMissing = true)
    public TaskScheduler rdsCleanupTaskScheduler() {
        var scheduler = new SimpleAsyncTaskScheduler();
        scheduler.setThreadNamePrefix("idempotent-rds-cleanup-");
        return scheduler;
    }
}
