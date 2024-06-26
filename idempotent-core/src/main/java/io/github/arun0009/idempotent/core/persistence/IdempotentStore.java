package io.github.arun0009.idempotent.core.persistence;

import java.io.Serializable;

/**
 * Idempotent store interface. See RedisIdempotentStore and DynamoIdempotentStore for implementations.
 */
public interface IdempotentStore {

    /**
     * Gets value (response for given key) if exists and avoids calling downstream apis.
     *
     * @param key the idempotentKey
     * @param returnType Class of return type to use for deserialization
     * @return Value the value which contains response
     */
    Value getValue(IdempotentKey key, Class<?> returnType);

    /**
     * Store idempotent key and value which contains response
     *
     * @param key   the idempotentKey
     * @param value the value which contains response
     */
    void store(IdempotentKey key, Value value);

    /**
     * Remove the idempotent entry for given idempotent key
     *
     * @param key the idempotentKey
     */
    void remove(IdempotentKey key);

    /**
     * Updates an idempotent keys value with status and response.
     *
     * @param key   the idempotentKey
     * @param value the value which contains response
     */
    void update(IdempotentKey key, Value value);

    /**
     * Idempotent Key Record
     *
     * @param key idempotent key
     * @param processName this is Controller.methodName()
     */
    record IdempotentKey(String key, String processName) implements Serializable {}

    /**
     * Value record
     *
     * @param status response status which is either INPROGRESS or COMPLETED
     * @param expirationTimeInMilliSeconds expiry time of idempotent entry from store
     * @param response this is response received from downstream apis.
     */
    record Value(String status, Long expirationTimeInMilliSeconds, Object response) implements Serializable {}

    /**
     * The enum Status.
     */
    enum Status {
        /**
         * Inprogress status.
         */
        INPROGRESS("INPROGRESS"),
        /**
         * Completed status.
         */
        COMPLETED("COMPLETED");

        private final String status;

        Status(String status) {
            this.status = status;
        }

        public String toString() {
            return status;
        }
    }
}
