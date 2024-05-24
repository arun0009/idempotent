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
        map.put(idempotentKey, value);
    }

    @Override
    public void remove(IdempotentKey idempotentKey) {
        map.remove(idempotentKey);
    }

    @Override
    public void update(IdempotentKey idempotentKey, Value value) {
        map.replace(idempotentKey, value);
    }
}
