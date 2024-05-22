package com.arung.idempotent.core.persistence;

import java.util.concurrent.ConcurrentHashMap;

public class InMemoryIdempotentStore implements IdempotentStore {

    private final ConcurrentHashMap<IdempotentKey, Value> map;

    public InMemoryIdempotentStore() {
        map = new ConcurrentHashMap<>();
    }

    @Override
    public Value getValue(IdempotentKey idempotentKey){
        return map.get(idempotentKey);
    }

    @Override
    public void store(IdempotentKey idempotentKey, Value value){
         map.put(idempotentKey, value);
    }

    @Override
    public void remove(IdempotentKey idempotentKey){
         map.remove(idempotentKey);
    }

    @Override
    public void update(IdempotentKey idempotentKey, Value value){
        map.replace(idempotentKey, value);
    }
}
