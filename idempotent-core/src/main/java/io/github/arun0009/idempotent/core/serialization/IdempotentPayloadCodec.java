package io.github.arun0009.idempotent.core.serialization;

import org.jspecify.annotations.Nullable;

/** Serializes/deserializes idempotent payload values across storage backends. */
public interface IdempotentPayloadCodec {

    byte[] serializeToBytes(Object value);

    <T> T deserializeFromBytes(byte[] bytes, Class<T> type);

    @Nullable String serializeToString(@Nullable Object value);

    @Nullable Object deserializeFromString(@Nullable String value, Class<?> type);
}
