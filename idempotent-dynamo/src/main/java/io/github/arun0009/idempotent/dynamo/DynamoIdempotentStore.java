package io.github.arun0009.idempotent.dynamo;

import io.github.arun0009.idempotent.core.exception.IdempotentException;
import io.github.arun0009.idempotent.core.exception.IdempotentKeyConflictException;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Dynamo idempotent store.
 */
public class DynamoIdempotentStore implements IdempotentStore {
    private final DynamoDbEnhancedClient dynamoEnhancedClient;
    private final String dynamoTableName;
    private final JsonMapper jsonMapper = JsonMapper.shared();

    /**
     * Instantiates a new Dynamo idempotent store.
     *
     * @param dynamoEnhancedClient the dynamo v2 enhanced client
     * @param dynamoTableName      the dynamo table name
     */
    public DynamoIdempotentStore(DynamoDbEnhancedClient dynamoEnhancedClient, String dynamoTableName) {
        this.dynamoEnhancedClient = dynamoEnhancedClient;
        this.dynamoTableName = dynamoTableName;
    }

    private DynamoDbTable<IdempotentItem> getTable() {
        return dynamoEnhancedClient.table(dynamoTableName, TableSchema.fromBean(IdempotentItem.class));
    }

    @Override
    public Value getValue(IdempotentKey idempotentKey, Class<?> returnType) {
        try {
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
                    jsonMapper.readValue(idempotentItem.getResponse(), returnType));
        } catch (JacksonException e) {
            throw new IdempotentException(
                    String.format("Error getting response from dynamo for key: %s ", idempotentKey.key()), e);
        }
    }

    @Override
    public void store(IdempotentKey idempotentKey, Value value) {
        try {
            DynamoDbTable<IdempotentItem> idempotentTable = getTable();
            IdempotentItem idempotentItem = new IdempotentItem();
            idempotentItem.setKey(idempotentKey.key());
            idempotentItem.setProcessName(idempotentKey.processName());
            idempotentItem.setStatus(value.status());
            idempotentItem.setExpirationTimeInMilliSeconds(value.expirationTimeInMilliSeconds());
            idempotentItem.setResponse(jsonMapper.writeValueAsString(value.response()));

            PutItemEnhancedRequest<IdempotentItem> putRequest = PutItemEnhancedRequest
                    .builder(IdempotentItem.class)
                    .item(idempotentItem)
                    .conditionExpression(Expression.builder()
                            .expression("attribute_not_exists(#pk) AND attribute_not_exists(#sk)")
                            .putExpressionName("#pk", "key")
                            .putExpressionName("#sk", "processName")
                            .build())
                    .build();
            idempotentTable.putItem(putRequest);
        } catch (ConditionalCheckFailedException e) {
            throw new IdempotentKeyConflictException("Idempotent key already exists in DynamoDB", idempotentKey);
        } catch (JacksonException e) {
            throw new IdempotentException("error storing idempotent item", e);
        }
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
        try {
            DynamoDbTable<IdempotentItem> idempotentTable = getTable();
            IdempotentItem idempotentItem = new IdempotentItem();
            idempotentItem.setKey(idempotentKey.key());
            idempotentItem.setProcessName(idempotentKey.processName());
            idempotentItem.setStatus(value.status());
            idempotentItem.setExpirationTimeInMilliSeconds(value.expirationTimeInMilliSeconds());
            idempotentItem.setResponse(jsonMapper.writeValueAsString(value.response()));
            idempotentTable.putItem(idempotentItem);
        } catch (JacksonException e) {
            throw new IdempotentException("error updating idempotent item", e);
        }
    }
}
