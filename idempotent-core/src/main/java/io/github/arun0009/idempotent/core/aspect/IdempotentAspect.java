package io.github.arun0009.idempotent.core.aspect;

import io.github.arun0009.idempotent.core.IdempotentProperties;
import io.github.arun0009.idempotent.core.annotation.Idempotent;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.github.arun0009.idempotent.core.service.IdempotentService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AOP aspect that applies idempotency to methods annotated with {@link Idempotent}. Resolves the
 * key (header takes precedence over the SpEL-evaluated annotation key), the TTL, and the process
 * name from the join point, then delegates the state machine to {@link IdempotentService}.
 */
@Aspect
public class IdempotentAspect {

    private static final Logger log = LoggerFactory.getLogger(IdempotentAspect.class);

    private final IdempotentService idempotentService;
    private final ExpressionParser parser;
    private final String idempotentKeyHeader;
    private final Set<Method> warnedEmptyKeyMethods = ConcurrentHashMap.newKeySet();

    public IdempotentAspect(IdempotentService idempotentService, IdempotentProperties properties) {
        this.idempotentService = idempotentService;
        this.idempotentKeyHeader = properties.keyHeader();
        this.parser = new SpelExpressionParser();
    }

    @Around("@annotation(io.github.arun0009.idempotent.core.annotation.Idempotent)")
    public @Nullable Object around(ProceedingJoinPoint pjp) throws Throwable {
        var signature = (MethodSignature) pjp.getSignature();
        var annotation = signature.getMethod().getAnnotation(Idempotent.class);

        String key = resolveKey(pjp, signature, annotation);
        if (key == null || key.isEmpty()) {
            warnEmptyKeyOnce(signature.getMethod());
            return pjp.proceed();
        }

        key = hashKeyIfRequired(key, annotation);
        var ttl = parseDuration(annotation.duration());
        var idempotentKey = new IdempotentStore.IdempotentKey(key, processName(pjp));

        @SuppressWarnings({"unchecked", "rawtypes"})
        Class<Object> returnType = (Class) signature.getReturnType();

        return idempotentService.executeThrowable(idempotentKey, returnType, pjp::proceed, ttl);
    }

    private @Nullable String resolveKey(ProceedingJoinPoint pjp, MethodSignature signature, Idempotent annotation) {
        String key = headerKey();
        if (key != null && !key.isEmpty()) {
            return key;
        }
        return spelKey(pjp, signature, annotation.key());
    }

    private @Nullable String headerKey() {
        var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest().getHeader(idempotentKeyHeader) : null;
    }

    private @Nullable String spelKey(ProceedingJoinPoint pjp, MethodSignature signature, String keyExpression) {
        if (keyExpression.isEmpty()) {
            return null;
        }
        String[] paramNames = signature.getParameterNames();
        if (paramNames == null) {
            throw new IllegalStateException(
                    "Parameter names are not available. Ensure the '-parameters' compiler option is enabled.");
        }
        var context = new StandardEvaluationContext();
        Object[] args = pjp.getArgs();
        for (int i = 0; i < paramNames.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }
        Object value = parser.parseExpression(keyExpression).getValue(context);
        return value != null ? value.toString() : null;
    }

    private static String hashKeyIfRequired(String key, Idempotent annotation) throws NoSuchAlgorithmException {
        if (!annotation.hashKey()) {
            return key;
        }
        var md = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = md.digest(key.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hashBytes);
    }

    /**
     * Parses {@code value} accepting both ISO-8601 ({@code PT1M}) and Spring's short form
     * ({@code 100ms}, {@code 5s}, {@code 1m}).
     */
    private static Duration parseDuration(String value) {
        return DurationStyle.detectAndParse(value);
    }

    private static String processName(ProceedingJoinPoint pjp) {
        return "__%s.%s()"
                .formatted(
                        pjp.getTarget().getClass().getSimpleName(),
                        pjp.getSignature().getName());
    }

    private void warnEmptyKeyOnce(Method method) {
        if (warnedEmptyKeyMethods.add(method)) {
            log.warn(
                    "@Idempotent on {}#{} resolved to an empty key; the method will run without idempotency. "
                            + "Check the SpEL expression or supply the '{}' request header.",
                    method.getDeclaringClass().getName(),
                    method.getName(),
                    idempotentKeyHeader);
        }
    }
}
