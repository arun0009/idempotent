package com.codeweave.idempotent.redis;

import com.codeweave.idempotent.core.exception.IdempotentException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.io.IOException;

public class JsonRedisSerializer<T> implements RedisSerializer<T> {

    private final ObjectMapper objectMapper;

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
