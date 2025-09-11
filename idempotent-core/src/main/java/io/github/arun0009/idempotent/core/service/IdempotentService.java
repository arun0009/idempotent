package io.github.arun0009.idempotent.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.arun0009.idempotent.core.exception.IdempotentException;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Service-based API for idempotent operations.
 * Provides programmatic access to idempotency functionality without requiring annotations.
 */
public class IdempotentService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private final IdempotentStore idempotentStore;

    public IdempotentService(IdempotentStore idempotentStore) {
        this.idempotentStore = idempotentStore;
    }

    /**
     * Execute an operation idempotently using a string key with default process name.
     *
     * @param key the idempotent key (must not be null)
     * @param operation the operation to execute (must not be null)
     * @param ttl the time to live for the idempotent result (must not be null)
     * @param <T> the return type of the operation
     * @return the result of the operation
     * @throws NullPointerException if any parameter is null
     */
    public <T> T execute(String key, Supplier<T> operation, Duration ttl) {
        return execute(key, "default", operation, ttl);
    }

    /**
     * Execute an operation idempotently using a string key and process name.
     *
     * @param key the idempotent key (must not be null)
     * @param processName the process name for namespacing (must not be null)
     * @param operation the operation to execute (must not be null)
     * @param ttl the time to live for the idempotent result (must not be null)
     * @param <T> the return type of the operation
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
     * @param operation the operation to execute (must not be null)
     * @param ttl the time to live for the idempotent result (must not be null)
     * @param <T> the return type
     * @return the result of the operation
     * @throws NullPointerException if any parameter is null
     */
    @SuppressWarnings("unchecked")
    public <T> T execute(IdempotentStore.IdempotentKey idempotentKey, Supplier<T> operation, Duration ttl) {
        Objects.requireNonNull(idempotentKey, "idempotentKey cannot be null");
        Objects.requireNonNull(operation, "operation cannot be null");
        Objects.requireNonNull(ttl, "ttl cannot be null");
        // Check if operation already exists
        IdempotentStore.Value existingValue = idempotentStore.getValue(idempotentKey, Object.class);

        if (existingValue != null) {
            // Return cached result if completed
            if (IdempotentStore.Status.COMPLETED.name().equals(existingValue.status())) {
                Object response = existingValue.response();
                if (response instanceof java.util.Map) {
                    return OBJECT_MAPPER.convertValue(
                            response, (Class<T>) operation.get().getClass());
                }
                return (T) response;
            }
            // If in progress, wait briefly then check again (simplified approach)
            if (IdempotentStore.Status.INPROGRESS.name().equals(existingValue.status())) {
                try {
                    Thread.sleep(100); // Simple wait
                    existingValue = idempotentStore.getValue(idempotentKey, Object.class);
                    if (existingValue != null
                            && IdempotentStore.Status.COMPLETED.name().equals(existingValue.status())) {
                        return (T) existingValue.response();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        // Execute the operation
        try {
            // Mark as in progress
            long expirationTime = Instant.now().plus(ttl).toEpochMilli();
            IdempotentStore.Value inProgressValue =
                    new IdempotentStore.Value(IdempotentStore.Status.INPROGRESS.name(), expirationTime, null);
            idempotentStore.store(idempotentKey, inProgressValue);

            // Execute the operation
            T result = operation.get();

            // Store the completed result
            IdempotentStore.Value completedValue =
                    new IdempotentStore.Value(IdempotentStore.Status.COMPLETED.name(), expirationTime, result);
            idempotentStore.update(idempotentKey, completedValue);

            return result;
        } catch (Exception e) {
            // Clean up on error
            idempotentStore.remove(idempotentKey);
            throw new IdempotentException("Error executing idempotent operation", e);
        }
    }
}
