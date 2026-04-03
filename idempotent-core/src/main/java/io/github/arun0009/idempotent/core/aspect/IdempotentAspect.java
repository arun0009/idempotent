package io.github.arun0009.idempotent.core.aspect;

import io.github.arun0009.idempotent.core.IdempotentProperties;
import io.github.arun0009.idempotent.core.annotation.Idempotent;
import io.github.arun0009.idempotent.core.exception.IdempotentException;
import io.github.arun0009.idempotent.core.exception.IdempotentKeyConflictException;
import io.github.arun0009.idempotent.core.exception.IdempotentWaitExhaustedException;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.github.arun0009.idempotent.core.retry.IdempotentCompletionAwaiter;
import io.github.arun0009.idempotent.core.retry.WaitStrategy;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Contract;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Idempotent aspect Implementation
 */
@Aspect
public class IdempotentAspect {
    private static final Logger log = LoggerFactory.getLogger(IdempotentAspect.class);
    private final IdempotentCompletionAwaiter completionAwaiter;
    private final ExpressionParser parser;
    private final IdempotentStore idempotentStore;
    private final String idempotentKeyHeader;

    /**
     * Instantiates a new Idempotent aspect with a given store and configuration.
     *
     * @param idempotentStore the idempotent store
     * @param properties      core properties
     */
    public IdempotentAspect(IdempotentStore idempotentStore, IdempotentProperties properties) {
        this.idempotentKeyHeader = properties.keyHeader();
        this.idempotentStore = idempotentStore;
        this.parser = new SpelExpressionParser();
        var inprogress = properties.inprogress();
        this.completionAwaiter = new IdempotentCompletionAwaiter(
                idempotentStore,
                new WaitStrategy(
                        inprogress.maxRetries(),
                        Duration.ofMillis(inprogress.retryInitialIntervalMillis()),
                        inprogress.retryMultiplier()));
    }

    /**
     * Around - wrap logic around the api. We first store idempotent key along with Process Name
     * which is combination of ControllerName.methodName() as key and status as INPROGRESS.
     * <p>
     * Once we get response from server we update the record with status as COMPLETED along with response
     * received from server.
     *
     * @param pjp the pjp
     * @return the object
     * @throws Throwable the throwable
     */
    @Around("@annotation(io.github.arun0009.idempotent.core.annotation.Idempotent)")
    public @Nullable Object around(ProceedingJoinPoint pjp) throws Throwable {
        String key = getIdempotentKey(pjp);
        if (key == null || key.isEmpty()) {
            return pjp.proceed();
        }

        var processName = getProcessName(pjp);
        var idempotentAnnotation =
                ((MethodSignature) pjp.getSignature()).getMethod().getAnnotation(Idempotent.class);

        var ttl = Duration.parse(idempotentAnnotation.duration());

        key = hashKeyIfRequired(key, pjp);

        var idempotentKey = new IdempotentStore.IdempotentKey(key, processName);
        IdempotentStore.@Nullable Value value =
                idempotentStore.getValue(idempotentKey, ((MethodSignature) pjp.getSignature()).getReturnType());

        if (isExistingRequest(value)) {
            log.atDebug().log("Idempotent key {} already exists", key);
            return handleExistingRequest(idempotentKey, value);
        }

        try {
            log.atDebug().log("Idempotent key {} does not exist, creating new entry", key);
            return handleNewRequest(pjp, idempotentKey, ttl);
        } catch (IdempotentKeyConflictException e) {
            log.info("Key conflict for: {}. Refetching value to handle as existing request", e.getKey());
            value = idempotentStore.getValue(idempotentKey, ((MethodSignature) pjp.getSignature()).getReturnType());
            if (value == null) {
                // Possible race condition: key expired in the meantime
                return handleNewRequest(pjp, idempotentKey, ttl);
            }

            return handleExistingRequest(idempotentKey, value);
        }
    }

    /**
     * get idempotent key, first preference is to check header so client can dictate what is idempotent or else get
     * it from annotation key.
     *
     * @param pjp Spring's Proceed Joint Point
     * @return value of key as String
     */
    private @Nullable String getIdempotentKey(ProceedingJoinPoint pjp) {
        String key = getIdempotentKeyFromHeader();
        if (key == null || key.isEmpty()) {
            MethodSignature signature = (MethodSignature) pjp.getSignature();
            key = getIdempotentKeyFromAnnotation(
                    pjp, signature.getMethod().getAnnotation(Idempotent.class).key(), signature);
        }
        return key;
    }

    // gets Idempotent Key from request header default X-Idempotency-Key
    private @Nullable String getIdempotentKeyFromHeader() {
        var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest().getHeader(idempotentKeyHeader) : null;
    }

