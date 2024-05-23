package com.codeweave.idempotent.redis;

import com.codeweave.idempotent.core.persistence.IdempotentStore;
import org.springframework.data.redis.core.RedisTemplate;

public class RedisIdempotentStore implements IdempotentStore {

    private final RedisTemplate<IdempotentKey, Value> redisTemplate;

    public RedisIdempotentStore(RedisTemplate<IdempotentKey, Value> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Value getValue(IdempotentKey key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void store(IdempotentKey key, Value value) {
        redisTemplate.opsForValue().set(key, value);
    }

    @Override
    public void remove(IdempotentKey key) {
        redisTemplate.delete(key);
    }

    @Override
    public void update(IdempotentKey key, Value value) {
        redisTemplate.opsForValue().set(key, value);
    }
}
