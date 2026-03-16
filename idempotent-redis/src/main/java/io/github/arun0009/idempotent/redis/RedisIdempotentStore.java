package io.github.arun0009.idempotent.redis;

import io.github.arun0009.idempotent.core.exception.IdempotentKeyConflictException;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import org.jspecify.annotations.Nullable;
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
    public @Nullable Value getValue(IdempotentKey key, Class<?> returnType) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void store(IdempotentKey key, Value value) {
        var timeout = remainingTtlMillis(value.expirationTimeInMilliSeconds());
        // the key should not exist
        var exists = !redisTemplate.opsForValue().setIfAbsent(key, value, timeout, TimeUnit.MILLISECONDS);
        if (exists) {
            throw new IdempotentKeyConflictException("Idempotent key already exists in Redis", key);
        }
    }

    @Override
    public void remove(IdempotentKey key) {
        redisTemplate.delete(key);
    }

    @Override
    public void update(IdempotentKey key, Value value) {
        var timeout = remainingTtlMillis(value.expirationTimeInMilliSeconds());
        redisTemplate.opsForValue().set(key, value, timeout, TimeUnit.MILLISECONDS);
    }

    private static long remainingTtlMillis(long expirationTimeInMs) {
        return Math.max(0, expirationTimeInMs - System.currentTimeMillis());
    }
}
