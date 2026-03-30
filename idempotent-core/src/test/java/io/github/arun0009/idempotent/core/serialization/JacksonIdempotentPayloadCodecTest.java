package io.github.arun0009.idempotent.core.serialization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JacksonIdempotentPayloadCodecTest {

    private JacksonIdempotentPayloadCodec codec;

    public record TestRecord(String name, int value) implements Serializable {}

    public static class TestPojo implements Serializable {
        public String name;
        public int value;

        public TestPojo() {}

        public TestPojo(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }

    @BeforeEach
    void setUp() {
        var builder = JsonMapper.builder();
        IdempotentJsonMapperDefaults.applyPermissivePolymorphicTyping(builder);
        codec = new JacksonIdempotentPayloadCodec(builder.build());
    }

    @Test
    void recordRoundTripsAsObjectType() {
        var original = new TestRecord("hello", 42);
        String json = codec.serializeToString(original);
        assertNotNull(json);
        Object deserialized = codec.deserializeFromString(json, Object.class);
        assertNotNull(deserialized);
        assertEquals(TestRecord.class, deserialized.getClass());
        assertEquals("hello", ((TestRecord) deserialized).name());
        assertEquals(42, ((TestRecord) deserialized).value());
    }

    @Test
    void pojoRoundTripsAsObjectType() {
        var original = new TestPojo("hello", 42);
        String json = codec.serializeToString(original);
        assertNotNull(json);
        Object deserialized = codec.deserializeFromString(json, Object.class);
        assertNotNull(deserialized);
        assertEquals(TestPojo.class, deserialized.getClass());
        assertEquals("hello", ((TestPojo) deserialized).name);
    }

    @Test
    void nullRoundTrips() {
        assertNull(codec.serializeToString(null));
        assertNull(codec.deserializeFromString(null, Object.class));
    }

    @Test
    void invalidJsonThrows() {
        assertThrows(
                IllegalArgumentException.class, () -> codec.deserializeFromBytes("not-json".getBytes(), Object.class));
    }
}
