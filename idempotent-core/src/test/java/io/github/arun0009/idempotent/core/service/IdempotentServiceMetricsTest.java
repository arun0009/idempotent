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
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static io.github.arun0009.idempotent.core.persistence.IdempotentStore.Status.COMPLETED;
import static io.github.arun0009.idempotent.core.persistence.IdempotentStore.Status.IN_PROGRESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        assertEquals(1, metrics.records.size());
        assertEquals(Outcome.NEW_SUCCESS, metrics.records.get(0).outcome());
        assertNotNull(metrics.records.get(0).elapsed());
    }

    @Test
    void secondExecutionRecordsHit() {
        service.execute(key("k1"), () -> "v", TTL);
        metrics.clear();
        service.execute(key("k1"), () -> "ignored", TTL);
        assertEquals(List.of(new RecordCall(PROCESS, Outcome.HIT, null)), metrics.records);
    }

    @Test
    void operationFailureRecordsNewFailureAndFailureTimer() {
        assertThrows(
                IllegalStateException.class,
                () -> service.execute(
                        key("k1"),
                        () -> {
                            throw new IllegalStateException("boom");
                        },
                        TTL));
        assertEquals(1, metrics.records.size());
        assertEquals(Outcome.NEW_FAILURE, metrics.records.get(0).outcome());
        assertNotNull(metrics.records.get(0).elapsed());
    }

    @Test
    void conflictThenCompletedEntryRecordsConflictAndHit() {
        var conflictService =
                new IdempotentService(new SimulatedConflictStore(store), WaitStrategy.withDefaults(), metrics);
        var result = conflictService.execute(key("k1"), () -> "fresh", TTL);
        assertEquals("concurrent", result);
        assertEquals(List.of(PROCESS), metrics.conflicts);
        assertEquals(List.of(new RecordCall(PROCESS, Outcome.HIT, null)), metrics.records);
    }

    @Test
    void waitExhaustedRecordsWaitExhausted() {
        var waitService = new IdempotentService(store, new WaitStrategy(1, Duration.ofMillis(1), 1), metrics);
        var key = key("stuck");
        store.store(key, new IdempotentStore.Value(IN_PROGRESS, Instant.now().plus(TTL), null));
        assertThrows(IdempotentWaitExhaustedException.class, () -> waitService.execute(key, () -> "never", TTL));
        assertEquals(List.of(new RecordCall(PROCESS, Outcome.WAIT_EXHAUSTED, null)), metrics.records);
    }

    @Test
    void non2xxResponseRecordsFailureAndCanBeRetried() {
        var key = key("failed-response");
        service.execute(key, () -> ResponseEntity.internalServerError().build(), TTL);
        assertEquals(Outcome.NEW_FAILURE, metrics.records.get(0).outcome());
        assertNotNull(metrics.records.get(0).elapsed());
        metrics.clear();
        service.execute(key, () -> ResponseEntity.ok("retried"), TTL);
        assertEquals(Outcome.NEW_SUCCESS, metrics.records.get(0).outcome());
    }

    private static IdempotentStore.IdempotentKey key(String key) {
        return new IdempotentStore.IdempotentKey(key, PROCESS);
    }

    private record RecordCall(
            String process, Outcome outcome, @Nullable Duration elapsed) {}

    private static final class RecordingMetrics implements IdempotentMetrics {
        private final List<RecordCall> records = new ArrayList<>();
        private final List<String> conflicts = new ArrayList<>();

        @Override
        public void record(String process, Outcome outcome, @Nullable Duration elapsed) {
            records.add(new RecordCall(process, outcome, elapsed));
        }

        @Override
        public void recordConflict(String process) {
            conflicts.add(process);
        }

        void clear() {
            records.clear();
            conflicts.clear();
        }
    }

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
