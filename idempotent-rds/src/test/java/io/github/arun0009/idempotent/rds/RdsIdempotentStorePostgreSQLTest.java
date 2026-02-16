package io.github.arun0009.idempotent.rds;

import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore.IdempotentKey;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RdsPostgreSQLTestConfig.class)
@TestPropertySource(properties = {"idempotent.rds.table.name=idempotent"})
public class RdsIdempotentStorePostgreSQLTest {

    @Autowired
    private IdempotentStore idempotentStore;

    @Autowired
    private RdsCleanupTask rdsCleanupTask;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    public void setUp() {
        // Create the table for PostgreSQL
        jdbcTemplate.update("""
            CREATE TABLE IF NOT EXISTS idempotent (
                key_id VARCHAR(255) NOT NULL,
                process_name VARCHAR(255) NOT NULL,
                status VARCHAR(50),
                expiration_time_millis BIGINT,
                response TEXT,
                PRIMARY KEY (key_id, process_name)
            )
        """);

        jdbcTemplate.update("CREATE INDEX IF NOT EXISTS idx_expiration_time ON idempotent(expiration_time_millis)");

        // Clean up the database before each test
        jdbcTemplate.update("DELETE FROM idempotent");
    }

    @Test
    public void testStoreAndGet() {
        IdempotentKey key = new IdempotentKey("test-key", "test-process");
        Value value = new Value("COMPLETED", System.currentTimeMillis() + 5000, Map.of("result", "success"));

        idempotentStore.store(key, value);

        Value retrieved = idempotentStore.getValue(key, Map.class);
        assertNotNull(retrieved);
        assertEquals("COMPLETED", retrieved.status());
        assertEquals("success", ((Map) retrieved.response()).get("result"));
    }

    @Test
    public void testUpdate() {
        IdempotentKey key = new IdempotentKey("test-key-update", "test-process");
        Value value = new Value("INPROGRESS", System.currentTimeMillis() + 10000, null);

        idempotentStore.store(key, value);

        Value newValue = new Value("COMPLETED", System.currentTimeMillis() + 20000, Map.of("result", "updated"));
        idempotentStore.update(key, newValue);

        Value retrieved = idempotentStore.getValue(key, Map.class);
        assertNotNull(retrieved);
        assertEquals("COMPLETED", retrieved.status());
        assertEquals("updated", ((Map) retrieved.response()).get("result"));
    }

    @Test
    public void testDuplicateStoreDoesNotOverwrite() {
        IdempotentKey key = new IdempotentKey("dup-key", "dup-process");
        Value value1 = new Value("INPROGRESS", System.currentTimeMillis() + 10000, null);
        idempotentStore.store(key, value1);

        Value value2 = new Value("COMPLETED", System.currentTimeMillis() + 20000, Map.of("data", "overwritten"));

        // With race condition protection, this should NOT overwrite value1
        idempotentStore.store(key, value2);

        Value retrieved = idempotentStore.getValue(key, Map.class);
        assertNotNull(retrieved);
        assertEquals("INPROGRESS", retrieved.status()); // Should still be the original value
        assertNull(retrieved.response()); // Original value had null response
    }

    @Test
    public void testCleanup() {
        IdempotentKey key1 = new IdempotentKey("test-key-cleanup-1", "test-process");
        // Expired
        Value value1 = new Value("COMPLETED", System.currentTimeMillis() - 10000, Map.of("data", "expired"));

        IdempotentKey key2 = new IdempotentKey("test-key-cleanup-2", "test-process");
        // Not expired
        Value value2 = new Value("COMPLETED", System.currentTimeMillis() + 10000, Map.of("data", "valid"));

        idempotentStore.store(key1, value1);
        idempotentStore.store(key2, value2);

        rdsCleanupTask.cleanup();

        assertNull(idempotentStore.getValue(key1, Map.class));
        assertNotNull(idempotentStore.getValue(key2, Map.class));
    }

    @Test
    public void testComplexPojoSerialization() {
        IdempotentKey key = new IdempotentKey("complex-key", "complex-process");
        Map<String, Object> complexData = Map.of(
                "id",
                123,
                "status",
                "active",
                "tags",
                List.of("tag1", "tag2"),
                "metadata",
                Map.of("source", "api", "version", "1.0"));

        Value value = new Value("COMPLETED", System.currentTimeMillis() + 5000, complexData);

        idempotentStore.store(key, value);

        Value retrieved = idempotentStore.getValue(key, Map.class);
        assertNotNull(retrieved);
        assertEquals("COMPLETED", retrieved.status());
        Map<String, Object> response = (Map<String, Object>) retrieved.response();
        assertEquals(123, response.get("id"));
        assertEquals("active", response.get("status"));
        assertEquals(List.of("tag1", "tag2"), response.get("tags"));
    }

    @Test
    public void testPostgreSQLDialectDetection() {
        // Verify that PostgreSQL dialect is detected correctly with real PostgreSQL
        RdsDialect dialect = RdsDialect.detect(jdbcTemplate);
        assertEquals(RdsDialect.POSTGRES, dialect);

        // Verify PostgreSQL syntax works (SERIAL, TEXT types)
        jdbcTemplate.update("CREATE TABLE IF NOT EXISTS test_pg_syntax (id SERIAL PRIMARY KEY, name TEXT)");
        jdbcTemplate.update("INSERT INTO test_pg_syntax (name) VALUES ('test')");
        jdbcTemplate.update("DROP TABLE test_pg_syntax");
    }
}
