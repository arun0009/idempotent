package io.github.arun0009.idempotent.nats;

import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.github.arun0009.idempotent.core.service.IdempotentService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static io.github.arun0009.idempotent.core.persistence.IdempotentStore.Status.COMPLETED;
import static io.github.arun0009.idempotent.core.persistence.IdempotentStore.Status.IN_PROGRESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.testcontainers.utility.DockerImageName.parse;

@SpringBootTest
@DisplayName("NATS Idempotent Service")
class NatsIdempotentServiceTest {
    private static final NatsContainer NATS_CONTAINER = new NatsContainer(parse("nats:latest"));

    @Autowired
    private IdempotentService service;

    @Autowired
    private IdempotentStore store;

    private ExecutorService executor;

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("idempotent.nats.servers", NATS_CONTAINER::getServerUrl);
    }

    @BeforeAll
    static void startContainer() {
        NATS_CONTAINER.start();
    }

    @AfterAll
    static void stopContainer() {
        NATS_CONTAINER.stop();
    }

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(5);
    }

    @AfterEach
    void tearDown() {
        if (executor != null) executor.shutdownNow();
    }

    @Test
    @DisplayName("Returns cached result when the same key is executed twice")
    void testIdempotencyForSameKey() {
        var counter = new AtomicInteger();
        Supplier<String> operation = () -> "result-" + counter.incrementAndGet();

        var result1 = service.execute("test-key", operation, Duration.ofMinutes(5));
        var result2 = service.execute("test-key", operation, Duration.ofMinutes(5));

        assertThat(result1).isEqualTo("result-1");
        assertThat(result2).isEqualTo("result-1");
        assertThat(counter).hasValue(1);
    }

    @Test
    @DisplayName("Executes the operation only once under concurrent access")
    void testConcurrentExecution() {
        var executionCount = new AtomicInteger();
        var expectedResult = "result-" + executionCount.incrementAndGet();

        Supplier<String> operation = () -> {
            try {
                TimeUnit.MILLISECONDS.sleep(100);
                return expectedResult;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        };

        var futures = IntStream.range(0, 5)
                .mapToObj(i -> CompletableFuture.supplyAsync(
                        () -> service.execute("concurrent-key", operation, Duration.ofMinutes(5)), executor))
                .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        var results = futures.stream().map(CompletableFuture::join).toList();

        assertThat(results).isNotEmpty().allMatch(r -> r.equals(results.get(0)));

        assertThat(executionCount).hasValue(1);
    }

    @Test
    @DisplayName("Scopes idempotency by process name for the same key")
    void testProcessScopedIdempotency() {
        var counter = new AtomicInteger();
        var key = "test-key";
        Supplier<String> operation = () -> "result-" + counter.incrementAndGet();

        var r1 = service.execute(key, "process-1", operation, Duration.ofMinutes(5));
        var r2 = service.execute(key, "process-1", operation, Duration.ofMinutes(5));
        var r3 = service.execute(key, "process-2", operation, Duration.ofMinutes(5));
        var r4 = service.execute(key, "process-1", operation, Duration.ofMinutes(5));

        assertThat(r1).isEqualTo("result-1");
        assertThat(r2).isEqualTo("result-1");
        assertThat(r3).isEqualTo("result-2");
        assertThat(r4).isEqualTo("result-1");
        assertThat(counter).hasValue(2);
    }

    @Test
    @DisplayName("Does not cache failed executions and rethrows the original exception")
    void testFailureNotCached() {
        Supplier<String> failingOperation = () -> {
            throw new IllegalStateException("Simulated failure");
        };

        assertThatThrownBy(() -> service.execute("error-key", failingOperation, Duration.ofMinutes(5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Simulated failure");

        assertThatThrownBy(() -> service.execute("error-key", failingOperation, Duration.ofMinutes(5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Simulated failure");

        var result = service.execute("test-key-1", () -> "success", Duration.ofMinutes(5));
        assertThat(result).isEqualTo("success");
    }

    @Test
    @DisplayName("Record payload round-trips correctly through Object.class deserialization")
    void testRecordPayloadRoundTripsAsObjectType() {
        Supplier<TestData> operation = () -> new TestData("record-name", 99);

        var result1 = service.execute("record-key", operation, Duration.ofMinutes(5));
        assertThat(result1)
                .isNotNull()
                .extracting(TestData::name, TestData::value)
                .containsExactly("record-name", 99);

        // Second call with same key — triggers getValue(key, Object.class) inside IdempotentService
        var result2 = service.execute("record-key", operation, Duration.ofMinutes(5));
        assertThat(result2)
                .isNotNull()
                .extracting(TestData::name, TestData::value)
                .containsExactly("record-name", 99);
    }

    @Test
    @DisplayName("Removes expired entry on read so the key can be stored again")
    void removesExpiredEntryOnReadSoKeyCanBeStoredAgain() {
        var key = new IdempotentStore.IdempotentKey("expired-key", "store-test");

        store.store(key, new IdempotentStore.Value(COMPLETED, Instant.now().minusSeconds(1), "stale"));

        assertThat(store.getValue(key, String.class)).isNull();

        store.store(key, new IdempotentStore.Value(IN_PROGRESS, Instant.now().plusSeconds(60), null));

        var stored = store.getValue(key, Object.class);
        assertThat(stored).isNotNull().extracting(IdempotentStore.Value::status).isEqualTo(IN_PROGRESS);
    }

    @Test
    @DisplayName("Update is a no-op when the key is missing")
    void updateIsNoOpWhenKeyIsMissing() {
        var key = new IdempotentStore.IdempotentKey("missing-key", "store-test");

        store.update(key, new IdempotentStore.Value(COMPLETED, Instant.now().plusSeconds(60), "should-not-resurrect"));

        assertThat(store.getValue(key, Object.class)).isNull();
    }

    public record TestData(String name, int value) {}
}
