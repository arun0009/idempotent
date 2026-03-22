package io.github.arun0009.idempotent.nats;

import io.github.arun0009.idempotent.core.exception.IdempotentException;

/**
 * Represents a custom exception that is thrown during idempotency-related
 * operations in the NATS Key-Value store integration. This exception acts
 * as a wrapper for errors encountered when interacting with the NATS
 * infrastructure or processing idempotency data.
 * <p>
 * Instances of this exception typically indicate issues such as API errors,
 * serialization issues, or other failures related to storage and retrieval
 * of idempotency information within the NATS environment.
 */
class NatsIdempotentExceptions extends IdempotentException {

    NatsIdempotentExceptions(String message, Throwable cause) {
        super(message, cause);
    }
}
