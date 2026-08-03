package io.github.arun0009.idempotent.core.serialization;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.Base64;

/**
 * Java native serialization codec (requires payload classes to implement {@link java.io.Serializable}).
 */
public final class JdkIdempotentPayloadCodec implements IdempotentPayloadCodec {

    @Override
    public byte[] serializeToBytes(Object value) {
        try (var bos = new ByteArrayOutputStream();
                var oos = newOutputStream(bos)) {
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
        try (var bis = new ByteArrayInputStream(bytes);
                var ois = newInputStream(bis)) {
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

    private static ObjectOutputStream newOutputStream(OutputStream out) throws IOException {
        return Utils.isResponseEntityPresent() ? new ReplacingObjectOutputStream(out) : new ObjectOutputStream(out);
    }

    private static ObjectInputStream newInputStream(InputStream in) throws IOException {
        return Utils.isResponseEntityPresent() ? new ResolvingObjectInputStream(in) : new ObjectInputStream(in);
    }

    private static final class ReplacingObjectOutputStream extends ObjectOutputStream {
        ReplacingObjectOutputStream(OutputStream out) throws IOException {
            super(out);
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object obj) {
            return obj instanceof ResponseEntity<?> re ? ResponseEntityAdapter.toPayload(re) : obj;
        }
    }

    private static final class ResolvingObjectInputStream extends ObjectInputStream {
        ResolvingObjectInputStream(InputStream in) throws IOException {
            super(in);
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object obj) {
            return obj instanceof ResponseEntityPayload p ? ResponseEntityAdapter.fromPayload(p) : obj;
        }
    }
}
