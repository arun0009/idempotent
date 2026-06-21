package io.github.arun0009.idempotent.core.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

import java.time.Duration;

/**
 * Micrometer-backed {@link IdempotentMetrics}. This is the only class in the module that
 * references Micrometer types, so users who don't depend on Micrometer can use the rest of
 * the library without {@code NoClassDefFoundError}.
 */
public final class MicrometerIdempotentMetrics implements IdempotentMetrics {
    private static final String EXECUTIONS_METRIC = "idempotent.executions";
    private static final String OPERATION_METRIC = "idempotent.operation";
    private final MeterRegistry registry;

    public MicrometerIdempotentMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordOutcome(String process, Outcome outcome) {
        registry.counter(EXECUTIONS_METRIC, Tags.of("process", process, "outcome", tag(outcome)))
                .increment();
    }

    @Override
    public void recordOperation(String process, boolean success, Duration elapsed) {
        registry.timer(OPERATION_METRIC, Tags.of("process", process, "outcome", success ? "success" : "failure"))
                .record(elapsed);
    }

    private static String tag(Outcome outcome) {
        return outcome.name().toLowerCase();
    }
}
