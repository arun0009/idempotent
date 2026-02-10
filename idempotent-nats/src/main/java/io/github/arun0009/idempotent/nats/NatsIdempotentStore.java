package io.github.arun0009.idempotent.nats;

import io.github.arun0009.idempotent.core.exception.IdempotentKeyConflictException;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.nats.client.JetStreamApiException;
import io.nats.client.KeyValue;
import io.nats.client.MessageTtl;
import io.nats.client.api.KeyValueEntry;
import io.nats.client.support.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

class NatsIdempotentStore implements IdempotentStore {
    private static final Logger log = LoggerFactory.getLogger(NatsIdempotentStore.class);
    private final KeyValue kv;
    private final JsonMapper mapper;

    NatsIdempotentStore(KeyValue kv, JsonMapper jsonMapper) {
        this.kv = kv;
        this.mapper = jsonMapper;
    }

    private static MessageTtl fromExpirationTimeInMs(long value) {
        int ttl = (int) Duration.ofMillis(value - Instant.now().toEpochMilli()).toSeconds();
        return MessageTtl.seconds(ttl + 1);
    }

    /**
     * Encodes the provided key using Base64 encoding if it is deemed invalid, according to the
     * validation logic defined in `Validator.notWildcardKvKey`. If the key is valid, it is returned
     * unchanged.
     *
     * @param ik the key to be validated and potentially encoded
     * @return the original key if it is valid, otherwise the Base64-encoded version of the key
     */
    static String encodeIfNotValid(IdempotentKey ik) {
        String key = ik.key();
        Base64.Encoder encoder = Base64.getEncoder();
        if (Validator.notWildcardKvKey(key)) {
            log.atDebug().log("Key '{}' is not valid, encoding it", key);
            key = encoder.encodeToString(key.getBytes(UTF_8));
        }
        // processName always includes characters that are not allowed
        return key + "." + encoder.encodeToString(ik.processName().getBytes(UTF_8));
    }

    @Override
    public Value getValue(IdempotentKey idemKey, Class<?> returnType) {
        try {
            log.atDebug().log("Getting key {}", idemKey);
            var key = encodeIfNotValid(idemKey);
            KeyValueEntry entry = kv.get(key);
            if (entry == null) return null;

            Wrappers.Value wrapperValue = mapper.readValue(entry.getValue(), Wrappers.Value.class);
            return wrapperValue.value();
        } catch (IOException | JetStreamApiException e) {
            log.error("Error reading value from nats store", e);
            return null;
        }
    }

    @Override
    public void store(IdempotentKey idemKey, Value value) {
        log.atDebug().log("Storing key {}", idemKey);
        var key = encodeIfNotValid(idemKey);
        try {
            byte[] content = mapper.writeValueAsBytes(new Wrappers.Value(value));
            MessageTtl messageTtl = fromExpirationTimeInMs(value.expirationTimeInMilliSeconds());
            kv.create(key, content, messageTtl);
        } catch (JetStreamApiException e) {
            // Wrong last sequence, the key already exists.
            if (e.getApiErrorCode() == 10071) {
                throw new IdempotentKeyConflictException("NATS Key already exists: " + key, idemKey);
            }
            throw new NatsIdempotentExceptions("Api error storing value in nats", e);
        } catch (IOException e) {
            throw new NatsIdempotentExceptions("Error storing value in nats", e);
        }
    }

    @Override
    public void remove(IdempotentKey idemKey) {
        try {
            log.atDebug().log("Removing key {}", idemKey);
            var key = encodeIfNotValid(idemKey);
            kv.delete(key);
        } catch (JetStreamApiException | IOException e) {
            throw new NatsIdempotentExceptions("Error removing value from nats store", e);
        }
    }

    @Override
    public void update(IdempotentKey idemKey, Value value) {
        try {
            log.atDebug().log("Updating key {} with status {}", idemKey, value.status());
            log.atTrace().log(value::toString);
            var key = encodeIfNotValid(idemKey);
            byte[] content = mapper.writeValueAsBytes(new Wrappers.Value(value));
            kv.put(key, content);
        } catch (IOException | JetStreamApiException e) {
            throw new NatsIdempotentExceptions("Error storing value in nats", e);
        }
    }
}
