package com.arung.idempotent.core.persistence;

public interface IdempotentStore {

    Value getValue(IdempotentKey key);

    void store(IdempotentKey key, Value value);

    void remove(IdempotentKey key);

    void update(IdempotentKey key, Value value);

    record IdempotentKey(String key, String processName) {}

    record Value(String status, Long expirationTimeInMilliSeconds, Object response) {}

    enum Status {
        INPROGRESS("INPROGRESS"), COMPLETED("COMPLETED");

        private final String status;

        Status(String status) {
            this.status = status;
        }

        public String toString() {
            return status;
        }
    }
}