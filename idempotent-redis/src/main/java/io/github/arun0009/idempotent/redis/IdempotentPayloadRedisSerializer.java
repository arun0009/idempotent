package io.github.arun0009.idempotent.redis;

import io.github.arun0009.idempotent.core.serialization.IdempotentPayloadCodec;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.util.Objects;

/**
 * Redis serializer that delegates to the shared {@link IdempotentPayloadCodec}, keeping Redis JSON
 * serialization aligned with core Jackson settings (including customizers).
 */
public final class IdempotentPayloadRedisSerializer<T> implements RedisSerializer<T> {

    private final IdempotentPayloadCodec codec;
    private final Class<T> type;

    /**
     * Creates a serializer for the given type using the shared idempotent payload codec.
     *
     * @param codec the payload codec
     * @param type  the type to serialize/deserialize
     */
    public IdempotentPayloadRedisSerializer(IdempotentPayloadCodec codec, Class<T> type) {
        this.codec = codec;
        this.type = type;
    }

    @Override
    public byte[] serialize(@Nullable T value) throws SerializationException {
        if (value == null) {
            return new byte[0];
        }
        return codec.serializeToBytes(value);
    }

    @Override
    public @Nullable T deserialize(byte @Nullable [] bytes) throws SerializationException {
        var payload = Objects.requireNonNullElse(bytes, new byte[0]);
        if (payload.length == 0) {
            return null;
        }
        return codec.deserializeFromBytes(payload, type);
    }
}
