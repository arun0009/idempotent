package io.github.arun0009.idempotent.nats;

import io.github.arun0009.idempotent.core.exception.IdempotentException;

/**
 * Exception thrown during idempotency operations in the NATS Key-Value store.
 */
class NatsIdempotentException extends IdempotentException {

    NatsIdempotentException(String message, Throwable cause) {
        super(message, cause);
    }
}
