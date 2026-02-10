package io.github.arun0009.idempotent.core.service;

import io.github.arun0009.idempotent.core.exception.IdempotentException;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.github.arun0009.idempotent.core.persistence.InMemoryIdempotentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test for IdempotentService
 */
class IdempotentServiceTest {

    private IdempotentService idempotentService;
    private InMemoryIdempotentStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryIdempotentStore();
        idempotentService = new IdempotentService(store);
        store.clear();
    }

    @Test
    void testExecuteWithStringKey() {
        AtomicInteger counter = new AtomicInteger(0);
        Supplier<String> operation = () -> "result-" + counter.incrementAndGet();

        // First execution
        String result1 = idempotentService.execute("test-key", operation, Duration.ofSeconds(300));
        assertEquals("result-1", result1);
        assertEquals(1, counter.get());

        // Second execution should return cached result
        String result2 = idempotentService.execute("test-key", operation, Duration.ofSeconds(300));
        assertEquals("result-1", result2);
        assertEquals(1, counter.get()); // Counter should not increment
    }

    @Test
    void testExecuteWithKeyAndProcessName() {
        AtomicInteger counter = new AtomicInteger(0);
        Supplier<String> operation = () -> "result-" + counter.incrementAndGet();

        // First execution
        String result1 = idempotentService.execute("test-key", "test-process", operation, Duration.ofSeconds(300));
        assertEquals("result-1", result1);
        assertEquals(1, counter.get());

        // Second execution should return cached result
        String result2 = idempotentService.execute("test-key", "test-process", operation, Duration.ofSeconds(300));
        assertEquals("result-1", result2);
        assertEquals(1, counter.get()); // Counter should not increment

        // Different process name should execute again
        String result3 = idempotentService.execute("test-key", "different-process", operation, Duration.ofSeconds(300));
        assertEquals("result-2", result3);
        assertEquals(2, counter.get());
    }

    @Test
    void testExecuteWithIdempotentKey() {
        AtomicInteger counter = new AtomicInteger(0);
        Supplier<String> operation = () -> "result-" + counter.incrementAndGet();

        IdempotentStore.IdempotentKey key = new IdempotentStore.IdempotentKey("test-key", "test-process");

        // First execution
        String result1 = idempotentService.execute(key, operation, Duration.ofSeconds(300));
        assertEquals("result-1", result1);
        assertEquals(1, counter.get());

        // Second execution should return cached result
        String result2 = idempotentService.execute(key, operation, Duration.ofSeconds(300));
        assertEquals("result-1", result2);
        assertEquals(1, counter.get()); // Counter should not increment
    }

    @Test
    void testExecuteWithException() {
        Supplier<String> operation = () -> {
            throw new RuntimeException("Test exception");
        };

        // Should throw IdempotentException and not cache the result
        assertThrows(IdempotentException.class, () -> idempotentService.execute("test-key", operation, Duration.ofSeconds(300)));

        // Verify the key was removed after exception
        IdempotentStore.IdempotentKey key = new IdempotentStore.IdempotentKey("test-key", "default");
        assertNull(store.getValue(key, Object.class));
    }

    @Test
    void testExecuteWithDifferentReturnTypes() {
        Supplier<Integer> intOperation = () -> 42;
        Supplier<String> stringOperation = () -> "hello";

        Integer intResult1 = idempotentService.execute("int-key", intOperation, Duration.ofSeconds(300));
        assertEquals(42, intResult1);

        Integer intResult2 = idempotentService.execute("int-key", intOperation, Duration.ofSeconds(300));
        assertEquals(42, intResult2);

        String stringResult1 = idempotentService.execute("string-key", stringOperation, Duration.ofSeconds(300));
        assertEquals("hello", stringResult1);

        String stringResult2 = idempotentService.execute("string-key", stringOperation, Duration.ofSeconds(300));
        assertEquals("hello", stringResult2);
    }

    @Test
    void testExecuteWithNullResult() {
        Supplier<String> nullOperation = () -> null;

        String result1 = idempotentService.execute("null-key", nullOperation, Duration.ofSeconds(300));
        assertNull(result1);

        String result2 = idempotentService.execute("null-key", nullOperation, Duration.ofSeconds(300));
        assertNull(result2);
    }

    @Test
    void testExecuteWithComplexObject() {
        Supplier<TestObject> operation = () -> new TestObject("test", 123);

        TestObject result1 = idempotentService.execute("complex-key", operation, Duration.ofSeconds(300));
        assertEquals("test", result1.name);
        assertEquals(123, result1.value);

        TestObject result2 = idempotentService.execute("complex-key", operation, Duration.ofSeconds(300));
        assertEquals("test", result2.name);
        assertEquals(123, result2.value);
    }

    @Test
    void testExecuteWithZeroTtl() {
        AtomicInteger counter = new AtomicInteger(0);
        Supplier<String> operation = () -> "result-" + counter.incrementAndGet();

        String result = idempotentService.execute("zero-ttl-key", operation, Duration.ZERO);
        assertEquals("result-1", result);
        assertEquals(1, counter.get());
    }

    // Helper class for testing complex objects
    private static class TestObject {
        public final String name;
        public final int value;

        public TestObject(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }
}
