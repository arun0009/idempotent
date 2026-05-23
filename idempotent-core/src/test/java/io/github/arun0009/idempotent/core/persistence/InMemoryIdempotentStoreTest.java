package io.github.arun0009.idempotent.core.persistence;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static io.github.arun0009.idempotent.core.persistence.IdempotentStore.Status.COMPLETED;
import static io.github.arun0009.idempotent.core.persistence.IdempotentStore.Status.IN_PROGRESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class InMemoryIdempotentStoreTest {

    @Test
    void removesExpiredEntryOnReadSoKeyCanBeStoredAgain() {
        var store = new InMemoryIdempotentStore();
        var key = new IdempotentStore.IdempotentKey("order-1", "default");

        store.store(key, new IdempotentStore.Value(COMPLETED, Instant.now().minusSeconds(1), "stale"));

        assertNull(store.getValue(key, String.class));

        store.store(key, new IdempotentStore.Value(IN_PROGRESS, Instant.now().plusSeconds(60), null));

        var stored = store.getValue(key, Object.class);
        assertNotNull(stored);
        assertEquals(IN_PROGRESS, stored.status());
    }

    @Test
    void updateIsNoOpWhenKeyIsMissing() {
        var store = new InMemoryIdempotentStore();
        var key = new IdempotentStore.IdempotentKey("missing-key", "default");

        store.update(key, new IdempotentStore.Value(COMPLETED, Instant.now().plusSeconds(60), "should-not-resurrect"));

        assertNull(store.getValue(key, Object.class));
    }
}
