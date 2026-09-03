package io.github.arun0009.idempotent.rds;

import io.github.arun0009.idempotent.core.exception.IdempotentException;
import io.github.arun0009.idempotent.core.exception.IdempotentKeyConflictException;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.github.arun0009.idempotent.core.serialization.IdempotentPayloadCodec;
import io.github.arun0009.idempotent.core.serialization.IdempotentPayloadCodecException;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Map;

/**
 * RDS idempotent store using JdbcTemplate with atomic race condition protection.
 */
public class RdsIdempotentStore implements IdempotentStore {

    private final JdbcTemplate jdbcTemplate;
    private final String tableName;
    private final IdempotentPayloadCodec payloadCodec;

    public RdsIdempotentStore(JdbcTemplate jdbcTemplate, String tableName, IdempotentPayloadCodec payloadCodec) {
        this.jdbcTemplate = jdbcTemplate;
        this.tableName = tableName;
        this.payloadCodec = payloadCodec;
    }

    @Override
    public @Nullable Value loadValue(IdempotentKey key, Class<?> returnType) {
        var sql = """
                SELECT status, expires_at, response, attributes FROM %s WHERE key_id = ? AND process_name = ?
                """.formatted(tableName);
        try {
            var value = jdbcTemplate.queryForObject(
                    sql,
                    (rs, rowNum) -> {
                        var status = IdempotentStore.Status.valueOf(rs.getString("status"));
                        Instant expiresAt = Instant.ofEpochMilli(rs.getLong("expires_at"));
                        String serializedResponse = rs.getString("response");
                        Object response = payloadCodec.deserializeFromString(serializedResponse, returnType);
                        Map<String, String> attributes = deserializeAttributes(rs.getString("attributes"));
                        return new Value(status, expiresAt, response, attributes);
                    },
                    key.key(),
                    key.processName());
            return value;
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> deserializeAttributes(@Nullable String serializedAttributes) {
        if (serializedAttributes == null) {
            return Map.of();
        }
        var attributes = payloadCodec.deserializeFromString(serializedAttributes, Map.class);
        return attributes == null ? Map.of() : (Map<String, String>) attributes;
    }

    @Override
    public void store(IdempotentKey key, Value value) {
        try {
            var serializedResponse = payloadCodec.serializeToString(value.response());
            var serializedAttributes = payloadCodec.serializeToString(value.attributes());
            var sql = """
                    INSERT INTO %s (key_id, process_name, status, expires_at, response, attributes)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """.formatted(tableName);
            jdbcTemplate.update(
                    sql,
                    key.key(),
                    key.processName(),
                    value.status().name(),
                    value.expiresAt().toEpochMilli(),
                    serializedResponse,
                    serializedAttributes);
        } catch (IdempotentPayloadCodecException e) {
            throw new IdempotentException("Error serializing value response", e);
        } catch (DuplicateKeyException e) {
            throw new IdempotentKeyConflictException("Idempotent key already exists", key);
        }
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
            var serializedAttributes = payloadCodec.serializeToString(value.attributes());
            var sql = """
                    UPDATE %s SET status = ?, expires_at = ?, response = ?, attributes = ?
                    WHERE key_id = ? AND process_name = ?
                    """.formatted(tableName);
            jdbcTemplate.update(
                    sql,
                    value.status().name(),
                    value.expiresAt().toEpochMilli(),
                    serializedResponse,
                    serializedAttributes,
                    key.key(),
                    key.processName());
        } catch (IdempotentPayloadCodecException e) {
            throw new IdempotentException("Error serializing response", e);
        }
    }
}
