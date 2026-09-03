package io.github.arun0009.idempotent.core.serialization;

import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JacksonIdempotentPayloadCodecTest {

    private JacksonIdempotentPayloadCodec codec;

    public record TestRecord(String name, int value) implements Serializable {}

    public static class TestPojo implements Serializable {
        public String name = "";
        public int value;

        public TestPojo() {}

        public TestPojo(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }

    @BeforeEach
    void setUp() {
        codec = new JacksonIdempotentPayloadCodec(IdempotentJsonMapperDefaults.buildPermissiveMapper());
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
    void responseEntityWithStringBodyRoundTrips() {
        var headers = new HttpHeaders();
        headers.add("X-Trace", "abc");
        headers.add("Set-Cookie", "a=1");
        headers.add("Set-Cookie", "b=2");
        var original =
                ResponseEntity.status(HttpStatus.CREATED).headers(headers).body("hello");

        var json = codec.serializeToString(original);
        var deserialized = codec.deserializeFromString(json, Object.class);

        assertNotNull(deserialized);
        assertInstanceOf(ResponseEntity.class, deserialized);

        var re = (ResponseEntity<?>) deserialized;
        assertEquals(201, re.getStatusCode().value());
        assertEquals("hello", re.getBody());
        assertEquals(List.of("abc"), re.getHeaders().get("X-Trace"));
        assertEquals(List.of("a=1", "b=2"), re.getHeaders().get("Set-Cookie"));
    }

    @Test
    void responseEntityWithRecordBodyRoundTrips() {
        var original = ResponseEntity.ok(new TestRecord("hello", 42));
        var json = codec.serializeToString(original);
        var deserialized = codec.deserializeFromString(json, ResponseEntity.class);

        assertNotNull(deserialized);
        assertInstanceOf(ResponseEntity.class, deserialized);

        var re = (ResponseEntity<?>) deserialized;
        assertEquals(200, re.getStatusCode().value());
        assertInstanceOf(TestRecord.class, re.getBody());
        assertEquals(new TestRecord("hello", 42), re.getBody());
    }

    @Test
    void responseEntityWithVoidBodyRoundTrips() {
        var original = ResponseEntity.noContent().header("X-Count", "1").build();
        var json = codec.serializeToString(original);
        var deserialized = codec.deserializeFromString(json, ResponseEntity.class);

        assertNotNull(deserialized);

        var re = (ResponseEntity<?>) deserialized;
        assertEquals(204, re.getStatusCode().value());
        assertNull(re.getBody());
        assertEquals(List.of("1"), re.getHeaders().get("X-Count"));
    }

    @Test
    void valueWithAttributesRoundTrips() {
        var original = new IdempotentStore.Value(
                IdempotentStore.Status.COMPLETED,
                Instant.parse("2026-01-01T00:00:00Z"),
                "body",
                Map.of("owner", "user-1"));

        var deserialized = codec.deserializeFromBytes(codec.serializeToBytes(original), IdempotentStore.Value.class);

        assertEquals(IdempotentStore.Status.COMPLETED, deserialized.status());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), deserialized.expiresAt());
        assertEquals("body", deserialized.response());
        assertEquals(Map.of("owner", "user-1"), deserialized.attributes());
    }

    @Test
    void valueWithoutAttributesFieldDeserializesToEmptyMap() {
        // Entries persisted before `attributes` existed have no such field.
        // language=JSON
        var legacyJson = """
                {
                  "@class": "io.github.arun0009.idempotent.core.persistence.IdempotentStore$Value",
                  "status": [
                    "io.github.arun0009.idempotent.core.persistence.IdempotentStore$Status",
                    "COMPLETED"
                  ],
                  "expiresAt": [
                    "java.time.Instant",
                    "2026-01-01T00:00:00Z"
                  ],
                  "response": "body"
                }""";

        var deserialized = codec.deserializeFromBytes(legacyJson.getBytes(), IdempotentStore.Value.class);
        assertEquals(IdempotentStore.Status.COMPLETED, deserialized.status());
        assertEquals("body", deserialized.response());
        assertEquals(Map.of(), deserialized.attributes());
    }

    @Test
    void invalidJsonThrows() {
        assertThrows(
                IdempotentPayloadCodecException.class,
                () -> codec.deserializeFromBytes("not-json".getBytes(), Object.class));
    }
}
