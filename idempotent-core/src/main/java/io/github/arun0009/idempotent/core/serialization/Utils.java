package io.github.arun0009.idempotent.core.serialization;

import org.springframework.util.ClassUtils;

final class Utils {
    private static final boolean RESPONSE_ENTITY_PRESENT =
            ClassUtils.isPresent("org.springframework.http.ResponseEntity", null);

    private Utils() {}

    static boolean isResponseEntityPresent() {
        return RESPONSE_ENTITY_PRESENT;
    }
}
