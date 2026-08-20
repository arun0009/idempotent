package io.github.arun0009.idempotent.micrometer;

import io.github.arun0009.idempotent.core.metrics.IdempotentMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Locale;

/**
 * Micrometer-backed {@link IdempotentMetrics}. This is the only class in the module that
 * references Micrometer types, so users who don't depend on Micrometer can use the rest of
 * the library without {@code NoClassDefFoundError}.
 */
public final class MicrometerIdempotentMetrics implements IdempotentMetrics {
    private final MeterRegistry registry;

    public MicrometerIdempotentMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void record(String process, Outcome outcome, @Nullable Duration elapsed) {
        incCounter("idempotent.executions", Tags.of("process", process, "outcome", tag(outcome)));
        if (elapsed != null) {
            var operationOutcome = outcome == Outcome.NEW_SUCCESS ? "success" : "failure";
            var tags = Tags.of("process", process, "outcome", operationOutcome);
            registry.timer("idempotent.operations", tags).record(elapsed);
        }
    }

    @Override
    public void recordConflict(String process) {
        incCounter("idempotent.conflicts", Tags.of("process", process));
    }

    private void incCounter(String name, Tags process) {
        registry.counter(name, process).increment();
    }

    private static String tag(Outcome outcome) {
        return outcome.name().toLowerCase(Locale.ROOT);
    }
}
