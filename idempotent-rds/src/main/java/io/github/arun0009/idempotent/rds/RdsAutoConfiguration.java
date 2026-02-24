package io.github.arun0009.idempotent.rds;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.impl.DefaultTypeResolverBuilder;

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

    private static final Logger log = LoggerFactory.getLogger(RdsAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public IdempotentStore idempotentStore(
            JdbcTemplate jdbcTemplate, RdsIdempotentProperties properties, RdsJacksonJsonBuilderCustomizer customizer) {
        Assert.hasText(properties.tableName(), "idempotent.rds.table-name must not be blank");

        var jsonBuilder = JsonMapper.builder();
        customizer.customize(jsonBuilder);

        return new RdsIdempotentStore(jdbcTemplate, properties.tableName(), jsonBuilder.build());
    }

    @Bean
    @ConditionalOnMissingBean
    public RdsJacksonJsonBuilderCustomizer rdsJacksonJsonBuilderCustomizer() {
        return builder -> {
            log.warn("Using an unrestricted polymorphic type validator for RDS idempotent store. "
                    + "Without restrictions of the PolymorphicTypeValidator, deserialization is "
                    + "vulnerable to arbitrary code execution when reading from untrusted sources.");
            var ptv = BasicPolymorphicTypeValidator.builder()
                    .allowIfBaseType(Object.class)
                    .allowIfSubType((ctx, clazz) -> true)
                    .build();
            builder.polymorphicTypeValidator(ptv)
                    .setDefaultTyping(new DefaultTypeResolverBuilder(
                            ptv, DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY, JsonTypeInfo.Id.CLASS, "@class"));
        };
    }

    @Bean
    @ConditionalOnMissingBean
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
