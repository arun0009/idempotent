package io.github.arun0009.idempotent.rds;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RdsSchemaStatementsTest {

    @Test
    void rendersMySql() {
        assertEquals(List.of("""
                        CREATE TABLE IF NOT EXISTS idempotent (
                            key_id VARCHAR(255) NOT NULL,
                            process_name VARCHAR(255) NOT NULL,
                            status VARCHAR(50) NOT NULL,
                            expires_at BIGINT NOT NULL,
                            response MEDIUMTEXT,
                            attributes MEDIUMTEXT,
                            PRIMARY KEY (key_id, process_name),
                            KEY idx_idempotent_expires_at (expires_at)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"""), RdsSchemaStatements.ddl(RdsDialect.MYSQL, "idempotent"));
    }

    @Test
    void rendersAttributesMigration() {
        assertEquals(
                "ALTER TABLE idempotent ADD COLUMN IF NOT EXISTS attributes TEXT",
                RdsSchemaStatements.addAttributesColumn(RdsDialect.POSTGRES, "idempotent"));
        assertEquals(
                "ALTER TABLE idempotent ADD COLUMN IF NOT EXISTS attributes MEDIUMTEXT",
                RdsSchemaStatements.addAttributesColumn(RdsDialect.MYSQL, "idempotent"));
    }

    @ParameterizedTest
    @EnumSource(
            value = RdsDialect.class,
            names = {"POSTGRES", "H2"})
    void rendersPostgresAndH2(RdsDialect dialect) {
        assertEquals(
                List.of("""
                        CREATE TABLE IF NOT EXISTS idempotent (
                            key_id VARCHAR(255) NOT NULL,
                            process_name VARCHAR(255) NOT NULL,
                            status VARCHAR(50) NOT NULL,
                            expires_at BIGINT NOT NULL,
                            response TEXT,
                            attributes TEXT,
                            PRIMARY KEY (key_id, process_name)
                        )""", "CREATE INDEX IF NOT EXISTS idx_idempotent_expires_at ON idempotent (expires_at)"),
                RdsSchemaStatements.ddl(dialect, "idempotent"));
    }

    @Test
    void rejectsGeneric() {
        assertThrows(IllegalArgumentException.class, () -> RdsSchemaStatements.ddl(RdsDialect.GENERIC, "idempotent"));
    }

    @Test
    void indexNameFromQualifiedTable() {
        var statements = RdsSchemaStatements.ddl(RdsDialect.POSTGRES, "audit.idempotent");

        assertEquals(
                "CREATE INDEX IF NOT EXISTS idx_audit_idempotent_expires_at ON audit.idempotent (expires_at)",
                statements.get(1));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "idempotent;DROP TABLE users",
                "idem potent",
                "1idempotent",
                "idem-potent",
                "",
                "audit.",
                ".idempotent",
                "audit..idempotent",
                "a.b.c.d"
            })
    void rejectsInvalidTableName(String tableName) {
        assertThrows(IllegalArgumentException.class, () -> RdsSchemaStatements.validateIdentifier(tableName));
    }

    @ParameterizedTest
    @ValueSource(strings = {"idempotent_keys_2", "audit.idempotent", "catalog.schema.table"})
    void acceptsValidTableName(String tableName) {
        assertEquals(tableName, RdsSchemaStatements.validateIdentifier(tableName));
    }
}
