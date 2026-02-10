package io.github.arun0009.idempotent.core.retry;

import java.time.Duration;

/**
 * Configuration for retry behavior with exponential backoff.
 *
 * @param maxAttempts       maximum number of retry attempts for in-progress requests.
 * @param delay             initial delay between retries.
 * @param backoffMultiplier multiplier for exponential backoff between retries.
 */
public record WaitStrategy(int maxAttempts, Duration delay, int backoffMultiplier) {

    public static WaitStrategy withDefaults() {
        return new WaitStrategy(5, Duration.ofMillis(100), 2);
    }
}
