package io.github.arun0009.idempotent.core.persistence;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

import static io.github.arun0009.idempotent.core.persistence.IdempotentStore.Value;

/**
 * Helpers for {@link IdempotentStore.Value} expiry handling.
 */
public final class IdempotentValues {

    private static final Logger log = LoggerFactory.getLogger(IdempotentValues.class);

    private IdempotentValues() {}

    /**
     * Returns the value when valid, or {@code null} when missing or expired. Expired entries are
     * removed via {@code removeExpired} as a best-effort cleanup so subsequent strict inserts can
     * reuse the key. Cleanup failures are logged and swallowed; a {@code getValue} read must not
     * fail because of opportunistic cleanup.
     */
    public static @Nullable Value withoutExpired(@Nullable Value value, Runnable removeExpired) {
        if (value == null) {
            return null;
        }
        if (Instant.now().isBefore(value.expiresAt())) {
            return value;
        }
        try {
            removeExpired.run();
        } catch (RuntimeException e) {
            log.warn("Failed to remove expired idempotent entry; will be cleaned up later: {}", e.toString());
        }
        return null;
    }

    /**
     * Remaining time until {@code expiresAt} (never negative).
     */
    public static Duration remaining(Instant expiresAt) {
        var remaining = Duration.between(Instant.now(), expiresAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }
}
