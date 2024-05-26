package io.github.arun0009.idempotent.core.aspect;

import io.github.arun0009.idempotent.core.annotation.Idempotent;
import io.github.arun0009.idempotent.core.exception.IdempotentException;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Idempotent aspect Implementation
 */
@Aspect
public class IdempotentAspect {

    // header in request to check for idempotent key
    @Value("${idempotent.key.header:X-Idempotency-Key}")
    private String idempotentKeyHeader;

    // in progress request max reties to try (useful if duplicate requests are made concurrently, only lets one win)
    // defaults to 5.
    @Value("${idempotent.inprogress.max.retries:5}")
    private int inprogressMaxRetries;

    // in progress status check retry initial interval, defaults to 100 ms.
    @Value("${idempotent.inprogress.retry.initial.intervalMillis:100}")
    private int inprogressRetryInterval;

    // in progress retry multiplier for exponential backoff retries, default 2
    @Value("${idempotent.inprogress.retry.multiplier:2}")
    private int inprogressRetryMultiplier;

    private final IdempotentStore idempotentStore;

    /**
     * Create the SpEL parser.
     */
    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * Instantiates a new Idempotent aspect with a given store (look at idempotent-redis and idempotent-dynamo implementations).
     * You can also create your own store and pass it to this Aspect.
     *
     * @param idempotentStore the idempotent store
     */
    public IdempotentAspect(IdempotentStore idempotentStore) {
        this.idempotentStore = idempotentStore;
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
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        String key = getIdempotentKey(pjp);
        if (key == null || key.isEmpty()) {
            return pjp.proceed();
        }

        String processName = getProcessName(pjp);
        long ttlInSeconds = ((MethodSignature) pjp.getSignature())
                .getMethod()
                .getAnnotation(Idempotent.class)
                .ttlInSeconds();
        key = hashKeyIfRequired(key, pjp);

        IdempotentStore.IdempotentKey idempotentKey = new IdempotentStore.IdempotentKey(key, processName);
        IdempotentStore.Value value =
                idempotentStore.getValue(idempotentKey, ((MethodSignature) pjp.getSignature()).getReturnType());

        if (isExistingRequest(value)) {
            return handleExistingRequest(pjp, idempotentKey, value, ttlInSeconds);
        }

        return handleNewRequest(pjp, idempotentKey, ttlInSeconds);
    }

    /**
     * get idempotent key, first preference is to check header so client can dictate what is idempotent or else get
     * it from annotation key.
     * @param pjp Spring's Proceed Joint Point
     * @return value of key as String
     */
    private String getIdempotentKey(ProceedingJoinPoint pjp) {
        String key = getIdempotentKeyFromHeader();
        if (key == null || key.isEmpty()) {
            MethodSignature signature = (MethodSignature) pjp.getSignature();
            key = getIdempotentKeyFromAnnotation(
                    pjp, signature.getMethod().getAnnotation(Idempotent.class).key(), signature);
        }
        return key;
    }

    // gets Idempotent Key from request header default X-Idempotency-Key
    private String getIdempotentKeyFromHeader() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest().getHeader(idempotentKeyHeader) : null;
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

