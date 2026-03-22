package io.github.arun0009.idempotent.core.exception;

import io.github.arun0009.idempotent.core.persistence.IdempotentStore;

/**
 * Exception thrown when an idempotent operation exceeds the configured wait time
 * while trying to achieve completion. This typically occurs in scenarios where
 * an operation is retried but fails to make progress or complete within the given TTL
 * (time-to-live), leading to exhaustion of waiting mechanisms.
 * <p>
 * It serves as an indicator that the idempotent operation could not complete successfully
 * despite repeated attempts and suggests that further execution for the given key should
 * cease unless explicitly overridden.
 */
public class IdempotentWaitExhaustedException extends IdempotentException {
    private final IdempotentStore.IdempotentKey key;

    public IdempotentWaitExhaustedException(String message, IdempotentStore.IdempotentKey key) {
        super(message);
        this.key = key;
    }

    public IdempotentStore.IdempotentKey getKey() {
        return key;
    }
}
