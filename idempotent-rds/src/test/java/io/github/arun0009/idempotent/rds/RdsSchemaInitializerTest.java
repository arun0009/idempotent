package io.github.arun0009.idempotent.rds;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;

class RdsSchemaInitializerTest {

    private HikariDataSource dataSource;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:mem:schema-init-%s".formatted(UUID.randomUUID()));
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    @Test
    void createsTableWhenEmbedded() {
        initializer("idempotent", DatabaseInitializationMode.EMBEDDED).afterPropertiesSet();

        assertEquals(0, countRows("idempotent"));
    }

    @Test
    void createsTableWhenAlways() {
        initializer("my_keys", DatabaseInitializationMode.ALWAYS).afterPropertiesSet();

        assertEquals(0, countRows("my_keys"));
    }

    @Test
    void createsSchemaQualifiedTable() {
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS audit");

        initializer("audit.idempotent", DatabaseInitializationMode.EMBEDDED).afterPropertiesSet();

        assertEquals(0, countRows("audit.idempotent"));
    }

    @Test
    void isIdempotentIfTableExists() {
        var initializer = initializer("idempotent", DatabaseInitializationMode.ALWAYS);
        initializer.afterPropertiesSet();
        jdbcTemplate.update(
                "INSERT INTO idempotent (key_id, process_name, status, expires_at) VALUES ('k', 'p', 'COMPLETED', 1)");

        assertDoesNotThrow(initializer::afterPropertiesSet);
        assertEquals(1, countRows("idempotent"));
    }

    @Test
    void addsAttributesColumnToLegacyTable() {
        jdbcTemplate.execute("""
                CREATE TABLE idempotent (
                    key_id VARCHAR(255) NOT NULL,
                    process_name VARCHAR(255) NOT NULL,
                    status VARCHAR(50) NOT NULL,
                    expires_at BIGINT NOT NULL,
                    response TEXT,
                    PRIMARY KEY (key_id, process_name)
                )""");

        initializer("idempotent", DatabaseInitializationMode.ALWAYS).afterPropertiesSet();

        assertDoesNotThrow(() -> jdbcTemplate.execute("SELECT attributes FROM idempotent WHERE 1 = 0"));
    }

    @Test
    void skipsAttributesMigrationWhenColumnExists() {
        initializer("idempotent", DatabaseInitializationMode.ALWAYS).afterPropertiesSet();
        var spy = Mockito.spy(jdbcTemplate);

        new RdsSchemaInitializer(dataSource, spy, "idempotent", DatabaseInitializationMode.ALWAYS).afterPropertiesSet();

        Mockito.verify(spy, never()).execute(startsWith("ALTER TABLE"));
    }

    @Test
    void doesNothingWhenNever() {
        initializer("idempotent", DatabaseInitializationMode.NEVER).afterPropertiesSet();

        assertThrows(BadSqlGrammarException.class, () -> countRows("idempotent"));
    }

    @Test
    void embeddedSkipsNonEmbeddedDataSource() throws SQLException {
        var postgres = Mockito.mock(JdbcTemplate.class);
        var remote = Mockito.mock(DataSource.class);
        Mockito.doThrow(new SQLException("not an embedded database"))
                .when(remote)
                .getConnection();

        new RdsSchemaInitializer(remote, postgres, "idempotent", DatabaseInitializationMode.EMBEDDED)
                .afterPropertiesSet();

        Mockito.verify(postgres, never()).execute(anyString());
    }

    @Test
    void skipsUnrecognizedDialect() {
        var unrecognized = Mockito.mock(JdbcTemplate.class);
        Mockito.doReturn(RdsDialect.GENERIC).when(unrecognized).execute(any(ConnectionCallback.class));

        new RdsSchemaInitializer(dataSource, unrecognized, "idempotent", DatabaseInitializationMode.ALWAYS)
                .afterPropertiesSet();

        Mockito.verify(unrecognized, never()).execute(startsWith("CREATE"));
    }

    @Test
    void ignoresCreateRaceIfTableExists() {
        var initializer = new RdsSchemaInitializer(
                dataSource, racingOnCreateTable(true), "idempotent", DatabaseInitializationMode.ALWAYS);

        assertDoesNotThrow(initializer::afterPropertiesSet);
        assertEquals(0, countRows("idempotent"));
    }

    @Test
    void rethrowsIfTableMissingAfterRace() {
        var initializer = new RdsSchemaInitializer(
                dataSource, racingOnCreateTable(false), "idempotent", DatabaseInitializationMode.ALWAYS);

        assertThrows(DuplicateKeyException.class, initializer::afterPropertiesSet);
    }

    private JdbcTemplate racingOnCreateTable(boolean winnerCreatesTheTable) {
        var racing = Mockito.spy(jdbcTemplate);
        Mockito.doAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    if (!sql.startsWith("CREATE TABLE")) {
                        return invocation.callRealMethod();
                    }
                    if (winnerCreatesTheTable) {
                        jdbcTemplate.execute(sql);
                    }
                    throw new DuplicateKeyException(
                            "duplicate key value violates unique constraint \"pg_type_typname_nsp_index\"");
                })
                .when(racing)
                .execute(anyString());
        return racing;
    }

    private RdsSchemaInitializer initializer(String tableName, DatabaseInitializationMode mode) {
        return new RdsSchemaInitializer(dataSource, jdbcTemplate, tableName, mode);
    }

    private int countRows(String tableName) {
        var count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM %s".formatted(tableName), Integer.class);
        return count == null ? -1 : count;
    }
}
