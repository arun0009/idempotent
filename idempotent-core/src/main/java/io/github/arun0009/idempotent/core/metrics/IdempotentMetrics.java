package io.github.arun0009.idempotent.core.metrics;

import java.time.Duration;

/**
 * Hook for observing idempotent-execution outcomes. Implementations may forward to a metrics
 * backend (e.g. Micrometer). Core uses {@link #NOOP} when no implementation is wired so the
 * service has no dependency on any metrics library.
 */
public interface IdempotentMetrics {

    enum Outcome {
        HIT,
        HIT_AFTER_WAIT,
        NEW_SUCCESS,
        NEW_FAILURE,
        CONFLICT,
        WAIT_EXHAUSTED
    }

    void recordOutcome(String process, Outcome outcome);

    void recordOperation(String process, boolean success, Duration elapsed);

    IdempotentMetrics NOOP = new Noop();

    final class Noop implements IdempotentMetrics {
        private Noop() {}

        @Override
        public void recordOutcome(String process, Outcome outcome) {
            // noop
        }

        @Override
        public void recordOperation(String process, boolean success, Duration elapsed) {
            // noop
        }
    }
}
