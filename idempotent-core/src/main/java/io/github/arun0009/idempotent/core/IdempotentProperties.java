package io.github.arun0009.idempotent.core;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;

/**
 * Configuration properties for the idempotent library.
 * These properties can be configured in application.properties or application.yml.
 * <p>
 * Example:
 * <pre>
 * idempotent.key.header=X-Idempotency-Key
 * idempotent.inprogress.max.retries=5
 * idempotent.inprogress.retry.initial.intervalMillis=100
 * idempotent.inprogress.retry.multiplier=2
 * </pre>
 */
@ConfigurationProperties(prefix = "idempotent")
public class IdempotentProperties {

    /**
     * HTTP header name used to extract the idempotency key from incoming requests.
     * Clients can send this header to specify their own idempotency key.
     */
    @Name("key.header")
    private String keyHeader = "X-Idempotency-Key";

    /**
     * Configuration for handling in-progress requests (duplicate concurrent requests).
     */
    private InProgress inprogress = new InProgress();

    public String getKeyHeader() {
        return keyHeader;
    }

    public void setKeyHeader(String keyHeader) {
        this.keyHeader = keyHeader;
    }

    public InProgress getInprogress() {
        return inprogress;
    }

    public void setInprogress(InProgress inprogress) {
        this.inprogress = inprogress;
    }

    /**
     * Configuration for retry behavior when a duplicate request arrives
     * while the original request is still in progress.
     */
    public static class InProgress {

        /**
         * Maximum number of retry attempts when waiting for an in-progress request to complete.
         * After this many retries, the request will be treated as a new request.
         */
        @Name("max.retries")
        private int maxRetries = 5;

        /**
         * Initial interval in milliseconds between retry attempts.
         * This is the base delay before applying the exponential backoff multiplier.
         */
        @Name("retry.initial.intervalMillis")
        private int retryInitialIntervalMillis = 100;

        /**
         * Multiplier for exponential backoff between retry attempts.
         * Each subsequent retry will wait: initialInterval + (multiplier ^ attemptNumber) ms.
         */
        @Name("retry.multiplier")
        private int retryMultiplier = 2;

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public int getRetryInitialIntervalMillis() {
            return retryInitialIntervalMillis;
        }

        public void setRetryInitialIntervalMillis(int retryInitialIntervalMillis) {
            this.retryInitialIntervalMillis = retryInitialIntervalMillis;
        }

        public int getRetryMultiplier() {
            return retryMultiplier;
        }

        public void setRetryMultiplier(int retryMultiplier) {
            this.retryMultiplier = retryMultiplier;
        }
    }
}
