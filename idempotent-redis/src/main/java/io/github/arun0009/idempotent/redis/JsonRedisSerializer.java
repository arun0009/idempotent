package io.github.arun0009.idempotent.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.arun0009.idempotent.core.exception.IdempotentException;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.io.IOException;

/**
 * JsonRedisSerializer serializes IdempotentKey and Value to json before storing it in Redis as Key/Value.
 *
 * @param <T> the type parameter
 */
public class JsonRedisSerializer<T> implements RedisSerializer<T> {

    private final ObjectMapper objectMapper;

    /**
     * Instantiates a new Json redis serializer.
     *
     * @param objectMapper the object mapper
     */
    public JsonRedisSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public byte[] serialize(T t) throws IdempotentException {
        try {
            return objectMapper.writeValueAsBytes(t);
        } catch (JsonProcessingException e) {
            throw new IdempotentException("Error serializing object to JSON", e);
        }
    }

    @Override
    public T deserialize(byte[] bytes) throws IdempotentException {
        if (bytes == null) {
            return null;
        }
        try {
            return objectMapper.readValue(bytes, objectMapper.getTypeFactory().constructType(Object.class));
        } catch (IOException e) {
            throw new IdempotentException("Error deserializing object from JSON", e);
        }
    }
}
