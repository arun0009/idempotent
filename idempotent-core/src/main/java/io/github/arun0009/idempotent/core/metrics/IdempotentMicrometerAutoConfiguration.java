package io.github.arun0009.idempotent.core.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers a Micrometer-backed {@link IdempotentMetrics} when both Micrometer is on the
 * classpath and a {@link MeterRegistry} bean is available. Ordered before
 * {@code IdempotentCoreAutoConfiguration} so its NOOP fallback only kicks in when Micrometer is
 * absent.
 */
@AutoConfiguration(beforeName = "io.github.arun0009.idempotent.core.IdempotentCoreAutoConfiguration")
@ConditionalOnClass(MeterRegistry.class)
class IdempotentMicrometerAutoConfiguration {

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean(IdempotentMetrics.class)
    IdempotentMetrics micrometerIdempotentMetrics(MeterRegistry meterRegistry) {
        return new MicrometerIdempotentMetrics(meterRegistry);
    }
}
