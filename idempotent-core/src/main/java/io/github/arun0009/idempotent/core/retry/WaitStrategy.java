package io.github.arun0009.idempotent.core.retry;

import java.time.Duration;

/**
 * Configuration for retry behavior with exponential backoff.
 *
 * @param maxAttempts       Maximum number of retry attempts for in-progress requests.
 * @param delay             Delay before a retry: initialInterval + (backoffMultiplier ^ attemptNumber).
 * @param backoffMultiplier Multiplier used for exponential backoff between retries.
 */
public record WaitStrategy(int maxAttempts, Duration delay, int backoffMultiplier) {

    public static WaitStrategy withDefaults() {
        return new WaitStrategy(5, Duration.ofMillis(100), 2);
    }

    public long nextDelayOf(int attempt) {
        if (attempt > maxAttempts) {
            throw new IllegalArgumentException("Attempt number must be between 0 and " + maxAttempts);
        }
        return delay.multipliedBy((long) Math.pow(backoffMultiplier, attempt)).toMillis();
    }
}
