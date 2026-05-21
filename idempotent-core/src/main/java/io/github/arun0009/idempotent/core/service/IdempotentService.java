package io.github.arun0009.idempotent.core.service;

import io.github.arun0009.idempotent.core.exception.IdempotentException;
import io.github.arun0009.idempotent.core.exception.IdempotentKeyConflictException;
import io.github.arun0009.idempotent.core.exception.IdempotentWaitExhaustedException;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.github.arun0009.idempotent.core.retry.IdempotentCompletionAwaiter;
import io.github.arun0009.idempotent.core.retry.WaitStrategy;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

import static io.github.arun0009.idempotent.core.persistence.IdempotentStore.Status.COMPLETED;
import static io.github.arun0009.idempotent.core.persistence.IdempotentStore.Status.INPROGRESS;

/**
 * Service-based API for idempotent operations.
 * Provides programmatic access to idempotency functionality without requiring annotations.
 */
public class IdempotentService {
    private static final Logger log = LoggerFactory.getLogger(IdempotentService.class);
    private final IdempotentStore idempotentStore;
    private final IdempotentCompletionAwaiter completionAwaiter;

    public IdempotentService(IdempotentStore idempotentStore) {
        this(idempotentStore, WaitStrategy.withDefaults());
    }

    public IdempotentService(IdempotentStore idempotentStore, WaitStrategy waitStrategy) {
        this.idempotentStore = idempotentStore;
        this.completionAwaiter = new IdempotentCompletionAwaiter(idempotentStore, waitStrategy);
    }

    /**
     * Execute an operation idempotently using a string key with default process name.
     *
     * @param key       the idempotent key (must not be null)
     * @param operation the operation to execute (must not be null)
     * @param ttl       the time to live for the idempotent result (must not be null)
     * @param <T>       the return type of the operation
     * @return the result of the operation if completed, or {@code null} when no value should be cached
     * @throws NullPointerException if any parameter is null
     * @throws IdempotentException  if there is an error executing the operation
     * @throws IdempotentWaitExhaustedException if the wait times out
     */
    public <T> @Nullable T execute(String key, Supplier<T> operation, Duration ttl) {
        return execute(key, "default", operation, ttl);
    }

    /**
     * Execute an operation idempotently using a string key and process name.
     *
     * @param key         the idempotent key (must not be null)
     * @param processName the process name for namespacing (must not be null)
     * @param operation   the operation to execute (must not be null)
     * @param ttl         the time to live for the idempotent result (must not be null)
     * @param <T>         the return type of the operation
     * @return the result of the operation if completed, or {@code null} when no value should be cached
     * @throws NullPointerException if any parameter is null
     * @throws IdempotentException  if there is an error executing the operation
     * @throws IdempotentWaitExhaustedException if the wait times out
     */
    public <T> @Nullable T execute(String key, String processName, Supplier<T> operation, Duration ttl) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(processName, "processName cannot be null");
        Objects.requireNonNull(operation, "operation cannot be null");
        Objects.requireNonNull(ttl, "ttl cannot be null");

        var idempotentKey = new IdempotentStore.IdempotentKey(key, processName);
        return execute(idempotentKey, operation, ttl);
    }

    /**
     * Execute an operation idempotently using an IdempotentKey.
     *
     * @param idempotentKey the idempotent key (must not be null)
     * @param operation     the operation to execute (must not be null)
     * @param ttl           the time to live for the idempotent result (must not be null)
     * @param <T>           the return type
     * @return the result of the operation if completed, or {@code null} when no value should be cached
     * @throws NullPointerException if any parameter is null
     * @throws IdempotentException  if there is an error executing the operation
     * @throws IdempotentWaitExhaustedException if the wait times out
     */
    public <T> @Nullable T execute(IdempotentStore.IdempotentKey idempotentKey, Supplier<T> operation, Duration ttl) {
        Objects.requireNonNull(idempotentKey, "idempotentKey cannot be null");
        Objects.requireNonNull(operation, "operation cannot be null");
        Objects.requireNonNull(ttl, "ttl cannot be null");
        IdempotentStore.Value existingValue = idempotentStore.getValue(idempotentKey, Object.class);
        if (existingValue == null) {
            return handleNewOperation(idempotentKey, operation, ttl);
        }
        if (isExistingRequest(existingValue)) {
            return handleExistingOperation(idempotentKey, existingValue);
        }

        return handleNewOperation(idempotentKey, operation, ttl);
    }

    @SuppressWarnings("unchecked")
    private <T> @Nullable T handleExistingOperation(
            IdempotentStore.IdempotentKey idempotentKey, IdempotentStore.Value value) {
        if (value.status() == COMPLETED) {
            return (T) value.response();
        }

        if (value.status() == INPROGRESS) {
            value = completionAwaiter.wait(idempotentKey, value);
            if (value != null && value.status() == COMPLETED) {
                return (T) value.response();
            }
        }

        idempotentStore.remove(idempotentKey);
        throw new IdempotentWaitExhaustedException(
                "Operation wait exhausted in progress after multiple retries", idempotentKey);
    }

    private <T> @Nullable T handleNewOperation(
            IdempotentStore.IdempotentKey idempotentKey, Supplier<T> operation, Duration ttl) {
        try {
            long expirationTime = Instant.now().plus(ttl).toEpochMilli();
            var inProgressValue = new IdempotentStore.Value(INPROGRESS, expirationTime, null);
            idempotentStore.store(idempotentKey, inProgressValue);

            T result = operation.get();
            updateStoreWithResponse(idempotentKey, result, expirationTime);

            return result;
        } catch (IdempotentKeyConflictException e) {
            log.info("Idempotent key conflict detected for key: {}", idempotentKey.key());
            IdempotentStore.Value value = idempotentStore.getValue(idempotentKey, Object.class);
            if (value == null) {
                return handleNewOperation(idempotentKey, operation, ttl);
            }
            return handleExistingOperation(idempotentKey, value);
        } catch (Exception e) {
            idempotentStore.remove(idempotentKey);
            throw new IdempotentException("Error executing idempotent operation", e);
        }
    }

    private boolean isExistingRequest(IdempotentStore.Value value) {
        return Instant.now().isBefore(Instant.ofEpochMilli(value.expirationTimeInMilliSeconds()));
    }

    private void updateStoreWithResponse(
            IdempotentStore.IdempotentKey idempotentKey, @Nullable Object response, long expiryTimeInMilliseconds) {
        if (response instanceof ResponseEntity<?> responseEntity
                && !responseEntity.getStatusCode().is2xxSuccessful()) {
            idempotentStore.remove(idempotentKey);
        } else if (response != null) {
            idempotentStore.update(
                    idempotentKey, new IdempotentStore.Value(COMPLETED, expiryTimeInMilliseconds, response));
        } else {
            idempotentStore.remove(idempotentKey);
        }
    }
}
