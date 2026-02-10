package io.github.arun0009.idempotent.core.retry;

import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static io.github.arun0009.idempotent.core.persistence.IdempotentStore.Status.INPROGRESS;

/**
 * Waits for an in-progress idempotent operation to complete using exponential backoff.
 */
public class IdempotentCompletionAwaiter {
    private static final Logger log = LoggerFactory.getLogger(IdempotentCompletionAwaiter.class);

    private final IdempotentStore idempotentStore;
    private final WaitStrategy waitStrategy;

    public IdempotentCompletionAwaiter(IdempotentStore idempotentStore, WaitStrategy waitStrategy) {
        this.idempotentStore = idempotentStore;
        this.waitStrategy = waitStrategy;
    }

    /**
     * Waits for an in-progress operation to complete using exponential backoff.
     *
     * @param idempotentKey the idempotent key for the request
     * @param value         current value from store
     * @return updated value from store, or null if removed
     */
    public IdempotentStore.Value wait(IdempotentStore.IdempotentKey idempotentKey, IdempotentStore.Value value) {
        int attempt = 0;
        while (attempt < waitStrategy.maxAttempts() && INPROGRESS.is(value.status())) {
            try {
                long delay = waitStrategy
                        .delay()
                        .plusMillis((long) Math.pow(waitStrategy.backoffMultiplier(), attempt))
                        .toMillis();
                log.atTrace()
                        .log("Waiting for idempotent operation to complete. Attempt: {}, Delay: {}ms", attempt, delay);
                TimeUnit.MILLISECONDS.sleep(delay);
            } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for idempotent operation to complete", e);
                Thread.currentThread().interrupt();
                return value;
            }
            attempt++;
            value = idempotentStore.getValue(idempotentKey, Object.class);
            if (value == null) {
                return null;
            }
        }
        return value;
    }
}
