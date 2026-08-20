package io.github.arun0009.idempotent.core.serialization;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResponseEntityAdapterTest {

    @Test
    void preservesStatusHeadersAndBody() {
        var headers = new HttpHeaders();
        headers.add("X-Trace", "abc");
        headers.add("Set-Cookie", "a=1");
        headers.add("Set-Cookie", "b=2");
        var original =
                ResponseEntity.status(HttpStatus.CREATED).headers(headers).body("hello");

        var payload = ResponseEntityAdapter.toPayload(original);
        var roundTripped = ResponseEntityAdapter.fromPayload(payload);

        assertEquals(201, roundTripped.getStatusCode().value());
        assertEquals("hello", roundTripped.getBody());
        assertEquals(List.of("abc"), roundTripped.getHeaders().get("X-Trace"));
        assertEquals(List.of("a=1", "b=2"), roundTripped.getHeaders().get("Set-Cookie"));
    }

    @Test
    void allowsNullBody() {
        var original = ResponseEntity.noContent().build();
        var roundTripped = ResponseEntityAdapter.fromPayload(ResponseEntityAdapter.toPayload(original));
        assertEquals(204, roundTripped.getStatusCode().value());
        assertNull(roundTripped.getBody());
    }
}
