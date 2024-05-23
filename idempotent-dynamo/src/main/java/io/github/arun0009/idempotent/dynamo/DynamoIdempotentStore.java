package io.github.arun0009.idempotent.dynamo;

import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest;

/**
 * Dynamo idempotent store.
 */
public class DynamoIdempotentStore implements IdempotentStore {

    private final DynamoDbEnhancedClient dynamoEnhancedClient;

    /**
     * Instantiates a new Dynamo idempotent store.
     *
     * @param dynamoEnhancedClient the dynamo v2 enhanced client
     */
    public DynamoIdempotentStore(DynamoDbEnhancedClient dynamoEnhancedClient) {
        this.dynamoEnhancedClient = dynamoEnhancedClient;
    }

    private DynamoDbTable<IdempotentItem> getTable() {
        return dynamoEnhancedClient.table("Idempotent", TableSchema.fromBean(IdempotentItem.class));
    }

    @Override
    public Value getValue(IdempotentKey idempotentKey) {
        DynamoDbTable<IdempotentItem> idempotentTable = getTable();
        Key dynamoKey = Key.builder()
                .partitionValue(idempotentKey.key())
                .sortValue(idempotentKey.processName())
                .build();
        IdempotentItem idempotentItem = idempotentTable.getItem(dynamoKey);
        if (idempotentItem == null) {
            return null;
        }
        return new Value(
                idempotentItem.getStatus(),
                idempotentItem.getExpirationTimeInMilliSeconds(),
                idempotentItem.getResponse());
    }

    @Override
    public void store(IdempotentKey idempotentKey, Value value) {
        DynamoDbTable<IdempotentItem> idempotentTable = getTable();
        IdempotentItem idempotentItem = new IdempotentItem();
        idempotentItem.setKey(idempotentKey.key());
        idempotentItem.setProcessName(idempotentKey.processName());
        idempotentItem.setStatus(idempotentItem.getStatus());
        idempotentItem.setExpirationTimeInMilliSeconds(idempotentItem.getExpirationTimeInMilliSeconds());
        idempotentItem.setResponse(value.response());
        idempotentTable.putItem(idempotentItem);
    }

    @Override
    public void remove(IdempotentKey idempotentKey) {
        DynamoDbTable<IdempotentItem> idempotentTable = getTable();
        Key dynamoKey = Key.builder()
                .partitionValue(idempotentKey.key())
                .sortValue(idempotentKey.processName())
                .build();
        DeleteItemEnhancedRequest deleteRequest =
                DeleteItemEnhancedRequest.builder().key(dynamoKey).build();
        idempotentTable.deleteItem(deleteRequest);
    }

    @Override
    public void update(IdempotentKey idempotentKey, Value value) {
        DynamoDbTable<IdempotentItem> idempotentTable = getTable();
        IdempotentItem idempotentItem = new IdempotentItem();
        idempotentItem.setKey(idempotentKey.key());
        idempotentItem.setProcessName(idempotentKey.processName());
        idempotentItem.setStatus(idempotentItem.getStatus());
        idempotentItem.setExpirationTimeInMilliSeconds(idempotentItem.getExpirationTimeInMilliSeconds());
        idempotentItem.setResponse(value.response());
        idempotentTable.updateItem(idempotentItem);
    }
}
