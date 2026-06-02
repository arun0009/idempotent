package io.github.arun0009.idempotent.redis;

import io.github.arun0009.idempotent.core.exception.IdempotentKeyConflictException;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.github.arun0009.idempotent.core.persistence.IdempotentValues;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed {@link IdempotentStore}. Uses {@code SET ... NX} for strict insert and
 * {@code SET ... XX} for updates so an update never resurrects a missing key.
 */
public class RedisIdempotentStore implements IdempotentStore {

    private final RedisTemplate<IdempotentKey, Value> redisTemplate;

    public RedisIdempotentStore(RedisTemplate<IdempotentKey, Value> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public @Nullable Value loadValue(IdempotentKey key, Class<?> returnType) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void store(IdempotentKey key, Value value) {
        var timeout = IdempotentValues.remaining(value.expiresAt());
        var inserted = redisTemplate.opsForValue().setIfAbsent(key, value, timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (Boolean.FALSE.equals(inserted)) {
            throw new IdempotentKeyConflictException("Idempotent key already exists in Redis", key);
        }
    }

    @Override
    public void remove(IdempotentKey key) {
        redisTemplate.delete(key);
    }

    @Override
    public void update(IdempotentKey key, Value value) {
        Duration timeout = IdempotentValues.remaining(value.expiresAt());
        // SET ... XX — only set when the key already exists (no-op when missing).
        redisTemplate.opsForValue().setIfPresent(key, value, timeout.toMillis(), TimeUnit.MILLISECONDS);
    }
}
