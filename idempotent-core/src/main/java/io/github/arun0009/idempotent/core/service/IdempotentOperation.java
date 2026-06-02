package io.github.arun0009.idempotent.core.service;

import org.jspecify.annotations.Nullable;

/**
 * Operation executed by {@link IdempotentService#executeThrowable}. Permits any {@link Throwable},
 * so callers such as the AOP aspect can pass {@code ProceedingJoinPoint::proceed} without losing
 * the original (possibly checked) exception.
 *
 * @param <T> the result type
 */
@FunctionalInterface
public interface IdempotentOperation<T> {

    @Nullable T execute() throws Throwable;
}
