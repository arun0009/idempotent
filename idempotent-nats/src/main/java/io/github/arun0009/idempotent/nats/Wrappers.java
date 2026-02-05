package io.github.arun0009.idempotent.nats;

import io.github.arun0009.idempotent.core.persistence.IdempotentStore;

import java.util.Map;

final class Wrappers {
    private Wrappers() {}

    record ResponseEntity<T>(int status, Map<String, String> headers, T body) {}

    record Value(IdempotentStore.Value value) {
        Value(IdempotentStore.Value value) {
            this.value = ValueConverter.convert(value);
        }
    }
}
