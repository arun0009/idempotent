package io.github.arun0009.idempotent.core.serialization;

import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Codec-friendly wire form of {@link org.springframework.http.ResponseEntity}. Headers are stored
 * as a multi-value map to preserve repeated header values (e.g. {@code Set-Cookie}).
 */
public record ResponseEntityPayload(
        int status,
        Map<String, List<String>> headers,
        @Nullable Object body) implements Serializable {}
