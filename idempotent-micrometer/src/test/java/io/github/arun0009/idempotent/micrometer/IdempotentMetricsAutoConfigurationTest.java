package io.github.arun0009.idempotent.micrometer;

import io.github.arun0009.idempotent.core.metrics.IdempotentMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class IdempotentMetricsAutoConfigurationTest {
    private static final Class<?> CORE_AUTO_CONFIGURATION;

    static {
        try {
            CORE_AUTO_CONFIGURATION =
                    Class.forName("io.github.arun0009.idempotent.core.IdempotentCoreAutoConfiguration");
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(
                    AutoConfigurations.of(IdempotentMicrometerAutoConfiguration.class, CORE_AUTO_CONFIGURATION));

    @Test
    void micrometerImplementationIsWiredWhenMeterRegistryPresent() {
        contextRunner
                .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> {
                    assertEquals(
                            1, context.getBeansOfType(IdempotentMetrics.class).size());
                    assertInstanceOf(MicrometerIdempotentMetrics.class, context.getBean(IdempotentMetrics.class));
                });
    }

    @Test
    void noopFallbackIsWiredWhenMeterRegistryAbsent() {
        contextRunner.run(context -> {
            assertEquals(1, context.getBeansOfType(IdempotentMetrics.class).size());
            assertSame(IdempotentMetrics.NOOP, context.getBean(IdempotentMetrics.class));
        });
    }
}
