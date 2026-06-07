package io.github.arun0009.idempotent.core.serialization;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Bridges {@link ResponseEntity} and the codec-friendly {@link ResponseEntityPayload}. Shared by
 * the Jackson module and the JDK codec so both wire formats round-trip identically.
 */
public final class ResponseEntityAdapter {

    private ResponseEntityAdapter() {}

    public static ResponseEntityPayload toPayload(ResponseEntity<?> response) {
        var source = response.getHeaders();
        var headers = new LinkedHashMap<String, List<String>>();
        source.forEach(headers::put);
        return new ResponseEntityPayload(response.getStatusCode().value(), headers, response.getBody());
    }

    public static ResponseEntity<?> fromPayload(ResponseEntityPayload payload) {
        var headers = new HttpHeaders();
        payload.headers().forEach(headers::addAll);
        return ResponseEntity.status(HttpStatusCode.valueOf(payload.status()))
                .headers(headers)
                .body(payload.body());
    }
}
