package com.arung.idempotent.core.aspect;

import com.arung.idempotent.core.annotation.Idempotent;
import com.arung.idempotent.core.persistence.IdempotentStore;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

@Aspect
public class IdempotentAspect {

    public static final String X_IDEMPOTENCY_KEY = "X-Idempotency-Key";

    private final IdempotentStore idempotentStore;

    public IdempotentAspect(IdempotentStore idempotentStore) {
        this.idempotentStore = idempotentStore;
    }

    // Create the SpEL parser
    ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(com.arung.idempotent.core.annotation.Idempotent)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        String key = getIdempotentKeyFromHeader();
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Idempotent idempotent = signature.getMethod().getAnnotation(Idempotent.class);

        String keySPEL = idempotent.key();
        boolean hashKey = idempotent.hashKey();
        String processName = processName(pjp);
        long ttlInSeconds = idempotent.ttlInSeconds();

        if (key == null || key.isEmpty()) {
            key = getIdempotentKeyFromAnnotation(pjp, keySPEL, signature);
        }

        if (key != null && !key.isEmpty()) {
            if (hashKey) {
                key = hashIdempotentKey(key);
            }

            IdempotentStore.IdempotentKey idempotentKey = new IdempotentStore.IdempotentKey(key, processName);
            IdempotentStore.Value value = idempotentStore.getValue(idempotentKey);

            if (value != null && value.response() != null) {
                if (value.status().equals(IdempotentStore.Status.COMPLETED.name()))
                    if (Instant.now().isBefore(Instant.ofEpochSecond(value.expirationTimeInMilliSeconds()))) {
                        return value.response();
                    } else {
                        idempotentStore.remove(idempotentKey);
                    }
            }

            Long expiryTimeInMilliseconds = Instant.now().toEpochMilli() + (ttlInSeconds * 1000);
            idempotentStore.store(
                    idempotentKey,
                    new IdempotentStore.Value(
                            IdempotentStore.Status.INPROGRESS.name(), expiryTimeInMilliseconds, null));

            Object response;
            try {
                response = pjp.proceed();
            } catch (Exception e) {
                idempotentStore.remove(idempotentKey);
                throw e;
            }

            if (response instanceof ResponseEntity<?> responseEntity) {
                // if not successful response remove idempotency key to allow retries
                if (!responseEntity.getStatusCode().is2xxSuccessful()) {
                    idempotentStore.remove(idempotentKey);
                } else {
                    idempotentStore.update(
                            idempotentKey,
                            new IdempotentStore.Value(
                                    IdempotentStore.Status.COMPLETED.name(), expiryTimeInMilliseconds, response));
                }
            } else if (response != null) {
                idempotentStore.update(
                        idempotentKey,
                        new IdempotentStore.Value(
                                IdempotentStore.Status.COMPLETED.name(), expiryTimeInMilliseconds, response));
            } else {
                idempotentStore.remove(idempotentKey);
            }
            return response;
        } else {
            return pjp.proceed();
        }
    }

    // gets Idempotent Key from request header X-Idempotency-Key
    private static String getIdempotentKeyFromHeader() {
        // If key is still null or empty, get it from the request header
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String key = null;
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            key = request.getHeader(X_IDEMPOTENCY_KEY);
        }
        return key;
    }

    // gets Idempotent Key value from Spring Expression SpEL.
    private String getIdempotentKeyFromAnnotation(ProceedingJoinPoint pjp, String key, MethodSignature signature) {
        // Evaluate the SpEL expression if the key is specified
        if (key != null && !key.isEmpty()) {
            StandardEvaluationContext context = new StandardEvaluationContext();
            String[] paramNames = signature.getParameterNames();
            Object[] args = pjp.getArgs();
            if (paramNames != null) {
                for (int i = 0; i < paramNames.length; i++) {
                    context.setVariable(paramNames[i], args[i]);
                }
            } else {
                throw new IllegalStateException(
                        "Parameter names are not available. Ensure the '-parameters' compiler option is enabled.");
            }
            key = parser.parseExpression(key).getValue(context, String.class);
        }
        return key;
    }

    // hash idempotentKey
    private static String hashIdempotentKey(String key) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = md.digest(key.getBytes());
        StringBuilder hexString = new StringBuilder();
        for (byte hashByte : hashBytes) {
            String hex = Integer.toHexString(0xff & hashByte);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private String processName(ProceedingJoinPoint pjp) {
        return String.format(
                "__%s.%s()",
                pjp.getTarget().getClass().getSimpleName(), pjp.getSignature().getName());
    }
}
