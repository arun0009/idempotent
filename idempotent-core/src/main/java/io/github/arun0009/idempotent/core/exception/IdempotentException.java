package io.github.arun0009.idempotent.core.exception;

/**
 * The type Idempotent exception.
 */
public class IdempotentException extends RuntimeException {

    /**
     * Instantiates a new Idempotent exception.
     */
    public IdempotentException() {
        super();
    }

    /**
     * Instantiates a new Idempotent exception.
     *
     * @param cause the cause
     */
    public IdempotentException(Throwable cause) {
        super(cause);
    }

    /**
     * Instantiates a new Idempotent exception.
     *
     * @param message the message
     */
    public IdempotentException(String message) {
        super(message);
    }

    /**
     * Instantiates a new Idempotent exception.
     *
     * @param message the message
     * @param cause   the cause
     */
    public IdempotentException(String message, Throwable cause) {
        super(message, cause);
    }
}
