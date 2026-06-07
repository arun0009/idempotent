package io.github.arun0009.idempotent.core.serialization;

import org.springframework.util.ClassUtils;

final class Utils {

    private Utils() {}

    static boolean isResponseEntityPresent() {
        return ClassUtils.isPresent("org.springframework.http.ResponseEntity", null);
    }
}
