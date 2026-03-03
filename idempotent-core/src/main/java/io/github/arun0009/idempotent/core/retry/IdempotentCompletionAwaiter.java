package io.github.arun0009.idempotent.core.retry;

import io.github.arun0009.idempotent.core.exception.IdempotentException;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import org.jspecify.annotations.Nullable;
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
     * @param value         current value from store or null if not found
     * @return updated value from store, or null if removed
     */
    public IdempotentStore.@Nullable Value wait(
            IdempotentStore.IdempotentKey idempotentKey, IdempotentStore.Value value) {
        int attempt = 0;
        while (attempt < waitStrategy.maxAttempts() && INPROGRESS.is(value.status())) {
            try {
                long delay = waitStrategy.nextDelayOf(attempt);
                log.debug("Waiting for idempotent operation to complete. Attempt: {}, Delay: {}ms", attempt, delay);
                TimeUnit.MILLISECONDS.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IdempotentException("Interrupted while waiting for idempotent operation to complete", e);
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
