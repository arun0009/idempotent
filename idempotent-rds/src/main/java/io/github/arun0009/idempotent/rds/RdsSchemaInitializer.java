package io.github.arun0009.idempotent.rds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/** Creates the idempotent table when {@code initialize-schema} is enabled. */
public class RdsSchemaInitializer implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(RdsSchemaInitializer.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final String tableName;
    private final DatabaseInitializationMode mode;

    public RdsSchemaInitializer(
            DataSource dataSource, JdbcTemplate jdbcTemplate, String tableName, DatabaseInitializationMode mode) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.tableName = tableName;
        this.mode = mode;
    }

    @Override
    public void afterPropertiesSet() {
        if (!shouldCreate()) {
            return;
        }
        var dialect = RdsDialect.detect(jdbcTemplate);
        if (dialect == RdsDialect.GENERIC) {
            log.warn("Not creating idempotent table '{}': unrecognized database", tableName);
            return;
        }
        create(dialect);
        migrateAttributes(dialect);
    }

    private boolean shouldCreate() {
        return switch (mode) {
            case NEVER -> false;
            case EMBEDDED -> EmbeddedDatabaseConnection.isEmbedded(dataSource);
            case ALWAYS -> true;
        };
    }

    private void create(RdsDialect dialect) {
        for (var statement : RdsSchemaStatements.ddl(dialect, tableName)) {
            try {
                jdbcTemplate.execute(statement);
            } catch (DuplicateKeyException e) {
                // Concurrent CREATE TABLE IF NOT EXISTS on Postgres hits a catalog unique index.
                if (!tableExists()) {
                    throw e;
                }
                log.debug("Idempotent table '{}' already exists", tableName, e);
            }
        }
    }

    private void migrateAttributes(RdsDialect dialect) {
        if (existsColAttributes()) {
            return;
        }
        try {
            jdbcTemplate.execute(RdsSchemaStatements.addAttributesColumn(dialect, tableName));
        } catch (BadSqlGrammarException e) {
            // Existing installations may manage schema changes outside this initializer.
            log.warn(
                    "Could not add attributes column to idempotent table '{}'; apply the migration manually",
                    tableName,
                    e);
        }
    }

    private boolean tableExists() {
        try {
            jdbcTemplate.execute("SELECT 1 FROM %s WHERE 1 = 0".formatted(tableName));
            return true;
        } catch (BadSqlGrammarException e) {
            return false;
        }
    }

    private boolean existsColAttributes() {
        try {
            jdbcTemplate.execute("SELECT attributes FROM %s WHERE 1 = 0".formatted(tableName));
            return true;
        } catch (BadSqlGrammarException e) {
            return false;
        }
    }
}
