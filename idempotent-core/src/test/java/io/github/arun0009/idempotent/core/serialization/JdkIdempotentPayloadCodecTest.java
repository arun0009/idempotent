package io.github.arun0009.idempotent.core.serialization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdkIdempotentPayloadCodecTest {

    private JdkIdempotentPayloadCodec codec;

    public record TestRecord(String name, int value) implements Serializable {}

    @BeforeEach
    void setUp() {
        codec = new JdkIdempotentPayloadCodec();
    }

    @Test
    void recordRoundTrips() {
        var original = new TestRecord("hello", 42);
        String base64 = codec.serializeToString(original);
        Object deserialized = codec.deserializeFromString(base64, TestRecord.class);
        assertEquals(original, deserialized);
    }

    @Test
    void nullRoundTrips() {
        assertNull(codec.serializeToString(null));
        assertNull(codec.deserializeFromString(null, Object.class));
    }

    @Test
    void nonSerializableThrows() {
        record NotSerializable(String name) {}
        assertThrows(IllegalArgumentException.class, () -> codec.serializeToBytes(new NotSerializable("x")));
    }
}
