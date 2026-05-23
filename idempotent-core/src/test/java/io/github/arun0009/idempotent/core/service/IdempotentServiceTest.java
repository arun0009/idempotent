package io.github.arun0009.idempotent.core.service;

import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.github.arun0009.idempotent.core.persistence.InMemoryIdempotentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@SpringBootApplication(scanBasePackages = "io.github.arun0009.idempotent.core")
class IdempotentServiceTest {
    @Autowired
    private IdempotentService idempotentService;

    @Autowired
    private IdempotentStore store;

    @BeforeEach
    void setUp() {
        ((InMemoryIdempotentStore) store).clear();
    }

    @Test
    void testExecuteWithStringKey() {
        AtomicInteger counter = new AtomicInteger(0);
        Supplier<String> operation = () -> "result-" + counter.incrementAndGet();

        // First execution
        String result1 = idempotentService.execute("test-key", operation, Duration.ofMinutes(5));
        assertEquals("result-1", result1);
        assertEquals(1, counter.get());

        // Second execution should return cached result
        String result2 = idempotentService.execute("test-key", operation, Duration.ofMinutes(5));
        assertEquals("result-1", result2);
        assertEquals(1, counter.get()); // Counter should not increment
    }

    @Test
    void testExecuteWithKeyAndProcessName() {
        AtomicInteger counter = new AtomicInteger(0);
        Supplier<String> operation = () -> "result-" + counter.incrementAndGet();

        // First execution
        String result1 = idempotentService.execute("test-key", "test-process", operation, Duration.ofMinutes(5));
        assertEquals("result-1", result1);
        assertEquals(1, counter.get());

        // Second execution should return cached result
        String result2 = idempotentService.execute("test-key", "test-process", operation, Duration.ofMinutes(5));
        assertEquals("result-1", result2);
        assertEquals(1, counter.get()); // Counter should not increment

        // Different process name should execute again
        String result3 = idempotentService.execute("test-key", "different-process", operation, Duration.ofMinutes(5));
        assertEquals("result-2", result3);
        assertEquals(2, counter.get());
    }

    @Test
    void testExecuteWithIdempotentKey() {
        AtomicInteger counter = new AtomicInteger(0);
        Supplier<String> operation = () -> "result-" + counter.incrementAndGet();

        IdempotentStore.IdempotentKey key = new IdempotentStore.IdempotentKey("test-key", "test-process");

        // First execution
        String result1 = idempotentService.execute(key, operation, Duration.ofMinutes(5));
        assertEquals("result-1", result1);
        assertEquals(1, counter.get());

        // Second execution should return cached result
        String result2 = idempotentService.execute(key, operation, Duration.ofMinutes(5));
        assertEquals("result-1", result2);
        assertEquals(1, counter.get()); // Counter should not increment
    }

    @Test
    void testExecuteRethrowsDomainException() {
        Supplier<String> operation = () -> {
            throw new IllegalStateException("Test exception");
        };

        var thrown = assertThrows(
                IllegalStateException.class,
                () -> idempotentService.execute("test-key", operation, Duration.ofMinutes(5)));
        assertEquals("Test exception", thrown.getMessage());

        // Verify the key was removed after the exception.
        IdempotentStore.IdempotentKey key = new IdempotentStore.IdempotentKey("test-key", "default");
        assertNull(store.getValue(key, Object.class));
    }

    @Test
    void testExecuteWithDifferentReturnTypes() {
        Supplier<Integer> intOperation = () -> 42;
        Supplier<String> stringOperation = () -> "hello";

        Integer intResult1 = idempotentService.execute("int-key", intOperation, Duration.ofMinutes(5));
        assertEquals(42, intResult1);

        Integer intResult2 = idempotentService.execute("int-key", intOperation, Duration.ofMinutes(5));
        assertEquals(42, intResult2);

        String stringResult1 = idempotentService.execute("string-key", stringOperation, Duration.ofMinutes(5));
        assertEquals("hello", stringResult1);

        String stringResult2 = idempotentService.execute("string-key", stringOperation, Duration.ofMinutes(5));
        assertEquals("hello", stringResult2);
    }

    @Test
    void testExecuteWithNullResultCachesNullAndRunsOperationOnce() {
        AtomicInteger counter = new AtomicInteger(0);
        Supplier<String> nullOperation = () -> {
            counter.incrementAndGet();
            return null;
        };

        String result1 = idempotentService.execute("null-key", nullOperation, Duration.ofMinutes(5));
        assertNull(result1);

        String result2 = idempotentService.execute("null-key", nullOperation, Duration.ofMinutes(5));
        assertNull(result2);

        assertEquals(1, counter.get(), "null result must be cached so the operation runs only once");
    }

    @Test
    void testExecuteWithComplexObject() {
        Supplier<TestObject> operation = () -> new TestObject("test", 123);

        TestObject result1 = idempotentService.execute("complex-key", operation, Duration.ofMinutes(5));
        assertNotNull(result1);
        assertEquals("test", result1.name);
        assertEquals(123, result1.value);

        TestObject result2 = idempotentService.execute("complex-key", operation, Duration.ofMinutes(5));
        assertNotNull(result2);
        assertEquals("test", result2.name);
        assertEquals(123, result2.value);
    }

    @Test
    void testExecuteWithExplicitReturnType() {
        AtomicInteger counter = new AtomicInteger(0);
        Supplier<String> operation = () -> "typed-" + counter.incrementAndGet();

        String first = idempotentService.execute("typed-key", String.class, operation, Duration.ofMinutes(5));
        String second = idempotentService.execute("typed-key", String.class, operation, Duration.ofMinutes(5));

        assertEquals("typed-1", first);
        assertEquals("typed-1", second);
        assertEquals(1, counter.get());
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
    private record TestObject(String name, int value) {}
}
