package io.github.arun0009.idempotent.core.serialization;

import io.github.arun0009.idempotent.core.exception.IdempotentException;

/**
 * Runtime exception thrown when payload serialization/deserialization fails.
 */
public final class IdempotentPayloadCodecException extends IdempotentException {

    public IdempotentPayloadCodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
