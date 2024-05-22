package com.arung.idempotent.core.exception;

public class IdempotentException extends RuntimeException {

    public IdempotentException() {
        super();
    }

    public IdempotentException(Throwable cause) {
        super(cause);
    }

    public IdempotentException(String message) {
        super(message);
    }

    public IdempotentException(String message, Throwable cause) {
        super(message, cause);
    }
}
