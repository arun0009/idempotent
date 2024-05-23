package com.codeweave.idempotent.dynamo;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@DynamoDbBean
public class IdempotentItem {
    private String key;
    private String processName;
    private String status;
    private Long expirationTimeInMilliSeconds;
    private Object response;

    @DynamoDbAttribute("key")
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    @DynamoDbAttribute("processName")
    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    @DynamoDbAttribute("status")
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @DynamoDbAttribute("expiryTime")
    public Long getExpirationTimeInMilliSeconds() {
        return expirationTimeInMilliSeconds;
    }

    public void setExpirationTimeInMilliSeconds(Long expirationTimeInMilliSeconds) {
        this.expirationTimeInMilliSeconds = expirationTimeInMilliSeconds;
    }

    @DynamoDbAttribute("response")
    public Object getResponse() {
        return response;
    }

    public void setResponse(Object response) {
        this.response = response;
    }
}
