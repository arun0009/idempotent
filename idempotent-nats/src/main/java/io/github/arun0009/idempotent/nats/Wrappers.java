package io.github.arun0009.idempotent.nats;

import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import org.jspecify.annotations.Nullable;

import java.util.Map;

final class Wrappers {
    private Wrappers() {}

    record ResponseEntity<T>(
            int status,
            Map<String, String> headers,
            @Nullable T body) {}

    record Value(IdempotentStore.Value value) {
        Value(IdempotentStore.Value value) {
            this.value = ValueConverter.convert(value);
        }
    }
}
