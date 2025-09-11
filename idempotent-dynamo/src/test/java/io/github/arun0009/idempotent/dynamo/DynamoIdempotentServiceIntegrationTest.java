package io.github.arun0009.idempotent.dynamo;

import io.github.arun0009.idempotent.core.service.IdempotentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(classes = DynamoTestConfig.class, initializers = DynamoTestConfig.Initializer.class)
class DynamoIdempotentServiceIntegrationTest {

    @Autowired
    private DynamoDbClient dynamoDbClient;

    @Value("${idempotent.dynamodb.table.name:Idempotent}")
    private String dynamoTableName;

    @Autowired
    private IdempotentService idempotentService;

    @BeforeEach
    void setUp() {
        clearDynamoTable();
    }

    private void clearDynamoTable() {
        try {
            // First, get the table description to understand the key schema
            DescribeTableRequest describeTableRequest =
                    DescribeTableRequest.builder().tableName(dynamoTableName).build();

            DescribeTableResponse describeTableResponse = dynamoDbClient.describeTable(describeTableRequest);
            List<KeySchemaElement> keySchema = describeTableResponse.table().keySchema();

            // Scan all items in the table
            ScanRequest scanRequest =
                    ScanRequest.builder().tableName(dynamoTableName).build();

            ScanResponse scanResponse = dynamoDbClient.scan(scanRequest);

            // Process items in batches of 25 (DynamoDB limit)
            List<Map<String, AttributeValue>> items = scanResponse.items();
            for (int i = 0; i < items.size(); i += 25) {
                List<Map<String, AttributeValue>> batch = items.subList(i, Math.min(i + 25, items.size()));

                // Build delete requests for each item in the batch
                List<WriteRequest> deleteRequests = batch.stream()
                        .map(item -> {
                            // Create a map containing just the key attributes
                            Map<String, AttributeValue> keyMap = new HashMap<>();
                            keySchema.forEach(key -> keyMap.put(key.attributeName(), item.get(key.attributeName())));

                            return WriteRequest.builder()
                                    .deleteRequest(
                                            DeleteRequest.builder().key(keyMap).build())
                                    .build();
                        })
                        .collect(Collectors.toList());

                if (!deleteRequests.isEmpty()) {
                    BatchWriteItemRequest batchWriteItemRequest = BatchWriteItemRequest.builder()
                            .requestItems(Collections.singletonMap(dynamoTableName, deleteRequests))
                            .build();

                    dynamoDbClient.batchWriteItem(batchWriteItemRequest);
                }
            }
        } catch (ResourceNotFoundException e) {
            // Table doesn't exist yet, which is fine for tests
        } catch (Exception e) {
            throw new RuntimeException("Failed to clear DynamoDB table", e);
        }
    }

    @Test
    void testServiceBasicIdempotency() {
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
    void testServiceConcurrentExecution() throws Exception {
        AtomicInteger executionCount = new AtomicInteger(0);
        String expectedResult = "result-" + (executionCount.incrementAndGet());

        Supplier<String> operation = () -> {
            try {
                Thread.sleep(100); // Simulate work
                return expectedResult; // Always return the same result
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(5);

        // Execute same operation concurrently
        @SuppressWarnings("unchecked")
        CompletableFuture<String>[] futures = new CompletableFuture[5];
        for (int i = 0; i < 5; i++) {
            futures[i] = CompletableFuture.supplyAsync(
                    () -> idempotentService.execute("concurrent-key", operation, Duration.ofSeconds(300)), executor);
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

        executor.shutdown();
    }

    @Test
    void testServiceWithDifferentProcessNames() {
        AtomicInteger counter = new AtomicInteger(0);
        String key = "test-key";

        // Operation that increments counter and returns a result
        Supplier<String> operation = () -> "result-" + counter.incrementAndGet();

        // Test 1: First call with process-1 should execute and increment counter to 1
        String result1 = idempotentService.execute(key, "process-1", operation, Duration.ofSeconds(300));
        assertEquals("result-1", result1);
        assertEquals(1, counter.get(), "First call should execute the operation once");

        // Test 2: Same key and same process should return cached result without executing
        String result2 = idempotentService.execute(key, "process-1", operation, Duration.ofSeconds(300));
        assertEquals("result-1", result2, "Same key and process should return cached result");
        assertEquals(1, counter.get(), "Counter should remain 1 for same key and process");

        // Test 3: Same key but different process name should execute again and increment counter to 2
        String result3 = idempotentService.execute(key, "process-2", operation, Duration.ofSeconds(300));
        assertEquals("result-2", result3, "Different process should execute again");
        assertEquals(2, counter.get(), "Counter should increment to 2 for different process");

        // Test 4: Back to first process name should still return cached result
        String result4 = idempotentService.execute(key, "process-1", operation, Duration.ofSeconds(300));
        assertEquals("result-1", result4, "Original process should still get cached result");
        assertEquals(2, counter.get(), "Counter should remain at 2");
    }

    @Test
    void testServiceErrorHandling() {
        Supplier<String> failingOperation = () -> {
            throw new RuntimeException("Simulated failure");
        };

        // First call should fail
        assertThrows(
                Exception.class,
                () -> idempotentService.execute("error-key", failingOperation, Duration.ofSeconds(300)));

        // Second call should also fail (no caching of errors)
        assertThrows(
                Exception.class,
                () -> idempotentService.execute("error-key", failingOperation, Duration.ofSeconds(300)));

        // Successful operation after failures should work
        String result = idempotentService.execute("test-key-1", () -> "success", Duration.ofSeconds(300));
        assertEquals("success", result);
    }

    @Test
    void testServiceWithComplexObjects() {
        Supplier<TestData> operation = () -> new TestData("test-name", 42);

        TestData result1 = idempotentService.execute("complex-key", operation, Duration.ofSeconds(300));
        assertNotNull(result1);
        assertEquals("test-name", result1.name);
        assertEquals(42, result1.value);

        TestData result2 = idempotentService.execute("complex-key", operation, Duration.ofSeconds(300));
        assertNotNull(result2);
        assertEquals("test-name", result2.name);
        assertEquals(42, result2.value);
    }

    public static class TestData {
        public String name;
        public int value;

        public TestData() {
            // Default constructor for Jackson
        }

        public TestData(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }
}
