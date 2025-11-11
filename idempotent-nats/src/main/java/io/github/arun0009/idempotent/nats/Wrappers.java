package io.github.arun0009.idempotent.nats;

import io.github.arun0009.idempotent.core.persistence.IdempotentStore;

import java.util.List;
import java.util.Map;

final class Wrappers {
    private Wrappers() {}

    record ResponseEntity<T>(int status, Map<String, List<String>> headers, T body) {}

    record Value(IdempotentStore.Value value, String processName) {
        Value(IdempotentStore.Value value, String processName) {
            this.processName = processName;
            this.value = ValueConverter.convert(value);
        }
    }
}
