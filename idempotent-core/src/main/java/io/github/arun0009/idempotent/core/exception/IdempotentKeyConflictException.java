package io.github.arun0009.idempotent.core.exception;

import io.github.arun0009.idempotent.core.persistence.IdempotentStore;

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
