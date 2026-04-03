package io.github.arun0009.idempotent.rds;

import io.github.arun0009.idempotent.core.exception.IdempotentException;
import io.github.arun0009.idempotent.core.exception.IdempotentKeyConflictException;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.github.arun0009.idempotent.core.serialization.IdempotentPayloadCodec;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * RDS idempotent store using JdbcTemplate with atomic race condition
 * protection.
 */
public class RdsIdempotentStore implements IdempotentStore {

    private final JdbcTemplate jdbcTemplate;
    private final String tableName;
    private final IdempotentPayloadCodec payloadCodec;
    private final RdsDialect dialect;

    public RdsIdempotentStore(JdbcTemplate jdbcTemplate, String tableName, IdempotentPayloadCodec payloadCodec) {
        this.jdbcTemplate = jdbcTemplate;
        this.tableName = tableName;
        this.payloadCodec = payloadCodec;
        this.dialect = RdsDialect.detect(jdbcTemplate);
    }

    @Override
    public @Nullable Value getValue(IdempotentKey key, Class<?> returnType) {
        var sql = """
                SELECT status, expiration_time_millis, response FROM %s WHERE key_id = ? AND process_name = ?
                """.formatted(tableName);
        try {
            return jdbcTemplate.queryForObject(
                    sql,
                    (rs, rowNum) -> {
                        String status = rs.getString("status");
                        long expirationTime = rs.getLong("expiration_time_millis");
                        String serializedResponse = rs.getString("response");
                        Object response = payloadCodec.deserializeFromString(serializedResponse, returnType);
                        return new Value(status, expirationTime, response);
                    },
                    key.key(),
                    key.processName());
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public void store(IdempotentKey key, Value value) {
        try {
            var serializedResponse = payloadCodec.serializeToString(value.response());
            switch (dialect) {
                case POSTGRES -> storePostgres(key, value, serializedResponse);
                case MYSQL -> storeMySQL(key, value, serializedResponse);
                case H2, GENERIC -> storeGeneric(key, value, serializedResponse);
            }
        } catch (IllegalArgumentException e) {
            throw new IdempotentException("Error serializing value response", e);
        } catch (DuplicateKeyException e) {
            throw new IdempotentKeyConflictException("Idempotent key already exists in " + dialect, key);
        }
    }

    private void storePostgres(IdempotentKey key, Value value, @Nullable String serializedResponse) {
        var sql = """
                INSERT INTO %s (key_id, process_name, status, expiration_time_millis, response)
                VALUES (?, ?, ?, ?, ?)
                """.formatted(tableName);
        jdbcTemplate.update(
                sql,
                key.key(),
                key.processName(),
                value.status(),
                value.expirationTimeInMilliSeconds(),
                serializedResponse);
    }

    private void storeMySQL(IdempotentKey key, Value value, @Nullable String serializedResponse) {
        var sql = """
                INSERT INTO %s (key_id, process_name, status, expiration_time_millis, response) VALUES (?, ?, ?, ?, ?)
                """.formatted(tableName);
        jdbcTemplate.update(
                sql,
                key.key(),
                key.processName(),
                value.status(),
                value.expirationTimeInMilliSeconds(),
                serializedResponse);
    }

    private void storeGeneric(IdempotentKey key, Value value, @Nullable String response) {
        var sql = """
                INSERT INTO %s (key_id, process_name, status, expiration_time_millis, response) VALUES (?, ?, ?, ?, ?)
                """.formatted(tableName);
        jdbcTemplate.update(
                sql, key.key(), key.processName(), value.status(), value.expirationTimeInMilliSeconds(), response);
    }

    @Override
    public void remove(IdempotentKey key) {
        var sql = """
                DELETE FROM %s WHERE key_id = ? AND process_name = ?
                """.formatted(tableName);
        jdbcTemplate.update(sql, key.key(), key.processName());
    }

    @Override
    public void update(IdempotentKey key, Value value) {
        try {
            var serializedResponse = payloadCodec.serializeToString(value.response());
            var updateSql = """
                    UPDATE %s SET status = ?, expiration_time_millis = ?, response = ?
                    WHERE key_id = ? AND process_name = ?
                    """.formatted(tableName);
            jdbcTemplate.update(
                    updateSql,
                    value.status(),
                    value.expirationTimeInMilliSeconds(),
                    serializedResponse,
                    key.key(),
                    key.processName());
        } catch (IllegalArgumentException e) {
            throw new IdempotentException("Error serializing response", e);
        }
    }
}
