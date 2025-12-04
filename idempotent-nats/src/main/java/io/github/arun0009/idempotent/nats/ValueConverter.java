package io.github.arun0009.idempotent.nats;

import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import static java.util.Collections.unmodifiableMap;

final class ValueConverter {
    private ValueConverter() {}

    /**
     * Converts an {@link IdempotentStore.Value} object by transforming its response field into a
     * compatible representation based on its type. If the response is an instance of {@code
     * ResponseEntity} or {@code Wrappers.ResponseEntity}, it transforms the response into the
     * appropriate format. If the response is null or not of the expected types, the original value is
     * returned unchanged.
     *
     * @param value the {@link IdempotentStore.Value} object to be converted
     * @return a new {@link IdempotentStore.Value} object with the transformed response, or the
     *     original value if no transformation is applied
     */
    static IdempotentStore.Value convert(IdempotentStore.Value value) {
        Object response = value.response();
        if (response == null) return value;

        if (response instanceof ResponseEntity) {
            return toEntityWrapper(value, (ResponseEntity<?>) response);
        }
        if (response instanceof Wrappers.ResponseEntity) {
            return fromEntityWrapper(value, (Wrappers.ResponseEntity<?>) response);
        }

        return value;
    }

    private static IdempotentStore.Value toEntityWrapper(IdempotentStore.Value value, ResponseEntity<?> response) {
        return new IdempotentStore.Value(
                value.status(),
                value.expirationTimeInMilliSeconds(),
                new Wrappers.ResponseEntity<>(
                        response.getStatusCode().value(), unmodifiableMap(response.getHeaders()), response.getBody()));
    }

    private static IdempotentStore.Value fromEntityWrapper(
            IdempotentStore.Value value, Wrappers.ResponseEntity<?> response) {
        HttpHeaders headers = new HttpHeaders();
        response.headers().forEach(headers::addAll);
        return new IdempotentStore.Value(
                value.status(),
                value.expirationTimeInMilliSeconds(),
                ResponseEntity.status(response.status()).headers(headers).body(response.body()));
    }
}
