package io.github.arun0009.idempotent.core.exception;

import io.github.arun0009.idempotent.core.persistence.IdempotentStore;

/**
 * Exception thrown when an attempt is made to store an idempotent key
 * that already exists in the {@link IdempotentStore}.
 * This exception signifies a conflict in maintaining idempotency and
 * prevents overwriting an already existing key in the store.
 */
public class IdempotentKeyConflictException extends IdempotentException {
    private final IdempotentStore.IdempotentKey key;

    public IdempotentKeyConflictException(String message, IdempotentStore.IdempotentKey key) {
        super(message);
        this.key = key;
    }

    public IdempotentStore.IdempotentKey getKey() {
        return key;
    }
}
