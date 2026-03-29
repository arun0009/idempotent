package io.github.arun0009.idempotent.rds;

import com.zaxxer.hikari.HikariDataSource;
import io.github.arun0009.idempotent.core.exception.IdempotentKeyConflictException;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore.IdempotentKey;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore.Value;
import io.github.arun0009.idempotent.core.serialization.IdempotentJsonMapperDefaults;
import io.github.arun0009.idempotent.core.serialization.JacksonIdempotentPayloadCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Fast H2-based tests for development.
 * These tests use H2 in MySQL mode for quick feedback during development.
 * For production accuracy, use MySQLTest and PostgreSQLTest.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RdsIdempotentStoreH2Test.H2TestConfig.class)
@TestPropertySource(properties = {"idempotent.rds.table-name=idempotent"})
class RdsIdempotentStoreH2Test {

    @Autowired
    private IdempotentStore idempotentStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Configuration
    static class H2TestConfig {
        @Bean
        public DataSource dataSource() {
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL");
            ds.setDriverClassName("org.h2.Driver");
            ds.setUsername("sa");
            ds.setPassword("");
            return ds;
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        public IdempotentStore idempotentStore(JdbcTemplate jdbcTemplate) {
            var builder = JsonMapper.builder();
            IdempotentJsonMapperDefaults.applyPermissivePolymorphicTyping(
                    builder, LoggerFactory.getLogger(H2TestConfig.class));
            return new RdsIdempotentStore(
                    jdbcTemplate, "idempotent", new JacksonIdempotentPayloadCodec(builder.build()));
        }

        @Bean
        public RdsCleanupTask rdsCleanupTask(JdbcTemplate jdbcTemplate) {
            RdsDialect dialect = RdsDialect.detect(jdbcTemplate);
            int batchSize = 1000;
            return new RdsCleanupTask(jdbcTemplate, "idempotent", dialect, batchSize);
        }
    }

    @BeforeEach
    void setUp() {
        // Create table for H2
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
        jdbcTemplate.update("DELETE FROM idempotent");
    }

    @Test
    void testStoreAndGet() {
        IdempotentKey key = new IdempotentKey("test-key", "test-process");
        Value value = new Value("COMPLETED", System.currentTimeMillis() + 5000, "success");

        idempotentStore.store(key, value);

        Value retrieved = idempotentStore.getValue(key, String.class);
        assertNotNull(retrieved);
        assertEquals("COMPLETED", retrieved.status());
        assertEquals("success", retrieved.response());
    }

    @Test
    void testDialectDetection() {
        // Verify H2 dialect is detected correctly
        RdsDialect dialect = RdsDialect.detect(jdbcTemplate);
        assertEquals(RdsDialect.H2, dialect);
    }

    @Test
    void testDuplicateStoreThrowsException() {
        IdempotentKey key = new IdempotentKey("race-key", "race-process");
        Value value1 = new Value("INPROGRESS", System.currentTimeMillis() + 10000, null);
        idempotentStore.store(key, value1);

        Value value2 = new Value("COMPLETED", System.currentTimeMillis() + 20000, "overwritten");
        assertThrows(IdempotentKeyConflictException.class, () -> idempotentStore.store(key, value2));

        Value retrieved = idempotentStore.getValue(key, String.class);
        assertNotNull(retrieved);
        assertEquals("INPROGRESS", retrieved.status());
    }

    @Test
    void testRecordPayloadRoundTrip() {
        IdempotentKey key = new IdempotentKey("record-key", "test-process");
        Value value = new Value("COMPLETED", System.currentTimeMillis() + 5000, new TestRecord("hello", 42));

        idempotentStore.store(key, value);

        Value retrieved = idempotentStore.getValue(key, Object.class);
        assertNotNull(retrieved);
        assertEquals("COMPLETED", retrieved.status());
        assertNotNull(retrieved.response());
        assertEquals(TestRecord.class, retrieved.response().getClass());
        assertEquals("hello", ((TestRecord) retrieved.response()).name());
        assertEquals(42, ((TestRecord) retrieved.response()).value());
    }

    public record TestRecord(String name, int value) {}
}
