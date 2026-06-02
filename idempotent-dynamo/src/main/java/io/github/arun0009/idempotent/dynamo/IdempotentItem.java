package io.github.arun0009.idempotent.dynamo;

import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.time.Instant;

/**
 * DynamoDB persistence model for idempotent entries.
 *
 * <p>{@code expiresAtEpochSeconds} is the sole expiry field (epoch seconds) and is used for DynamoDB TTL.
 */
@DynamoDbBean
public class IdempotentItem {
    private String key = "";
    private String processName = "";
    private IdempotentStore.Status status = IdempotentStore.Status.IN_PROGRESS;
    private Long expiresAtEpochSeconds = 0L;
    private @Nullable String response;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("key")
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("processName")
    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    @DynamoDbAttribute("status")
    public IdempotentStore.Status getStatus() {
        return status;
    }

    public void setStatus(IdempotentStore.Status status) {
        this.status = status;
    }

    /** Epoch-second expiry; also the DynamoDB TTL attribute. */
    @DynamoDbAttribute("expiresAtEpochSeconds")
    public Long getExpiresAtEpochSeconds() {
        return expiresAtEpochSeconds;
    }

    public void setExpiresAtEpochSeconds(Long expiresAtEpochSeconds) {
        this.expiresAtEpochSeconds = expiresAtEpochSeconds;
    }

    /** Convenience setter; not persisted (the persisted attribute is {@link #expiresAtEpochSeconds}). */
    @DynamoDbIgnore
    public void setExpiresAt(Instant expiresAt) {
        this.expiresAtEpochSeconds = expiresAt.getEpochSecond();
    }

    /** Convenience getter; not persisted (the persisted attribute is {@link #expiresAtEpochSeconds}). */
    @DynamoDbIgnore
    public Instant getExpiresAt() {
        return Instant.ofEpochSecond(expiresAtEpochSeconds);
    }

    @DynamoDbAttribute("response")
    public @Nullable String getResponse() {
        return response;
    }

    public void setResponse(@Nullable String response) {
        this.response = response;
    }

    @Override
    public String toString() {
        return "IdempotentItem{key='%s', processName='%s', status='%s', expiresAtEpochSeconds=%d, response='%s'}"
                .formatted(key, processName, status, expiresAtEpochSeconds, response);
    }
}
