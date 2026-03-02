package io.github.arun0009.idempotent.core.exception;

import io.github.arun0009.idempotent.core.persistence.IdempotentStore;

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
