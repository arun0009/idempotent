package io.github.arun0009.idempotent.dynamo;

import io.github.arun0009.idempotent.core.exception.IdempotentException;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * Dynamo idempotent store with conditional expressions for race condition handling
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
            Value value = new Value(
                    idempotentItem.getStatus(),
                    idempotentItem.getExpirationTimeInMilliSeconds(),
                    jsonMapper.readValue(idempotentItem.getResponse(), returnType));
            if (value.isExpired()) {
                return null;
            }
            return value;
        } catch (JacksonException e) {
            throw new IdempotentException(
                    String.format("Error getting response from dynamo for key: %s ", idempotentKey.key()), e);
        }
    }

    @Override
    public void store(IdempotentKey idempotentKey, Value value) {
        try {
            IdempotentItem item = createItem(idempotentKey, value);
            // Atomic condition: only insert if key doesn't exist or expired
            Expression condition = Expression.builder()
                    .expression("attribute_not_exists(#key) OR expirationTimeInMilliSeconds < :now")
                    .expressionNames(Map.of("#key", "key"))
                    .expressionValues(Map.of(
                            ":now",
                            AttributeValue.builder()
                                    .n(String.valueOf(System.currentTimeMillis()))
                                    .build()))
                    .build();
            getTable()
                    .putItem(PutItemEnhancedRequest.builder(IdempotentItem.class)
                            .item(item)
                            .conditionExpression(condition)
                            .build());
        } catch (ConditionalCheckFailedException e) {
            // Race condition - key exists and not expired, silent fail
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
            IdempotentItem item = createItem(idempotentKey, value);
            Expression condition = Expression.builder()
                    .expression(
                            "attribute_exists(#key) AND (#status = :inprogress OR expirationTimeInMilliSeconds < :now)")
                    .expressionNames(Map.of("#key", "key", "#status", "status"))
                    .expressionValues(Map.of(
                            ":now",
                            AttributeValue.builder()
                                    .n(String.valueOf(System.currentTimeMillis()))
                                    .build(),
                            ":inprogress",
                            AttributeValue.builder()
                                    .s(IdempotentStore.Status.INPROGRESS.name())
                                    .build()))
                    .build();

            getTable()
                    .putItem(PutItemEnhancedRequest.builder(IdempotentItem.class)
                            .item(item)
                            .conditionExpression(condition)
                            .build());
        } catch (ConditionalCheckFailedException e) {
            // Race condition - key doesn't exist or not in correct state, silent fail
        } catch (JacksonException e) {
            throw new IdempotentException("error updating idempotent item", e);
        }
    }

    private IdempotentItem createItem(IdempotentKey idempotentKey, Value value) throws JacksonException {
        IdempotentItem idempotentItem = new IdempotentItem();
        idempotentItem.setKey(idempotentKey.key());
        idempotentItem.setProcessName(idempotentKey.processName());
        idempotentItem.setStatus(value.status());
        idempotentItem.setExpirationTimeInMilliSeconds(value.expirationTimeInMilliSeconds());
        idempotentItem.setResponse(jsonMapper.writeValueAsString(value.response()));
        return idempotentItem;
    }
}
