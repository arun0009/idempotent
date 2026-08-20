package io.github.arun0009.idempotent.core.metrics;

import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * Hook for observing idempotent-execution outcomes. Implementations may forward to a metrics
 * backend (e.g. Micrometer). Core uses {@link #NOOP} when no implementation is wired, so the
 * service has no dependency on any metrics library.
 *
 * <p>Each execution records one terminal outcome. Contention is recorded separately because it is
 * an event on the way to a terminal outcome.
 */
public interface IdempotentMetrics {

    enum Outcome {
        HIT,
        HIT_AFTER_WAIT,
        NEW_SUCCESS,
        NEW_FAILURE,
        WAIT_EXHAUSTED
    }

    void record(String process, Outcome outcome, @Nullable Duration elapsed);

    default void recordConflict(String process) {}

    IdempotentMetrics NOOP = new Noop();

    final class Noop implements IdempotentMetrics {
        private Noop() {}

        @Override
        public void record(String process, Outcome outcome, @Nullable Duration elapsed) {
            // noop
        }
    }
}
