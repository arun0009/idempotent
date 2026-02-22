package io.github.arun0009.idempotent.rds;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.impl.DefaultTypeResolverBuilder;

import javax.sql.DataSource;
import java.time.Duration;

@Configuration
@ConditionalOnBean(DataSource.class)
@EnableConfigurationProperties(RdsIdempotentProperties.class)
@AutoConfigureAfter(
        name = {
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration"
        })
public class RdsConfig {

    private static final Logger log = LoggerFactory.getLogger(RdsConfig.class);

    @Bean
    @ConditionalOnMissingBean
    public JsonMapper rdsJsonMapper() {
        log.warn("Using an unrestricted polymorphic type validator for RDS idempotent store. "
                + "Without restrictions of the PolymorphicTypeValidator, deserialization is "
                + "vulnerable to arbitrary code execution when reading from untrusted sources.");
        BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .allowIfSubType((ctx, clazz) -> true)
                .build();
        return JsonMapper.builder()
                .polymorphicTypeValidator(ptv)
                .setDefaultTyping(new DefaultTypeResolverBuilder(
                        ptv, DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY, JsonTypeInfo.Id.CLASS, "@class"))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotentStore idempotentStore(
            JdbcTemplate jdbcTemplate, RdsIdempotentProperties properties, JsonMapper rdsJsonMapper) {
        return new RdsIdempotentStore(jdbcTemplate, properties.getTableName(), rdsJsonMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "idempotent.rds.cleanup", name = "enabled", matchIfMissing = true)
    public RdsCleanupTask rdsCleanupTask(
            JdbcTemplate jdbcTemplate, RdsIdempotentProperties properties, TaskScheduler rdsCleanupTaskScheduler) {
        RdsDialect dialect = RdsDialect.detect(jdbcTemplate);
        RdsCleanupTask cleanupTask = new RdsCleanupTask(
                jdbcTemplate,
                properties.getTableName(),
                dialect,
                properties.getCleanup().getBatchSize());
        rdsCleanupTaskScheduler.scheduleWithFixedDelay(
                cleanupTask::cleanup, Duration.ofMillis(properties.getCleanup().getFixedDelay()));
        return cleanupTask;
    }

    @Bean
    @ConditionalOnMissingBean(name = "rdsCleanupTaskScheduler")
    @ConditionalOnProperty(prefix = "idempotent.rds.cleanup", name = "enabled", matchIfMissing = true)
    public TaskScheduler rdsCleanupTaskScheduler() {
        SimpleAsyncTaskScheduler scheduler = new SimpleAsyncTaskScheduler();
        scheduler.setThreadNamePrefix("idempotent-rds-cleanup-");
        return scheduler;
    }
}
