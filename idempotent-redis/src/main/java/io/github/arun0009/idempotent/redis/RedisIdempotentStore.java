package io.github.arun0009.idempotent.redis;

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
        redisTemplate.opsForValue().set(key, value);
        if (value.expirationTimeInMilliSeconds() != null) {
            redisTemplate.expire(key, value.expirationTimeInMilliSeconds(), TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void remove(IdempotentKey key) {
        redisTemplate.delete(key);
    }

    @Override
    public void update(IdempotentKey key, Value value) {
        redisTemplate.opsForValue().set(key, value);
        if (value.expirationTimeInMilliSeconds() != null) {
            redisTemplate.expire(key, value.expirationTimeInMilliSeconds(), TimeUnit.MILLISECONDS);
        }
    }
}
