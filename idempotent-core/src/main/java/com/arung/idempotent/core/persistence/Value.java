package com.arung.idempotent.core.persistence;

public record Value(String status, Long expirationTimeInMilliSeconds, Object response) {
}
