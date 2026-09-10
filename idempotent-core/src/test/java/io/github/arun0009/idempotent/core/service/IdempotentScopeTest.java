package io.github.arun0009.idempotent.core.service;

import io.github.arun0009.idempotent.core.exception.IdempotentKeyConflictException;
import io.github.arun0009.idempotent.core.metrics.IdempotentMetrics;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore.IdempotentKey;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore.Value;
import io.github.arun0009.idempotent.core.persistence.InMemoryIdempotentStore;
import io.github.arun0009.idempotent.core.retry.WaitStrategy;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static io.github.arun0009.idempotent.core.persistence.IdempotentStore.Status.COMPLETED;
import static io.github.arun0009.idempotent.core.persistence.IdempotentStore.Status.IN_PROGRESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class IdempotentScopeTest {
    private static final Duration TTL = Duration.ofMinutes(5);
    private final AtomicReference<String> caller = new AtomicReference<>("alice");
    private final InMemoryIdempotentStore store = new InMemoryIdempotentStore();
    private final IdempotentService service = scopedService(store, caller::get);

    @Test
    void sameClientKeyCreatesSeparateEntriesAndReplaysOnlyTheCallersResponse() {
        var executions = new AtomicInteger();
        Supplier<String> operation = () -> caller.get() + "-" + executions.incrementAndGet();

        assertEquals("alice-1", service.execute("request-123", "orders", String.class, operation, TTL));
        caller.set("bob");
        assertEquals("bob-2", service.execute("request-123", "orders", String.class, operation, TTL));
        assertEquals("bob-2", service.execute("request-123", "orders", operation, TTL));

        caller.set("alice");
        assertEquals("alice-1", service.execute(new IdempotentKey("request-123", "orders"), operation, TTL));

        assertEquals(2, executions.get());
        assertEquals("alice-1", storedResponse("alice_request-123", "orders"));
        assertEquals("bob-2", storedResponse("bob_request-123", "orders"));
        assertNull(store.getValue(new IdempotentKey("request-123", "orders"), String.class));
    }

    @Test
    void customDelimiterIsUsedForStorageAndReplay() {
        var resolver = new IdempotentScopeResolver() {
            @Override
            public String resolveScope() {
                return caller.get();
            }

            @Override
            public String getDelimiter() {
                return "__";
            }
        };
        var scoped = scopedService(store, resolver);
        for (String user : new String[] {"alice", "bob"}) {
            caller.set(user);
            assertEquals(user, scoped.execute("key", caller::get, TTL));
            assertEquals(user, scoped.execute("key", () -> fail("must not execute"), TTL));
            assertEquals(user, storedResponse(user + "__key", "default"));
            assertNull(store.getValue(new IdempotentKey(user + "_key", "default"), String.class));
        }
    }

    @Test
    void scopeIsResolvedOnceAndFailureCleanupLeavesOtherCallersEntryIntact() {
        service.execute("key", () -> "alice-response", TTL);
        caller.set("bob");
        var resolutions = new AtomicInteger();
        var scoped = scopedService(store, () -> {
            resolutions.incrementAndGet();
            return caller.get();
        });
        var failure = new IllegalStateException("failed");
        assertSame(
                failure,
                assertThrows(
                        IllegalStateException.class,
                        () -> scoped.execute(
                                "key",
                                () -> {
                                    caller.set("alice");
                                    throw failure;
                                },
                                TTL)));

        assertEquals(1, resolutions.get());
        assertNull(store.getValue(new IdempotentKey("bob_key", "default"), String.class));
        assertEquals("alice-response", storedResponse("alice_key", "default"));
        caller.set("bob");
        assertEquals("bob-response", scoped.execute("key", () -> "bob-response", TTL));
        assertEquals("bob-response", storedResponse("bob_key", "default"));
    }

    @Test
    void conflictRefetchAndPollingUseTheSameScopedKey() {
        var backend = mock(IdempotentStore.class);
        var resolver = mock(IdempotentScopeResolver.class);
        when(resolver.resolveScope()).thenReturn("alice");
        when(resolver.getDelimiter()).thenReturn("_");

        var key = new IdempotentKey("alice_key", "default");
        var expiresAt = Instant.now().plus(TTL);
        when(backend.getValue(eq(key), any()))
                .thenReturn(null, new Value(IN_PROGRESS, expiresAt, null), new Value(COMPLETED, expiresAt, "cached"));
        doThrow(new IdempotentKeyConflictException("race", key)).when(backend).store(eq(key), any());

        assertEquals("cached", scopedService(backend, resolver).execute("key", () -> fail("must not execute"), TTL));

        verify(resolver).resolveScope();
        verify(resolver).getDelimiter();
        verify(backend, times(3)).getValue(eq(key), any());
        verify(backend).store(eq(key), any());
        verifyNoMoreInteractions(backend, resolver);
    }

    @Test
    void blankScopesUseSharedUnscopedEntries() {
        var executions = new AtomicInteger();
        Supplier<String> operation = () -> "result-" + executions.incrementAndGet();

        for (String scope : new String[] {"", " ", "\t"}) {
            caller.set(scope);
            assertEquals("result-1", service.execute("key", operation, TTL));
        }

        assertEquals(1, executions.get());
        assertEquals("result-1", storedResponse("key", "default"));
        assertEquals("result-1", new IdempotentService(store).execute("key", operation, TTL));
        assertEquals(1, executions.get());
    }

    @Test
    void nullScopeOrResolverExceptionAbortsBeforeStoreAccessOrExecution() {
        var backend = mock(IdempotentStore.class);
        var resolver = mock(IdempotentScopeResolver.class);
        var scoped = scopedService(backend, resolver);
        when(resolver.resolveScope()).thenReturn(null);
        assertThrows(NullPointerException.class, () -> scoped.execute("key", () -> fail("must not execute"), TTL));
        var failure = new IllegalStateException("no authenticated caller");
        when(resolver.resolveScope()).thenThrow(failure);
        assertSame(
                failure,
                assertThrows(
                        IllegalStateException.class, () -> scoped.execute("key", () -> fail("must not execute"), TTL)));
        verifyNoInteractions(backend);
    }

    @Test
    void noResolverKeepsExistingStoredKeys() {
        var key = new IdempotentKey("key", "default");
        store.store(key, new Value(COMPLETED, Instant.now().plus(TTL), "legacy-response"));

        assertEquals(
                "legacy-response", new IdempotentService(store).execute("key", () -> fail("must not execute"), TTL));
    }

    private @Nullable Object storedResponse(String key, String process) {
        var value = store.getValue(new IdempotentKey(key, process), String.class);
        assertNotNull(value);
        assertEquals(COMPLETED, value.status());
        return value.response();
    }

    private static IdempotentService scopedService(IdempotentStore store, IdempotentScopeResolver resolver) {
        return new IdempotentService(
                store, new WaitStrategy(2, Duration.ofMillis(1), 1), IdempotentMetrics.NOOP, resolver);
    }
}
