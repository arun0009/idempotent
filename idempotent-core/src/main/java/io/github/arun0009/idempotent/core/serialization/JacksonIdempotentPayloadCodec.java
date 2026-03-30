package io.github.arun0009.idempotent.core.serialization;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** JSON codec backed by Jackson {@link JsonMapper}. */
public final class JacksonIdempotentPayloadCodec implements IdempotentPayloadCodec {

    private final JsonMapper jsonMapper;

    public JacksonIdempotentPayloadCodec(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public byte[] serializeToBytes(Object value) {
        try {
            return jsonMapper.writeValueAsBytes(value);
        } catch (JacksonException e) {
            throw new IdempotentPayloadCodecException("Failed to serialize idempotent payload", e);
        }
    }

    @Override
    public <T> T deserializeFromBytes(byte[] bytes, Class<T> type) {
        try {
            return jsonMapper.readValue(bytes, type);
        } catch (JacksonException e) {
            throw new IdempotentPayloadCodecException("Failed to deserialize idempotent payload", e);
        }
    }

    @Override
    public @Nullable String serializeToString(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IdempotentPayloadCodecException("Failed to serialize idempotent payload", e);
        }
    }

    @Override
    public @Nullable Object deserializeFromString(@Nullable String value, Class<?> type) {
        if (value == null) {
            return null;
        }
        try {
            return jsonMapper.readValue(value, type);
        } catch (JacksonException e) {
            throw new IdempotentPayloadCodecException("Failed to deserialize idempotent payload", e);
        }
    }
}
