package io.github.arun0009.idempotent.rds;

import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.github.arun0009.idempotent.core.serialization.IdempotentJsonMapperCustomizer;
import io.github.arun0009.idempotent.core.serialization.IdempotentPayloadCodec;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.time.Duration;

@AutoConfiguration(
        afterName = {
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration"
        })
@ConditionalOnClass({DataSource.class, JdbcTemplate.class})
@ConditionalOnBean(DataSource.class)
@EnableConfigurationProperties(RdsIdempotentProperties.class)
public class RdsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IdempotentStore idempotentStore(
            JdbcTemplate jdbcTemplate,
            RdsIdempotentProperties properties,
            IdempotentPayloadCodec idempotentPayloadCodec) {
        Assert.hasText(properties.tableName(), "idempotent.rds.table-name must not be blank");

        return new RdsIdempotentStore(jdbcTemplate, properties.tableName(), idempotentPayloadCodec);
    }

    @Bean
    @ConditionalOnBean(RdsJacksonJsonBuilderCustomizer.class)
    IdempotentJsonMapperCustomizer rdsLegacyJacksonCustomizerAdapter(
            RdsJacksonJsonBuilderCustomizer rdsJacksonJsonBuilderCustomizer) {
        return rdsJacksonJsonBuilderCustomizer::customize;
    }

    @Bean
    @ConditionalOnMissingBean
    @SuppressWarnings("FutureReturnValueIgnored")
    @ConditionalOnProperty(prefix = "idempotent.rds.cleanup", name = "enabled", matchIfMissing = true)
    public RdsCleanupTask rdsCleanupTask(
            JdbcTemplate jdbcTemplate, RdsIdempotentProperties properties, TaskScheduler rdsCleanupTaskScheduler) {
        Assert.isTrue(properties.cleanup().batchSize() > 0, "idempotent.rds.cleanup.batch-size must be positive");
        Assert.isTrue(properties.cleanup().fixedDelay() > 0, "idempotent.rds.cleanup.fixed-delay must be positive");

        RdsDialect dialect = RdsDialect.detect(jdbcTemplate);
        var cleanupTask = new RdsCleanupTask(
                jdbcTemplate,
                properties.tableName(),
                dialect,
                properties.cleanup().batchSize());
        rdsCleanupTaskScheduler.scheduleWithFixedDelay(
                cleanupTask::cleanup, Duration.ofMillis(properties.cleanup().fixedDelay()));
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
