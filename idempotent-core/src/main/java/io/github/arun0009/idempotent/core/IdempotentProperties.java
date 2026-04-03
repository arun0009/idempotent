package io.github.arun0009.idempotent.core;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.Name;

/**
 * Configuration properties for the idempotent library.
 * These properties can be configured in application.properties or application.yml.
 * <p>
 * Example:
 * <pre>
 * idempotent.key.header=X-Idempotency-Key
 * idempotent.inprogress.max.retries=5
 * idempotent.inprogress.retry.initial.interval-millis=100
 * idempotent.inprogress.retry.multiplier=2
 * </pre>
 */
@ConfigurationProperties(prefix = "idempotent")
public record IdempotentProperties(
        @Name("key.header") @DefaultValue("X-Idempotency-Key")
        String keyHeader,

        @DefaultValue InProgress inprogress) {

    /**
     * Configuration for retry behavior when a duplicate request arrives
     * while the original request is still in progress.
     */
    public record InProgress(
            @Name("max.retries") @DefaultValue("5") int maxRetries,

            @Name("retry.initial.interval-millis") @DefaultValue("100")
            int retryInitialIntervalMillis,

            @Name("retry.multiplier") @DefaultValue("2") int retryMultiplier) {}
}
