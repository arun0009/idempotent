package io.github.arun0009.idempotent.core.service;

import io.github.arun0009.idempotent.core.IdempotentKeyAuthorization;
import io.github.arun0009.idempotent.core.exception.IdempotentException;
import io.github.arun0009.idempotent.core.exception.IdempotentKeyConflictException;
import io.github.arun0009.idempotent.core.exception.IdempotentWaitExhaustedException;
import io.github.arun0009.idempotent.core.metrics.IdempotentMetrics;
import io.github.arun0009.idempotent.core.metrics.IdempotentMetrics.Outcome;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.github.arun0009.idempotent.core.retry.IdempotentCompletionAwaiter;
import io.github.arun0009.idempotent.core.retry.WaitStrategy;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import static io.github.arun0009.idempotent.core.persistence.IdempotentStore.Status.COMPLETED;
import static io.github.arun0009.idempotent.core.persistence.IdempotentStore.Status.IN_PROGRESS;

/**
 * Programmatic API for idempotent operations.
 *
 * <h2>Untyped vs typed overloads</h2>
 * The untyped {@link #execute(IdempotentStore.IdempotentKey, Supplier, Duration) execute} overloads
 * pass {@code Object.class} to the store and rely on the codec's polymorphic typing to reconstruct
 * the concrete response type. The typed overloads accept a {@link Class} hint and let stores that
 * support typed deserialization (RDS, DynamoDB) round-trip without relying on polymorphic
 * {@code @class} metadata. Prefer the typed overloads when the response type is known.
 *
 * <h2>Exception propagation</h2>
 * Domain exceptions thrown by the operation propagate to the caller as-is (no wrapping in
 * {@link IdempotentException}). Cleanup of the in-progress entry happens before the throw.
 *
 * <h2>Key ownership</h2>
 * An optional {@link IdempotentKeyAuthorization} binds each key to whoever claimed it. Its attributes are
 * captured when the key is claimed and verified on every read of an existing entry, before a cached
 * response is returned and before any in-progress wait. Defaults to {@link IdempotentKeyAuthorization#NOOP}.
 */
public class IdempotentService {

    private static final Logger log = LoggerFactory.getLogger(IdempotentService.class);

    private final IdempotentStore idempotentStore;
    private final IdempotentCompletionAwaiter completionAwaiter;
    private final IdempotentMetrics metrics;
    private final IdempotentKeyAuthorization keyGuard;

    public IdempotentService(IdempotentStore idempotentStore) {
        this(idempotentStore, WaitStrategy.withDefaults(), IdempotentMetrics.NOOP);
    }

    public IdempotentService(IdempotentStore idempotentStore, WaitStrategy waitStrategy) {
        this(idempotentStore, waitStrategy, IdempotentMetrics.NOOP);
    }

    public IdempotentService(IdempotentStore idempotentStore, WaitStrategy waitStrategy, IdempotentMetrics metrics) {
        this(idempotentStore, waitStrategy, metrics, IdempotentKeyAuthorization.NOOP);
    }

    public IdempotentService(
            IdempotentStore idempotentStore,
            WaitStrategy waitStrategy,
            IdempotentMetrics metrics,
            IdempotentKeyAuthorization keyGuard) {
        this.idempotentStore = idempotentStore;
        this.completionAwaiter = new IdempotentCompletionAwaiter(idempotentStore, waitStrategy);
        this.metrics = metrics;
        this.keyGuard = keyGuard;
    }

    // ---- Untyped Supplier-based overloads (use Object.class internally) -----------------------

    public <T> @Nullable T execute(String key, Supplier<T> operation, Duration ttl) {
        return execute(key, "default", operation, ttl);
    }

    public <T> @Nullable T execute(String key, String processName, Supplier<T> operation, Duration ttl) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(processName, "processName cannot be null");
        return execute(new IdempotentStore.IdempotentKey(key, processName), operation, ttl);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> @Nullable T execute(IdempotentStore.IdempotentKey idempotentKey, Supplier<T> operation, Duration ttl) {
        Class<T> objectType = (Class) Object.class;
        return execute(idempotentKey, objectType, operation, ttl);
    }

    // ---- Typed Supplier-based overloads ------------------------------------------------------

    public <T> @Nullable T execute(String key, Class<T> returnType, Supplier<T> operation, Duration ttl) {
        return execute(key, "default", returnType, operation, ttl);
    }

