package io.github.arun0009.idempotent.core.serialization;

/**
 * Runtime exception thrown when payload serialization/deserialization fails.
 * <p>
 * Extends {@link IllegalArgumentException} to preserve backward-compatible behavior for callers
 * that already handle codec failures as invalid argument errors.
 */
public final class IdempotentPayloadCodecException extends IllegalArgumentException {

    public IdempotentPayloadCodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
