package io.github.arun0009.idempotent.nats;

class NatsIdempotentExceptions extends RuntimeException {

    NatsIdempotentExceptions(String message, Throwable cause) {
        super(message, cause);
    }
}