    public <T> @Nullable T execute(
            String key, String processName, Class<T> returnType, Supplier<T> operation, Duration ttl) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(processName, "processName cannot be null");
        return execute(new IdempotentStore.IdempotentKey(key, processName), returnType, operation, ttl);
    }

    public <T> @Nullable T execute(
            IdempotentStore.IdempotentKey idempotentKey, Class<T> returnType, Supplier<T> operation, Duration ttl) {
        Objects.requireNonNull(operation, "operation cannot be null");
        try {
            return executeThrowable(idempotentKey, returnType, operation::get, ttl);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            // Unreachable: Supplier.get() cannot throw checked Throwables.
            throw new IdempotentException("Unexpected exception", t);
        }
    }

    // ---- Canonical execution -----------------------------------------------------------------

    /**
     * Canonical idempotent execution that accepts an operation able to throw any {@link Throwable}.
     * Used by {@code IdempotentAspect} to forward {@code ProceedingJoinPoint::proceed} without
     * losing the original exception.
     */
    @SuppressWarnings("unchecked")
    public <T> @Nullable T executeThrowable(
            IdempotentStore.IdempotentKey idempotentKey,
            Class<T> returnType,
            IdempotentOperation<T> operation,
            Duration ttl)
            throws Throwable {
        Objects.requireNonNull(idempotentKey, "idempotentKey cannot be null");
        Objects.requireNonNull(returnType, "returnType cannot be null");
        Objects.requireNonNull(operation, "operation cannot be null");
        Objects.requireNonNull(ttl, "ttl cannot be null");

        IdempotentStore.Value existing = idempotentStore.getValue(idempotentKey, returnType);
        if (existing != null) {
            return (T) handleExisting(idempotentKey, existing);
        }
        return handleNew(idempotentKey, returnType, operation, ttl);
    }

    private @Nullable Object handleExisting(IdempotentStore.IdempotentKey idempotentKey, IdempotentStore.Value value) {
        // Fail fast before waiting
        keyGuard.authorize(idempotentKey, value.attributes());
        if (value.status() == COMPLETED) {
            metrics.record(idempotentKey.processName(), Outcome.HIT, null);
            return value.response();
        }
        IdempotentStore.Value awaited = completionAwaiter.wait(idempotentKey, value);
        if (awaited != null && awaited.status() == COMPLETED) {
            metrics.record(idempotentKey.processName(), Outcome.HIT_AFTER_WAIT, null);
            return awaited.response();
        }
        idempotentStore.remove(idempotentKey);
        metrics.record(idempotentKey.processName(), Outcome.WAIT_EXHAUSTED, null);
        throw new IdempotentWaitExhaustedException(
                "Operation wait exhausted in progress after multiple retries", idempotentKey);
    }

    private <T> @Nullable T handleNew(
            IdempotentStore.IdempotentKey idempotentKey,
            Class<T> returnType,
            IdempotentOperation<T> operation,
            Duration ttl)
            throws Throwable {
        var expiresAt = Instant.now().plus(ttl);
        var attributes = keyGuard.getClaimAttributes(idempotentKey);
        try {
            idempotentStore.store(idempotentKey, new IdempotentStore.Value(IN_PROGRESS, expiresAt, null, attributes));
        } catch (IdempotentKeyConflictException e) {
            log.info("Idempotent key conflict for {}; following existing-entry path", idempotentKey.key());
            metrics.recordConflict(idempotentKey.processName());
            IdempotentStore.Value refetched = idempotentStore.getValue(idempotentKey, returnType);
            if (refetched == null) {
                throw new IdempotentKeyConflictException(
                        "Idempotent key conflict but entry is not available", idempotentKey);
            }
            @SuppressWarnings("unchecked")
            T result = (T) handleExisting(idempotentKey, refetched);
            return result;
        }

        long startNanos = System.nanoTime();
        try {
            T result = operation.execute();
            var elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
            var successful = updateStoreWithResponse(idempotentKey, result, expiresAt, attributes);
            var outcome = successful ? Outcome.NEW_SUCCESS : Outcome.NEW_FAILURE;
            metrics.record(idempotentKey.processName(), outcome, elapsed);
            return result;
        } catch (Throwable t) {
            idempotentStore.remove(idempotentKey);
            metrics.record(
                    idempotentKey.processName(), Outcome.NEW_FAILURE, Duration.ofNanos(System.nanoTime() - startNanos));
            throw t;
        }
    }

    private boolean updateStoreWithResponse(
            IdempotentStore.IdempotentKey idempotentKey,
            @Nullable Object response,
            Instant expiresAt,
            Map<String, String> attributes) {
        if (response instanceof ResponseEntity<?> re && !re.getStatusCode().is2xxSuccessful()) {
            // Non-2xx responses are treated as failures and not cached so the caller can retry.
            idempotentStore.remove(idempotentKey);
            return false;
        }
        // Cache the result — including null (void methods or intentional null returns) so
        // later calls with the same key short-circuit instead of re-executing.
        idempotentStore.update(idempotentKey, new IdempotentStore.Value(COMPLETED, expiresAt, response, attributes));
        return true;
    }
}
