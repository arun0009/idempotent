package io.github.arun0009.idempotent.dynamo;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * Idempotent item is a copy of Idempotent Store's IdempotentKey/Value to store in Dynamo.
 */
@DynamoDbBean
public class IdempotentItem {
    private String key;
    private String processName;
    private String status;
    private Long expirationTimeInMilliSeconds;
    private String response;

    /**
     * Gets idempotent key.
     *
     * @return the idempotent key.
     */
    @DynamoDbPartitionKey
    @DynamoDbAttribute("key")
    public String getKey() {
        return key;
    }

    /**
     * Sets idempotent key.
     *
     * @param key the idempotent key
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * Gets process name.
     *
     * @return the process name
     */
    @DynamoDbSortKey
    @DynamoDbAttribute("processName")
    public String getProcessName() {
        return processName;
    }

    /**
     * Sets process name.
     *
     * @param processName the process name
     */
    public void setProcessName(String processName) {
        this.processName = processName;
    }

    /**
     * Gets status.
     *
     * @return the status
     */
    @DynamoDbAttribute("status")
    public String getStatus() {
        return status;
    }

    /**
     * Sets status.
     *
     * @param status the status
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Gets expiration time of idempotent item in milliseconds.
     *
     * @return the expiration time in milliseconds
     */
    @DynamoDbAttribute("expiryTime")
    public Long getExpirationTimeInMilliSeconds() {
        return expirationTimeInMilliSeconds;
    }

    /**
     * Sets expiration time of idempotent item in milliseconds.
     *
     * @param expirationTimeInMilliSeconds the expiration time in milliseconds
     */
    public void setExpirationTimeInMilliSeconds(Long expirationTimeInMilliSeconds) {
        this.expirationTimeInMilliSeconds = expirationTimeInMilliSeconds;
    }

    /**
     * Gets response stored for given idempotent key.
     *
     * @return the response
     */
    @DynamoDbAttribute("response")
    public String getResponse() {
        return response;
    }

    /**
     * Sets response.
     *
     * @param response the response
     */
    public void setResponse(String response) {
        this.response = response;
    }
}
