package com.arung.idempotent.core.persistence;

import java.util.concurrent.TimeUnit;

public interface IdempotentStore {

    Value getValue(IdempotentKey key);

    void store(IdempotentKey key, Value value);

    void remove(IdempotentKey key);

    void update(IdempotentKey key, Value value);

    public enum Status {
        INPROGRESS("INPROGRESS"), COMPLETED("COMPLETED"), EXPIRED("EXPIRED");

        private final String status;

        Status(String status) {
            this.status = status;
        }

        public String toString() {
            return status;
        }
    }
}