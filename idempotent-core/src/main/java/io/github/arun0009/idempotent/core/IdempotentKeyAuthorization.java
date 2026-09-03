package io.github.arun0009.idempotent.core;

import io.github.arun0009.idempotent.core.persistence.IdempotentStore.IdempotentKey;

import java.util.Map;

/**
 * Controls access to idempotency keys based on the caller that claimed them.
 *
 * <p>{@link #getClaimAttributes(IdempotentKey)} captures attributes when a key is claimed.
 * {@link #authorize(IdempotentKey, Map)} checks access whenever an existing entry is read.
 * Throw from {@code ensure} to reject the caller.
 *
 * <p>Implementations obtain caller identity from their own context. The guard is optional;
 * core uses {@link #NOOP} when none is configured.
 *
 * <p>Implementations must be thread-safe.
 */
public interface IdempotentKeyAuthorization {

    /**
     * Returns attributes to store with a newly claimed entry.
     *
     * @param key the idempotent key being claimed
     * @return attributes to store, or an empty map
     */
    Map<String, String> getClaimAttributes(IdempotentKey key);

    /**
     * Checks whether the current caller may use an existing entry.
     *
     * @param key              the idempotent key being read
     * @param storedAttributes attributes recorded when the entry was claimed
     * @throws RuntimeException if access is denied
     */
    void authorize(IdempotentKey key, Map<String, String> storedAttributes);

    IdempotentKeyAuthorization NOOP = new Noop();

    final class Noop implements IdempotentKeyAuthorization {
        private Noop() {}

        @Override
        public Map<String, String> getClaimAttributes(IdempotentKey key) {
            return Map.of();
        }

        @Override
        public void authorize(IdempotentKey key, Map<String, String> storedAttributes) {
            // noop
        }
    }
}
