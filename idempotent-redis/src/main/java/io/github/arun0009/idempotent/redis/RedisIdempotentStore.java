package io.github.arun0009.idempotent.redis;

import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Collections;

/**
 * Redis idempotent store with atomic operations using Lua scripts
 */
public class RedisIdempotentStore implements IdempotentStore {

    private final RedisTemplate<IdempotentKey, Value> redisTemplate;

    private static final String STORE_SCRIPT = """
            local existing = redis.call('GET', KEYS[1])
            local now = tonumber(ARGV[3])
            if not now then
                now = 0
            end

            if not existing then
                -- Key doesn't exist, safe to store it
                local ttl = tonumber(ARGV[2])
                if ttl and type(ttl) == "number" and ttl > 0 and ttl ~= -1 then
                    redis.call('SET', KEYS[1], ARGV[1], 'PX', ttl)
                else
                    redis.call('SET', KEYS[1], ARGV[1])
                end
                return '"1"'
            end

            -- Key exists check if expired
            local success, val = pcall(cjson.decode, existing)
            if success and val and val.expirationTimeInMilliSeconds and val.expirationTimeInMilliSeconds < now then
                  -- Expired, safe to overwrite
                    local ttl = tonumber(ARGV[2])
                    if ttl and type(ttl) == "number" and ttl > 0 and ttl ~= -1 then
                        redis.call('SET', KEYS[1], ARGV[1], 'PX', ttl)
                    else
                        redis.call('SET', KEYS[1], ARGV[1])
                    end
                    return '"1"'
                end
            -- Key exists and not expired, dont overwrite
            return '"0"'""";

    private static final String UPDATE_SCRIPT = """
            local existing = redis.call('GET', KEYS[1])
            if not existing then
               -- Key doesn't exist, nothing to update
                return '"0"'
            end

            local success, val = pcall(cjson.decode, existing)
            if success and val and val.status == 'INPROGRESS' then
                -- Only update if status is INPROGRESS
                local ttl = tonumber(ARGV[2])
                if ttl and type(ttl) == "number" and ttl > 0 and ttl ~= -1 then
                    redis.call('SET', KEYS[1], ARGV[1], 'PX', ttl)
                else
                    redis.call('SET', KEYS[1], ARGV[1])
                end
                return '"1"'
            end
            -- Key exists and not in progress, dont update
            return '"0"'""";

    /**
     * Instantiates a new Redis idempotent store.
     *
     * @param redisTemplate the redis template
     */
    public RedisIdempotentStore(RedisTemplate<IdempotentKey, Value> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private void executeRedisScript(String script, IdempotentKey key, Value value) {
        long now = System.currentTimeMillis();
        long ttl = value.expirationTimeInMilliSeconds() != null ? value.expirationTimeInMilliSeconds() - now : -1;

        redisTemplate.execute(
                new org.springframework.data.redis.core.script.DefaultRedisScript<>(script, String.class),
                Collections.singletonList(key),
                value,
                String.valueOf(ttl),
                String.valueOf(now));
        // Silent fail - race condition handled by Lua script
    }

    @Override
    public Value getValue(IdempotentKey key, Class<?> returnType) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void store(IdempotentKey key, Value value) {
        executeRedisScript(STORE_SCRIPT, key, value);
    }

    @Override
    public void remove(IdempotentKey key) {
        redisTemplate.delete(key);
    }

    @Override
    public void update(IdempotentKey key, Value value) {
        executeRedisScript(UPDATE_SCRIPT, key, value);
    }
}
