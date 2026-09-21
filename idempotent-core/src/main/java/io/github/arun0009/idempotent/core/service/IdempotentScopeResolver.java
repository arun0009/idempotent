package io.github.arun0009.idempotent.core.service;

/**
 * Provides a scope to distinguish callers using the same idempotency key, so they do not receive
 * each other's cached result. For example, scopes {@code caller-a} and {@code caller-b} with key
 * {@code request-123} produce separate entries {@code caller-a_request-123} and {@code caller-b_request-123}.
 *
 */
public interface IdempotentScopeResolver {

    IdempotentScopeResolver NOOP = () -> "";

    /**
     * Called once per idempotent execution, before any store access. Implementations should resolve
     * the current caller on each invocation and be safe for concurrent calls.
     *
     * @return a non-null scope; blank means no caller scoping. Applications requiring isolation
     * must return a non-blank scope or throw.
     */
    String resolveScope();
}
