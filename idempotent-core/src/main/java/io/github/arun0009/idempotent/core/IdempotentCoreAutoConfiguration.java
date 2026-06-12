package io.github.arun0009.idempotent.core;

import io.github.arun0009.idempotent.core.aspect.IdempotentAspect;
import io.github.arun0009.idempotent.core.metrics.IdempotentMetrics;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.github.arun0009.idempotent.core.persistence.InMemoryIdempotentStore;
import io.github.arun0009.idempotent.core.retry.WaitStrategy;
import io.github.arun0009.idempotent.core.service.IdempotentService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@AutoConfiguration
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
@EnableConfigurationProperties(IdempotentProperties.class)
class IdempotentCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotentStore.class)
    IdempotentStore idempotentStore() {
        return new InMemoryIdempotentStore();
    }

    @Bean
    @ConditionalOnMissingBean(IdempotentMetrics.class)
    IdempotentMetrics idempotentMetrics() {
        return IdempotentMetrics.NOOP;
    }

    @Bean
    @ConditionalOnMissingBean(IdempotentService.class)
    IdempotentService idempotentService(
            IdempotentStore idempotentStore, IdempotentProperties properties, IdempotentMetrics metrics) {
        var inprogress = properties.inprogress();
        var waitStrategy = new WaitStrategy(
                inprogress.maxRetries(), inprogress.retryInitialInterval(), inprogress.retryMultiplier());
        return new IdempotentService(idempotentStore, waitStrategy, metrics);
    }

    /**
     * Registers the {@link IdempotentAspect} only when Spring AOP is on the classpath. Without
     * {@code spring-aop} (typically pulled in via {@code spring-boot-starter-aop}) the aspect
     * cannot be woven into method invocations, so registering it would create a silent
     * footgun. Users relying on the programmatic {@link IdempotentService} are unaffected.
     */
    @Bean
    @ConditionalOnMissingBean(IdempotentAspect.class)
    @ConditionalOnClass(name = "org.springframework.aop.Advisor")
    IdempotentAspect idempotentAspect(IdempotentService idempotentService, IdempotentProperties properties) {
        return new IdempotentAspect(idempotentService, properties);
    }
}
