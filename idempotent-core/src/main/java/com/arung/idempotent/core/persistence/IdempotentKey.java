package com.arung.idempotent.core.persistence;

public record IdempotentKey(String key, String processName) {}
