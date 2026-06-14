package io.github.arun0009.idempotent.core.serialization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.Serializable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void responseEntityRoundTripsThroughReplaceResolve() {
        var headers = new HttpHeaders();
        headers.add("X-Trace", "abc");
        headers.add("Set-Cookie", "a=1");
        headers.add("Set-Cookie", "b=2");
        var original =
                ResponseEntity.status(HttpStatus.CREATED).headers(headers).body("hello");

        var base64 = codec.serializeToString(original);
        var deserialized = codec.deserializeFromString(base64, ResponseEntity.class);

        assertNotNull(deserialized);
        assertInstanceOf(ResponseEntity.class, deserialized);

        var re = (ResponseEntity<?>) deserialized;
        assertEquals(201, re.getStatusCode().value());
        assertEquals("hello", re.getBody());
        assertEquals(List.of("abc"), re.getHeaders().get("X-Trace"));
        assertEquals(List.of("a=1", "b=2"), re.getHeaders().get("Set-Cookie"));
    }

    @Test
    void nonSerializableThrows() {
        record NotSerializable(String name) {}
        assertThrows(IdempotentPayloadCodecException.class, () -> codec.serializeToBytes(new NotSerializable("x")));
    }
}
