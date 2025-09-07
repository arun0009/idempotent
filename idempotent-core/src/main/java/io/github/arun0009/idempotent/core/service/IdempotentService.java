package io.github.arun0009.idempotent.core.service;

import io.github.arun0009.idempotent.core.exception.IdempotentException;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;

import java.time.Instant;
import java.util.function.Supplier;

/**
 * Service-based API for idempotent operations.
 * Provides programmatic access to idempotency functionality without requiring annotations.
 */
public class IdempotentService {

    private final IdempotentStore idempotentStore;

    public IdempotentService(IdempotentStore idempotentStore) {
        this.idempotentStore = idempotentStore;
    }

    /**
     * Execute an operation idempotently using a string key.
     *
     * @param key the idempotent key
     * @param operation the operation to execute
     * @param ttlInSeconds time to live for the idempotent result
     * @param <T> the return type
     * @return the result of the operation
     */
    public <T> T execute(String key, Supplier<T> operation, long ttlInSeconds) {
        return execute(key, "default", operation, ttlInSeconds);
    }

    /**
     * Execute an operation idempotently using a string key and process name.
     *
     * @param key the idempotent key
     * @param processName the process name for namespacing
     * @param operation the operation to execute
     * @param ttlInSeconds time to live for the idempotent result
     * @param <T> the return type
     * @return the result of the operation
     */
    public <T> T execute(String key, String processName, Supplier<T> operation, long ttlInSeconds) {
        IdempotentStore.IdempotentKey idempotentKey = new IdempotentStore.IdempotentKey(key, processName);
        return execute(idempotentKey, operation, ttlInSeconds);
    }

    /**
     * Execute an operation idempotently using an IdempotentKey.
     *
     * @param idempotentKey the idempotent key
     * @param operation the operation to execute
     * @param ttlInSeconds time to live for the idempotent result
     * @param <T> the return type
     * @return the result of the operation
     */
    @SuppressWarnings("unchecked")
    public <T> T execute(IdempotentStore.IdempotentKey idempotentKey, Supplier<T> operation, long ttlInSeconds) {
        // Check if operation already exists
        IdempotentStore.Value existingValue = idempotentStore.getValue(idempotentKey, Object.class);

        if (existingValue != null) {
            // Return cached result if completed
            if (IdempotentStore.Status.COMPLETED.name().equals(existingValue.status())) {
                Object response = existingValue.response();
                if (response instanceof java.util.Map) {
                    return new com.fasterxml.jackson.databind.ObjectMapper()
                            .convertValue(response, (Class<T>) operation.get().getClass());
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
            long expirationTime = Instant.now().toEpochMilli() + (ttlInSeconds * 1000);
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
