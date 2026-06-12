package io.github.arun0009.idempotent.core.metrics;

import io.github.arun0009.idempotent.core.metrics.IdempotentMetrics.Outcome;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MicrometerIdempotentMetricsTest {
    private MeterRegistry registry;
    private MicrometerIdempotentMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new MicrometerIdempotentMetrics(registry);
    }

    @Test
    void recordOutcomeIncrementsCounterWithProcessAndOutcomeTags() {
        metrics.recordOutcome("orders", Outcome.HIT);
        metrics.recordOutcome("orders", Outcome.HIT);
        metrics.recordOutcome("orders", Outcome.NEW_SUCCESS);

        var hits = registry.find("idempotent.executions")
                .tags(Tags.of("process", "orders", "outcome", "hit"))
                .counter();
        var newSuccess = registry.find("idempotent.executions")
                .tags(Tags.of("process", "orders", "outcome", "new_success"))
                .counter();

        assertNotNull(hits);
        assertEquals(2.0, hits.count());
        assertNotNull(newSuccess);
        assertEquals(1.0, newSuccess.count());
    }

    @Test
    void recordOutcomeKeepsCountersSeparatePerProcess() {
        metrics.recordOutcome("orders", Outcome.HIT);
        metrics.recordOutcome("payments", Outcome.HIT);

        var orders = registry.find("idempotent.executions")
                .tags(Tags.of("process", "orders", "outcome", "hit"))
                .counter();
        var payments = registry.find("idempotent.executions")
                .tags(Tags.of("process", "payments", "outcome", "hit"))
                .counter();

        assertNotNull(orders);
        assertEquals(1.0, orders.count());
        assertNotNull(payments);
        assertEquals(1.0, payments.count());
    }

    @Test
    void recordOperationTimerUsesSuccessTagForSuccess() {
        metrics.recordOperation("orders", true, Duration.ofMillis(25));
        var timer = registry.find("idempotent.operation")
                .tags(Tags.of("process", "orders", "outcome", "success"))
                .timer();

        assertNotNull(timer);
        assertEquals(1, timer.count());
        assertEquals(25.0, timer.totalTime(MILLISECONDS), 1.0);
    }

    @Test
    void recordOperationTimerUsesFailureTagForFailure() {
        metrics.recordOperation("orders", false, Duration.ofMillis(10));

        var timer = registry.find("idempotent.operation")
                .tags(Tags.of("process", "orders", "outcome", "failure"))
                .timer();

        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    void allOutcomeEnumValuesProduceLowercaseTag() {
        for (var outcome : Outcome.values()) {
            metrics.recordOutcome("orders", outcome);

            var expectedTag = outcome.name().toLowerCase();
            var counter = registry.find("idempotent.executions")
                    .tags(Tags.of("process", "orders", "outcome", expectedTag))
                    .counter();

            assertNotNull(counter, "missing counter for outcome " + outcome);
            assertEquals(1.0, counter.count(), "wrong count for outcome " + outcome);
        }
    }
}
