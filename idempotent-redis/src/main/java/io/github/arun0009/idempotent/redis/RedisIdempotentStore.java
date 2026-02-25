package io.github.arun0009.idempotent.redis;

import io.github.arun0009.idempotent.core.exception.IdempotentKeyConflictException;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * Redis idempotent store
 */
public class RedisIdempotentStore implements IdempotentStore {

    private final RedisTemplate<IdempotentKey, Value> redisTemplate;

    /**
     * Instantiates a new Redis idempotent store.
     *
     * @param redisTemplate the redis template
     */
    public RedisIdempotentStore(RedisTemplate<IdempotentKey, Value> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Value getValue(IdempotentKey key, Class<?> returnType) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void store(IdempotentKey key, Value value) {
        Long ttl = value.expirationTimeInMilliSeconds();
        // the key should not exist
        if (!redisTemplate.opsForValue().setIfAbsent(key, value, ttl, TimeUnit.MILLISECONDS)) {
            throw new IdempotentKeyConflictException("Idempotent key already exists in Redis", key);
        }
    }

    @Override
    public void remove(IdempotentKey key) {
        redisTemplate.delete(key);
    }

    @Override
    public void update(IdempotentKey key, Value value) {
        Long ttl = value.expirationTimeInMilliSeconds();
        redisTemplate.opsForValue().set(key, value, ttl, TimeUnit.MILLISECONDS);
    }
}