    // gets Idempotent Key value from Spring Expression SpEL.
    private @Nullable String getIdempotentKeyFromAnnotation(
            ProceedingJoinPoint pjp, String key, MethodSignature signature) {
        // Evaluate the SpEL expression if the key is specified
        if (!key.isEmpty()) {
            var context = new StandardEvaluationContext();
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

            Object value = parser.parseExpression(key).getValue(context);
            return value != null ? convertToString(value) : null;
        }
        return null;
    }

    private String convertToString(Object value) {
        if (value instanceof String str) {
            return str;
        }

        // Customize conversion logic as needed
        return value.toString(); // Fallback conversion
    }

    /**
     * Hash key if hashKey arg is set to true, usually if idempotent key is entire entity it's a good idea to hash
     * the entity key.
     *
     * @param key key to hash
     * @param pjp Springs Proceed Joint Point
     * @return hashed key as String
     * @throws NoSuchAlgorithmException throw exception if sha-256 algorithm is not found
     */
    private String hashKeyIfRequired(String key, ProceedingJoinPoint pjp) throws NoSuchAlgorithmException {
        if (((MethodSignature) pjp.getSignature())
                .getMethod()
                .getAnnotation(Idempotent.class)
                .hashKey()) {
            var md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        }
        return key;
    }

    /**
     * check if request is existing request.
     *
     * @param value idempotent value
     * @return true if its an existing INPROGRESS request.
     */
    @Contract("null -> false")
    private boolean isExistingRequest(IdempotentStore.@Nullable Value value) {
        return value != null && Instant.now().isBefore(Instant.ofEpochMilli(value.expirationTimeInMilliSeconds()));
    }

    /**
     * Maybe a request is already in progress and this is a duplicate request, exponentially backoff
     * and retry and get response from first call. If status doesn't change from INPROGRESS
     * fpr a given timeout delete the key.
     *
     * @param idempotentKey idempotent key for given request
     * @param existingValue response value along with status and expiry time
     * @return Object which is the response.
     * @throws IdempotentWaitExhaustedException if wait times out
     */
    private @Nullable Object handleExistingRequest(
            IdempotentStore.IdempotentKey idempotentKey, IdempotentStore.Value existingValue) {
        if (IdempotentStore.Status.INPROGRESS.is(existingValue.status())) {
            existingValue = completionAwaiter.wait(idempotentKey, existingValue);
        }

        if (existingValue != null && IdempotentStore.Status.COMPLETED.is(existingValue.status())) {
            return existingValue.response();
        }

        idempotentStore.remove(idempotentKey);
        throw new IdempotentWaitExhaustedException(
                "Operation wait exhausted in progress after multiple retries", idempotentKey);
    }

    /**
     * Handles a new request (with no entry in idempotent table/cache).
     *
     * @param pjp           Spring's ProceedingJoinPoint for method execution
     * @param idempotentKey the idempotent key for the request (must not be null)
     * @param ttl           the time to live for the idempotent request/response (must not be null)
     * @return the response from the method execution
     * @throws IdempotentException  if an error occurs during request processing
     * @throws NullPointerException if any parameter is null
     */
    private @Nullable Object handleNewRequest(
            ProceedingJoinPoint pjp, IdempotentStore.IdempotentKey idempotentKey, Duration ttl) throws Throwable {
        long expiryTimeInMilliseconds = Instant.now().plus(ttl).toEpochMilli();
        idempotentStore.store(
                idempotentKey,
                new IdempotentStore.Value(IdempotentStore.Status.INPROGRESS.name(), expiryTimeInMilliseconds, null));

        try {
            Object response = pjp.proceed();
            updateStoreWithResponse(idempotentKey, response, expiryTimeInMilliseconds);
            return response;
        } catch (Throwable e) {
            idempotentStore.remove(idempotentKey);
            throw e;
        }
    }

    /**
     * Update response in memory/table with response, status
     *
     * @param idempotentKey            idempotent key for given request
     * @param response                 of downstream api
     * @param expiryTimeInMilliseconds time after which this record/entry can be deleted
     */
    private void updateStoreWithResponse(
            IdempotentStore.IdempotentKey idempotentKey, @Nullable Object response, long expiryTimeInMilliseconds) {
        if (response instanceof ResponseEntity<?> responseEntity
                && !responseEntity.getStatusCode().is2xxSuccessful()) {
            idempotentStore.remove(idempotentKey);
        } else if (response != null) {
            idempotentStore.update(
                    idempotentKey,
                    new IdempotentStore.Value(
                            IdempotentStore.Status.COMPLETED.name(), expiryTimeInMilliseconds, response));
        } else {
            idempotentStore.remove(idempotentKey);
        }
    }

    /**
     * get process name which is __ControllerName.methodName()
     *
     * @param pjp Springs Proceed Joint Point
     * @return process name
     */
    private String getProcessName(ProceedingJoinPoint pjp) {
        return "__%s.%s()"
                .formatted(
                        pjp.getTarget().getClass().getSimpleName(),
                        pjp.getSignature().getName());
    }
}
