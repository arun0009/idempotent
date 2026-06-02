package io.github.arun0009.idempotent.core.persistence;

import io.github.arun0009.idempotent.core.exception.IdempotentException;
import io.github.arun0009.idempotent.core.exception.IdempotentKeyConflictException;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.time.Instant;

/**
 * Persistence contract for idempotent entries.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>{@link #store store} performs a strict insert and is the only operation that creates a new
 *       entry. Conflicts raise {@link IdempotentKeyConflictException}.</li>
 *   <li>{@link #update update} mutates an existing entry. It is a <strong>no-op when the key is
 *       missing</strong>; it never resurrects a deleted or expired entry.</li>
 *   <li>{@link #loadValue loadValue} reads the persisted entry as-is. {@link #getValue getValue} is
 *       a default that wraps it with expiry enforcement, so implementations only provide the raw
 *       read.</li>
 *   <li>{@link #remove remove} deletes the entry. It is idempotent and tolerates missing keys.</li>
 * </ol>
 *
 * <h2>Implementation notes</h2>
 * <ul>
 *   <li>Implementations provide {@link #loadValue} (raw persistence) and must <strong>not</strong>
 *       filter on {@code expiresAt} — expiry is enforced centrally by the default {@link #getValue}.</li>
 *   <li>The lazy delete inside the default {@code getValue} is opportunistic; failures are logged
 *       and swallowed.</li>
 *   <li>Expiry uses wall-clock time via {@code expiresAt}; keep nodes clock-synchronized (NTP) and
 *       size TTLs above the slowest operation plus expected skew.</li>
 *   <li>Implementations are expected to be thread-safe.</li>
 * </ul>
 */
public interface IdempotentStore {

    /**
     * Reads the persisted entry for {@code key} without evaluating expiry. Implemented by each
     * backend; callers should use {@link #getValue} which enforces expiry.
     *
     * @param key        the idempotent key
     * @param returnType type hint used by stores that perform typed deserialization (RDS, DynamoDB);
     *                   pass {@code Object.class} when the type is unknown
     * @return the persisted value, or {@code null} when no entry exists
     * @throws IdempotentException if the backend fails
     */
    @Nullable Value loadValue(IdempotentKey key, Class<?> returnType);

    /**
     * Reads the entry for {@code key}, returning {@code null} when missing or expired. Expired
     * entries are removed as a best-effort cleanup so subsequent strict inserts can reuse the key.
     *
     * <p>This is a default that wraps {@link #loadValue}; implementations normally do not override
     * it.
     *
     * @param key        the idempotent key
     * @param returnType type hint used by stores that perform typed deserialization
     * @return the live stored value, or {@code null} when missing or expired
     * @throws IdempotentException if the backend fails
     */
    default @Nullable Value getValue(IdempotentKey key, Class<?> returnType) {
        return IdempotentValues.withoutExpired(loadValue(key, returnType), () -> remove(key));
    }

    /**
     * Strict insert: creates a new entry. Implementations must throw {@link
     * IdempotentKeyConflictException} when the key already exists rather than overwriting.
     *
     * @param key   the idempotent key
     * @param value the value to store
     * @throws IdempotentKeyConflictException if the key already exists
     * @throws IdempotentException            if the backend fails for any other reason
     */
    void store(IdempotentKey key, Value value);

    /**
     * Deletes the entry for {@code key}. Idempotent: tolerates a missing key without throwing.
     */
    void remove(IdempotentKey key);

    /**
     * Mutates an existing entry. <strong>No-op when the key is missing.</strong> Implementations
     * must not insert a new entry as a side effect.
     *
     * @param key   the idempotent key
     * @param value the new value
     * @throws IdempotentException if the backend fails
     */
    void update(IdempotentKey key, Value value);

    /**
     * Composite idempotent key.
     *
     * @param key         caller-supplied idempotency token
     * @param processName logical scope, typically {@code __ControllerName.methodName()}
     */
    record IdempotentKey(String key, String processName) implements Serializable {}

    /**
     * Stored value.
     *
     * @param status    current status of the operation
     * @param expiresAt absolute expiry instant
     * @param response  cached response, or {@code null} when no response has been recorded yet
     *                  (typically while {@link Status#IN_PROGRESS}) or when the response is null
     */
    record Value(Status status, Instant expiresAt, @Nullable Object response) implements Serializable {}

    /** Lifecycle status of an idempotent entry. */
    enum Status {
        IN_PROGRESS,
        COMPLETED
    }
}
