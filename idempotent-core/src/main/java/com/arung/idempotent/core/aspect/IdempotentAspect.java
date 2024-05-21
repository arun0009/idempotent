package com.arung.idempotent.core.aspect;

import com.arung.idempotent.core.annotation.Idempotent;
import com.arung.idempotent.core.persistence.IdempotentKey;
import com.arung.idempotent.core.persistence.IdempotentStore;
import com.arung.idempotent.core.persistence.Value;
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

import java.time.Instant;

@Aspect
public class IdempotentAspect {

    private final IdempotentStore idempotentStore;

    public IdempotentAspect(IdempotentStore idempotentStore) {
        this.idempotentStore = idempotentStore;
    }

    // Create the SpEL parser
    ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(com.arung.idempotent.core.annotation.Idempotent)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Idempotent idempotent = signature.getMethod().getAnnotation(Idempotent.class);

        String key = idempotent.key();
        String processName = processName(pjp);
        long ttlInSeconds = idempotent.ttlInSeconds();
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

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
                throw new IllegalStateException("Parameter names are not available. Ensure the '-parameters' compiler option is enabled.");
            }
            key = parser.parseExpression(key).getValue(context, String.class);
        }

        // If key is still null or empty, get it from the request header
        if ((key == null || key.isEmpty()) && attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            key = request.getHeader("X-Idempotency-Key");
        }

        IdempotentKey idempotentKey = new IdempotentKey(key, processName);
        Value value = idempotentStore.getValue(idempotentKey);

        if(value != null && value.response() != null) {
            if(value.status().equals(IdempotentStore.Status.COMPLETED.name()))
                if(Instant.now().isBefore(Instant.ofEpochSecond(value.expirationTimeInMilliSeconds()))) {
                    return value.response();
                } else {
                    idempotentStore.remove(idempotentKey);
            }
        }

        Long expiryTimeInMilliseconds = Instant.now().toEpochMilli() +  (ttlInSeconds * 1000);
        idempotentStore.store(idempotentKey, new Value(IdempotentStore.Status.INPROGRESS.name(), expiryTimeInMilliseconds, null));
        Object response;
        try {
            response = pjp.proceed();
        } catch (Exception e) {
            idempotentStore.remove(idempotentKey);
            throw e;
        }

        if (response instanceof ResponseEntity<?> responseEntity) {
            int statusCode = responseEntity.getStatusCode().value();

            // if not successful response remove idempotency key to allow retries
            if (statusCode < 200 || statusCode > 299) {
                idempotentStore.remove(idempotentKey);
            } else {
                idempotentStore.update(idempotentKey, new Value(IdempotentStore.Status.COMPLETED.name(), expiryTimeInMilliseconds, response));
            }
        }
        return response;
    }

    private String processName(ProceedingJoinPoint pjp) {
        return String.format("__%s.%s()", pjp.getTarget().getClass().getSimpleName(), pjp.getSignature().getName());
    }
}
