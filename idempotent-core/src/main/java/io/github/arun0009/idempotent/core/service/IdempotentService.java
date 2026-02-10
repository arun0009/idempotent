package io.github.arun0009.idempotent.core.service;

import io.github.arun0009.idempotent.core.exception.IdempotentException;
import io.github.arun0009.idempotent.core.exception.IdempotentKeyConflictException;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.github.arun0009.idempotent.core.retry.IdempotentCompletionAwaiter;
import io.github.arun0009.idempotent.core.retry.WaitStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Service-based API for idempotent operations.
 * Provides programmatic access to idempotency functionality without requiring annotations.
 */
public class IdempotentService {
    private static final Logger log = LoggerFactory.getLogger(IdempotentService.class);
    private final JsonMapper jsonMapper;
    private final IdempotentStore idempotentStore;
    private final IdempotentCompletionAwaiter completionAwaiter;

    public IdempotentService(IdempotentStore idempotentStore) {
        this(idempotentStore, WaitStrategy.withDefaults());
    }

    public IdempotentService(IdempotentStore idempotentStore, WaitStrategy retryArgs) {
        this.idempotentStore = idempotentStore;
        this.jsonMapper = JsonMapper.shared();
        this.completionAwaiter = new IdempotentCompletionAwaiter(idempotentStore, retryArgs);
    }

    /**
     * Execute an operation idempotently using a string key with default process name.
     *
     * @param key       the idempotent key (must not be null)
     * @param operation the operation to execute (must not be null)
     * @param ttl       the time to live for the idempotent result (must not be null)
     * @param <T>       the return type of the operation
     * @return the result of the operation
     * @throws NullPointerException if any parameter is null
     */
    public <T> T execute(String key, Supplier<T> operation, Duration ttl) {
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
     * @return the result of the operation
     * @throws NullPointerException if any parameter is null
     */
    public <T> T execute(String key, String processName, Supplier<T> operation, Duration ttl) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(processName, "processName cannot be null");
        Objects.requireNonNull(operation, "operation cannot be null");
        Objects.requireNonNull(ttl, "ttl cannot be null");

        IdempotentStore.IdempotentKey idempotentKey = new IdempotentStore.IdempotentKey(key, processName);
        return execute(idempotentKey, operation, ttl);
    }

    /**
     * Execute an operation idempotently using an IdempotentKey.
     *
     * @param idempotentKey the idempotent key (must not be null)
     * @param operation     the operation to execute (must not be null)
     * @param ttl           the time to live for the idempotent result (must not be null)
     * @param <T>           the return type
     * @return the result of the operation
     * @throws NullPointerException if any parameter is null
     */
    @SuppressWarnings("unchecked")
    public <T> T execute(IdempotentStore.IdempotentKey idempotentKey, Supplier<T> operation, Duration ttl) {
        Objects.requireNonNull(idempotentKey, "idempotentKey cannot be null");
        Objects.requireNonNull(operation, "operation cannot be null");
        Objects.requireNonNull(ttl, "ttl cannot be null");
        // Check if the operation already exists
        IdempotentStore.Value existingValue = idempotentStore.getValue(idempotentKey, Object.class);
        if (existingValue != null) {
            return handleExistingOperation(idempotentKey, existingValue, operation);
        }

        // Execute the operation
        return handleNewOperation(idempotentKey, operation, ttl);
    }

    @SuppressWarnings("unchecked")
    private <T> T handleExistingOperation(
            IdempotentStore.IdempotentKey idempotentKey, IdempotentStore.Value value, Supplier<T> operation) {
        // Return a cached result if completed
        if (COMPLETED.is(value.status())) {
            Object response = value.response();
            if (response instanceof java.util.Map<?, ?> m) {
                return jsonMapper.convertValue(m, (Class<T>) operation.get().getClass());
            }
            return (T) response;
        }

        // If in progress, wait with exponential backoff, then check again
        if (INPROGRESS.is(value.status())) {
            value = completionAwaiter.wait(idempotentKey, value);
            if (value != null && COMPLETED.is(value.status())) {
                return (T) value.response();
            }
        }
        return null;
    }

    private <T> T handleNewOperation(IdempotentStore.IdempotentKey idempotentKey, Supplier<T> operation, Duration ttl) {
        try {
            // Mark as in progress
            long expirationTime = Instant.now().plus(ttl).toEpochMilli();
            IdempotentStore.Value inProgressValue = new IdempotentStore.Value(INPROGRESS.name(), expirationTime, null);
            idempotentStore.store(idempotentKey, inProgressValue);

            // Execute the operation
            T result = operation.get();

            // Store the completed result
            IdempotentStore.Value completedValue = new IdempotentStore.Value(COMPLETED.name(), expirationTime, result);
            idempotentStore.update(idempotentKey, completedValue);

            return result;
        } catch (Exception e) {
            // Clean up on error
            idempotentStore.remove(idempotentKey);
            throw new IdempotentException("Error executing idempotent operation", e);
        }
    }
}
