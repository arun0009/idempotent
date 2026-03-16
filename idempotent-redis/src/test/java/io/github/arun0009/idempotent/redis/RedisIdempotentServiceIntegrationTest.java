package io.github.arun0009.idempotent.redis;

import io.github.arun0009.idempotent.core.service.IdempotentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ContextConfiguration;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ContextConfiguration(classes = RedisTestConfig.class, initializers = RedisTestConfig.Initializer.class)
class RedisIdempotentServiceIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(RedisIdempotentServiceIntegrationTest.class);

    @Autowired
    private IdempotentService idempotentService;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    private ExecutorService executor;

    // A static class to initialize the Spring context with the Testcontainers properties
    @SuppressWarnings("unused")
    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            TestPropertyValues.of("idempotent.redis.standalone.host=" + SharedRedisContainer.REDIS_CONTAINER.getHost()
                            + ":" + SharedRedisContainer.REDIS_CONTAINER.getFirstMappedPort())
                    .applyTo(context.getEnvironment());
        }
    }

    @BeforeEach
    void setUp() {
        // Clear all keys before each test
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            log.debug(
                    "Connected to Redis at: {}",
                    redisConnectionFactory.getConnection().getNativeConnection());
            connection.serverCommands().flushAll();
            log.debug("Flushed all Redis keys");
        } catch (Exception e) {
            log.error("Error setting up Redis connection: {}", e.getMessage(), e);
            throw e;
        }
        executor = Executors.newFixedThreadPool(5);
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void testServiceBasicIdempotency() {
        AtomicInteger counter = new AtomicInteger(0);
        Supplier<String> operation = () -> "result-" + counter.incrementAndGet();

        // First execution
        String result1 = idempotentService.execute("test-key", operation, Duration.ofMinutes(5));
        assertEquals("result-1", result1);
        assertEquals(1, counter.get());

        // Second execution should return a cached result
        String result2 = idempotentService.execute("test-key", operation, Duration.ofMinutes(5));
        assertEquals("result-1", result2);
        assertEquals(1, counter.get()); // Counter should not increment
    }

    @Test
    void testServiceConcurrentExecution() throws Exception {
        AtomicInteger executionCount = new AtomicInteger(0);
        Supplier<String> operation = () -> {
            try {
                Thread.sleep(100);
                return "result-" + executionCount.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        };

        // Execute the same operation concurrently
        int numThreads = 5;
        @SuppressWarnings("unchecked")
        CompletableFuture<String>[] futures = new CompletableFuture[numThreads];
        for (int i = 0; i < numThreads; i++) {
            futures[i] = CompletableFuture.supplyAsync(
                    () -> idempotentService.execute("concurrent-key", operation, Duration.ofMinutes(5)), executor);
        }

        // Wait for all to complete
        CompletableFuture.allOf(futures).get();

        // All should return the same result
        String firstResult = futures[0].get();
        for (CompletableFuture<String> future : futures) {
            assertEquals(firstResult, future.get());
        }

        // Operation should have executed only once
        assertEquals(1, executionCount.get());
    }

    @Test
    void testServiceWithDifferentProcessNames() {
        AtomicInteger counter = new AtomicInteger(0);
        String key = "test-key";

        // Operation that increments counter and returns a result
        Supplier<String> operation = () -> "result-" + counter.incrementAndGet();

        // Test 1: First call with process-1 should execute and increment counter to 1
        String result1 = idempotentService.execute(key, "process-1", operation, Duration.ofMinutes(5));
        assertEquals("result-1", result1);
        assertEquals(1, counter.get(), "First call should execute the operation once");

        // Test 2: Same key and same process should return cached result without executing
        String result2 = idempotentService.execute(key, "process-1", operation, Duration.ofMinutes(5));
        assertEquals("result-1", result2, "Same key and process should return cached result");
        assertEquals(1, counter.get(), "Counter should remain 1 for same key and process");

        // Test 3: Same key but different process name should execute again and increment counter to 2
        String result3 = idempotentService.execute(key, "process-2", operation, Duration.ofMinutes(5));
        assertEquals("result-2", result3, "Different process should execute again");
        assertEquals(2, counter.get(), "Counter should increment to 2 for different process");

        // Test 4: Back to first process name should still return cached result
        String result4 = idempotentService.execute(key, "process-1", operation, Duration.ofMinutes(5));
        assertEquals("result-1", result4, "Original process should still get cached result");
        assertEquals(2, counter.get(), "Counter should remain at 2");
    }

    @Test
    void testServiceWithException() {
        Supplier<String> failingOperation = () -> {
            throw new RuntimeException("Simulated failure");
        };

        // First call should fail
        assertThrows(
                Exception.class, () -> idempotentService.execute("error-key", failingOperation, Duration.ofMinutes(5)));

        // Second call should also fail (no caching of errors)
        assertThrows(
                Exception.class, () -> idempotentService.execute("error-key", failingOperation, Duration.ofMinutes(5)));

        // Successful operation after failures should work
        String result = idempotentService.execute("test-key-1", () -> "success", Duration.ofMinutes(5));
        assertEquals("success", result);
    }

    @Test
    void testServiceWithCustomTtl() {
        Supplier<TestData> operation = () -> new TestData("test-name", 42);

        TestData result1 = idempotentService.execute("complex-key-1", operation, Duration.ofMinutes(5));
        assertNotNull(result1);
        assertEquals("test-name", result1.name);
        assertEquals(42, result1.value);

        TestData result2 = idempotentService.execute("complex-key-2", operation, Duration.ofMinutes(5));
        assertNotNull(result2);
        assertEquals("test-name", result2.name);
        assertEquals(42, result2.value);
    }

    public record TestData(String name, int value) {}
}
