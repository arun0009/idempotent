package io.github.arun0009.idempotent.nats;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.databind.jsontype.impl.StdTypeResolverBuilder;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.nats.client.JetStreamApiException;
import io.nats.client.KeyValue;
import io.nats.client.MessageTtl;
import io.nats.client.api.KeyValueEntry;
import io.nats.client.support.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

class NatsIdempotentStore implements IdempotentStore {
    private static final Logger log = LoggerFactory.getLogger(NatsIdempotentStore.class);
    private final KeyValue kv;
    private final ObjectMapper mapper;

    NatsIdempotentStore(KeyValue kv) {
        this.kv = kv;
        this.mapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .activateDefaultTyping(LaissezFaireSubTypeValidator.instance)
                .setDefaultTyping(new StdTypeResolverBuilder()
                        .init(JsonTypeInfo.Id.CLASS, null)
                        .inclusion(JsonTypeInfo.As.PROPERTY))
                .findAndAddModules()
                .build();
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
     * @param key the key to be validated and potentially encoded
     * @return the original key if it is valid, otherwise the Base64-encoded version of the key
     */
    static String encodeIfNotValid(String key) {
        if (Validator.notWildcardKvKey(key)) {
            log.atDebug().log("Key '{}' is not valid, encoding it", key);
            return Base64.getEncoder().encodeToString(key.getBytes(UTF_8));
        }

        return key;
    }

    @Override
    public Value getValue(IdempotentKey idemKey, Class<?> returnType) {
        try {
            log.atDebug().log("Getting key {}", idemKey);
            var key = encodeIfNotValid(idemKey.key());
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
        try {
            log.atDebug().log("Storing key {}", idemKey);
            var key = encodeIfNotValid(idemKey.key());
            log.atTrace().log(value::toString);
            byte[] content = mapper.writeValueAsBytes(new Wrappers.Value(value, idemKey.processName()));
            MessageTtl messageTtl = fromExpirationTimeInMs(value.expirationTimeInMilliSeconds());
            kv.create(key, content, messageTtl);
        } catch (IOException | JetStreamApiException e) {
            throw new NatsIdempotentExceptions("Error storing value in nats", e);
        }
    }

    @Override
    public void remove(IdempotentKey idemKey) {
        try {
            log.atDebug().log("Removing key {}", idemKey);
            var key = encodeIfNotValid(idemKey.key());
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
            var key = encodeIfNotValid(idemKey.key());
            byte[] content = mapper.writeValueAsBytes(new Wrappers.Value(value, idemKey.processName()));
            kv.put(key, content);
        } catch (IOException | JetStreamApiException e) {
            throw new NatsIdempotentExceptions("Error storing value in nats", e);
        }
    }
}
