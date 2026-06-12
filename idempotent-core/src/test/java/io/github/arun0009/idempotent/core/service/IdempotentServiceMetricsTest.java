package io.github.arun0009.idempotent.core.service;

import io.github.arun0009.idempotent.core.exception.IdempotentKeyConflictException;
import io.github.arun0009.idempotent.core.exception.IdempotentWaitExhaustedException;
import io.github.arun0009.idempotent.core.metrics.IdempotentMetrics;
import io.github.arun0009.idempotent.core.metrics.IdempotentMetrics.Outcome;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.github.arun0009.idempotent.core.persistence.InMemoryIdempotentStore;
import io.github.arun0009.idempotent.core.retry.WaitStrategy;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static io.github.arun0009.idempotent.core.persistence.IdempotentStore.Status.COMPLETED;
import static io.github.arun0009.idempotent.core.persistence.IdempotentStore.Status.IN_PROGRESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdempotentServiceMetricsTest {

    private static final Duration TTL = Duration.ofMinutes(1);
    private static final String PROCESS = "orders";

    private RecordingMetrics metrics;
    private InMemoryIdempotentStore store;
    private IdempotentService service;

    @BeforeEach
    void setUp() {
        metrics = new RecordingMetrics();
        store = new InMemoryIdempotentStore();
        service = new IdempotentService(store, WaitStrategy.withDefaults(), metrics);
    }

    @Test
    void firstExecutionRecordsNewSuccessAndOperationTimer() {
        service.execute(key("k1"), () -> "v", TTL);

        assertEquals(List.of(new OutcomeCall(PROCESS, Outcome.NEW_SUCCESS)), metrics.outcomes);
        assertEquals(1, metrics.operations.size());
        assertEquals(PROCESS, metrics.operations.get(0).process());
        assertTrue(metrics.operations.get(0).success());
    }

    @Test
    void secondExecutionRecordsHit() {
        service.execute(key("k1"), () -> "v", TTL);
        metrics.clear();

        service.execute(key("k1"), () -> "ignored", TTL);

        assertEquals(List.of(new OutcomeCall(PROCESS, Outcome.HIT)), metrics.outcomes);
        assertEquals(List.of(), metrics.operations, "no operation timer on a cache hit");
    }

    @Test
    void operationFailureRecordsNewFailureAndFailureTimer() {
        var key = key("k1");
        assertThrows(
                IllegalStateException.class,
                () -> service.execute(
                        key,
                        () -> {
                            throw new IllegalStateException("boom");
                        },
                        TTL));

        assertEquals(List.of(new OutcomeCall(PROCESS, Outcome.NEW_FAILURE)), metrics.outcomes);
        assertEquals(1, metrics.operations.size());
        assertFalse(metrics.operations.get(0).success());
    }

    @Test
    void conflictThenCompletedEntryRecordsConflictThenHit() {
        var conflicting = new SimulatedConflictStore(store);
        var conflictService = new IdempotentService(conflicting, WaitStrategy.withDefaults(), metrics);

        var result = conflictService.execute(key("k1"), () -> "fresh", TTL);

        assertEquals("concurrent", result, "service should return the entry seeded by the concurrent writer");
        assertEquals(
                List.of(new OutcomeCall(PROCESS, Outcome.CONFLICT), new OutcomeCall(PROCESS, Outcome.HIT)),
                metrics.outcomes);
        assertEquals(List.of(), metrics.operations, "operation should not run after a conflict");
    }

    @Test
    void waitExhaustedRecordsWaitExhausted() {
        var fast = new WaitStrategy(1, Duration.ofMillis(1), 1);
        var waitService = new IdempotentService(store, fast, metrics);

        // Seed an in-progress entry that will never complete during the wait.
        var key = key("stuck");
        store.store(key, new IdempotentStore.Value(IN_PROGRESS, Instant.now().plus(TTL), null));

        assertThrows(IdempotentWaitExhaustedException.class, () -> waitService.execute(key, () -> "never", TTL));

        assertEquals(List.of(new OutcomeCall(PROCESS, Outcome.WAIT_EXHAUSTED)), metrics.outcomes);
    }

    private static IdempotentStore.IdempotentKey key(String k) {
        return new IdempotentStore.IdempotentKey(k, PROCESS);
    }

    private record OutcomeCall(String process, Outcome outcome) {
    }

    private record OperationCall(String process, boolean success, Duration elapsed) {
    }

    private static final class RecordingMetrics implements IdempotentMetrics {
        private final List<OutcomeCall> outcomes = new ArrayList<>();
        private final List<OperationCall> operations = new ArrayList<>();

        @Override
        public void recordOutcome(String process, Outcome outcome) {
            outcomes.add(new OutcomeCall(process, outcome));
        }

        @Override
        public void recordOperation(String process, boolean success, Duration elapsed) {
            operations.add(new OperationCall(process, success, elapsed));
        }

        void clear() {
            outcomes.clear();
            operations.clear();
        }
    }

    /**
     * Wraps a real store and simulates a race: the first {@code store()} call seeds a completed
     * entry into the underlying store (as if another process had inserted it concurrently) and
     * then throws a conflict, mirroring what a real backend would do.
     */
    private static final class SimulatedConflictStore implements IdempotentStore {
        private final IdempotentStore delegate;
        private boolean conflicted;

        SimulatedConflictStore(IdempotentStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public @Nullable Value loadValue(IdempotentKey key, Class<?> returnType) {
            return delegate.loadValue(key, returnType);
        }

        @Override
        public void store(IdempotentKey key, Value value) {
            if (!conflicted) {
                conflicted = true;
                delegate.store(key, new Value(COMPLETED, value.expiresAt(), "concurrent"));
                throw new IdempotentKeyConflictException("simulated conflict", key);
            }
            delegate.store(key, value);
        }

        @Override
        public void remove(IdempotentKey key) {
            delegate.remove(key);
        }

        @Override
        public void update(IdempotentKey key, Value value) {
            delegate.update(key, value);
        }
    }
}
