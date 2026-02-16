package io.github.arun0009.idempotent.core.persistence;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In memory idempotent store.
 */
public class InMemoryIdempotentStore implements IdempotentStore {

    private final ConcurrentHashMap<IdempotentKey, Value> map;

    /**
     * Instantiates a new In memory idempotent store.
     */
    public InMemoryIdempotentStore() {
        map = new ConcurrentHashMap<>();
    }

    @Override
    public Value getValue(IdempotentKey idempotentKey, Class<?> returnType) {
        return map.get(idempotentKey);
    }

    @Override
    public void store(IdempotentKey idempotentKey, Value value) {
        map.compute(idempotentKey, (k, v) -> {
            if (v == null || v.isExpired()) {
                return value;
            }
            // Race condition - key already exists and not expired
            // Silent fail as per void interface contract
            return v;
        });
    }

    @Override
    public void remove(IdempotentKey idempotentKey) {
        map.remove(idempotentKey);
    }

    @Override
    public void update(IdempotentKey idempotentKey, Value value) {
        map.compute(idempotentKey, (k, v) -> {
            if (v == null
                    || v.isExpired()
                    || IdempotentStore.Status.INPROGRESS.name().equals(v.status())) {
                return value;
            }
            // Race condition - key not in correct state for update
            // Silent fail as per void interface contract
            return v;
        });
    }

    /**
     * Clear all stored values (for testing purposes).
     */
    public void clear() {
        map.clear();
    }
}