            Object value = parser.parseExpression(key).getValue(context);
            return value != null ? convertToString(value) : null;
        }
        return null;
    }

    private String convertToString(Object value) {
        if (value instanceof String) {
            return (String) value;
        } else {
            // Customize conversion logic as needed
            return value.toString(); // Fallback conversion
        }
    }

    /**
     * Hash key if hashKey arg is set to true, usually if idempotent key is entire entity it's a good idea to hash
     * the entity key.
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
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(key.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte hashByte : hashBytes) {
                String hex = Integer.toHexString(0xff & hashByte);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        }
        return key;
    }

    /**
     * check if request is existing request.
     * @param value idempotent value
     * @return true if its an existing INPROGRESS request.
     */
    private boolean isExistingRequest(IdempotentStore.Value value) {
        return value != null && Instant.now().isBefore(Instant.ofEpochMilli(value.expirationTimeInMilliSeconds()));
    }

    /**
     * Maybe a request is already in progress and this is a duplicate request, exponentially backoff
     * and retry and get response from first call. If status doesn't change from INPROGRESS
     * fpr a given timeout delete the key.
     *
     * @param pjp Springs Proceed Joint Point
     * @param idempotentKey idempotent key for given request
     * @param value response value along with status and expiry time
     * @param ttlInSeconds how long should this idempotent request/response be stored
     * @return Object which is the response.
     * @throws IdempotentException idempotent exception with message
     */
    private Object handleExistingRequest(
            ProceedingJoinPoint pjp,
            IdempotentStore.IdempotentKey idempotentKey,
            IdempotentStore.Value value,
            long ttlInSeconds)
            throws IdempotentException {
        try {
            if (value.status().equals(IdempotentStore.Status.INPROGRESS.name())) {
                value = waitForCompletion(idempotentKey, value);
            }
            if (value.status().equals(IdempotentStore.Status.COMPLETED.name())) {
                return value.response();
            } else {
                idempotentStore.remove(idempotentKey);
            }
            return handleNewRequest(pjp, idempotentKey, ttlInSeconds);
        } catch (Exception e) {
            throw new IdempotentException("error handling existing request", e);
        }
    }

    /**
     * If a request is in progress wait for it to complete for a given period before giving up and deleting.
     *
     * @param idempotentKey idempotent key for given request
     * @param value response value along with status and expiry time
     * @return value response value along with status and expiry time
     * @throws IdempotentException idempotent exception with message
     */
    private IdempotentStore.Value waitForCompletion(
            IdempotentStore.IdempotentKey idempotentKey, IdempotentStore.Value value) throws IdempotentException {
        int retries = 0;
        while (retries < inprogressMaxRetries && !value.status().equals(IdempotentStore.Status.COMPLETED.name())) {
            try {
                TimeUnit.MILLISECONDS.sleep(
                        (long) inprogressRetryInterval * (int) Math.pow(inprogressRetryMultiplier, retries));
            } catch (InterruptedException e) {
                throw new IdempotentException("error waiting for completion", e);
            }
            retries++;
            value = idempotentStore.getValue(idempotentKey, value.getClass());
        }
        return value;
    }

    /**
     * handles new request (with no entry in idempotent table/cache)
     * @param pjp Springs Proceed Joint Point
     * @param idempotentKey idempotent key for given request
     * @param ttlInSeconds how long should this idempotent request/response be stored
     * @return Object which is the response.
     * @throws IdempotentException idempotent exception with message
     */
    private Object handleNewRequest(
            ProceedingJoinPoint pjp, IdempotentStore.IdempotentKey idempotentKey, long ttlInSeconds)
            throws IdempotentException {
        long expiryTimeInMilliseconds = Instant.now().toEpochMilli() + (ttlInSeconds * 1000);
        idempotentStore.store(
                idempotentKey,
                new IdempotentStore.Value(IdempotentStore.Status.INPROGRESS.name(), expiryTimeInMilliseconds, null));

        Object response;
        try {
            response = pjp.proceed();
        } catch (Throwable e) {
            idempotentStore.remove(idempotentKey);
            throw new IdempotentException("error calling downstream api", e);
        }

        updateStoreWithResponse(idempotentKey, response, expiryTimeInMilliseconds);
        return response;
    }

    /**
     * Update response in memory/table with response, status
     * @param idempotentKey idempotent key for given request
     * @param response of downstream api
     * @param expiryTimeInMilliseconds time after which this record/entry can be deleted
     */
    private void updateStoreWithResponse(
            IdempotentStore.IdempotentKey idempotentKey, Object response, long expiryTimeInMilliseconds) {
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
     * @param pjp Springs Proceed Joint Point
     * @return process name
     */
    private String getProcessName(ProceedingJoinPoint pjp) {
        return String.format(
                "__%s.%s()",
                pjp.getTarget().getClass().getSimpleName(), pjp.getSignature().getName());
    }
}
