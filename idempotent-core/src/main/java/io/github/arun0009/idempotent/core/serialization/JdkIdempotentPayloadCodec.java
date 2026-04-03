package io.github.arun0009.idempotent.core.serialization;

import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;

/** Java native serialization codec (requires payload classes to implement {@link java.io.Serializable}). */
public final class JdkIdempotentPayloadCodec implements IdempotentPayloadCodec {

    @Override
    public byte[] serializeToBytes(Object value) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(value);
            oos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IdempotentPayloadCodecException(
                    "Failed to serialize idempotent payload using JDK serialization", e);
        }
    }

    @Override
    public <T> T deserializeFromBytes(byte[] bytes, Class<T> type) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
                ObjectInputStream ois = new ObjectInputStream(bis)) {
            Object value = ois.readObject();
            return type.cast(value);
        } catch (IOException | ClassNotFoundException e) {
            throw new IdempotentPayloadCodecException(
                    "Failed to deserialize idempotent payload using JDK serialization", e);
        }
    }

    @Override
    public @Nullable String serializeToString(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(serializeToBytes(value));
    }

    @Override
    public @Nullable Object deserializeFromString(@Nullable String value, Class<?> type) {
        if (value == null) {
            return null;
        }
        return deserializeFromBytes(Base64.getDecoder().decode(value), type);
    }
}
