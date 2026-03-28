package io.github.arun0009.idempotent.dynamo;

import io.github.arun0009.idempotent.core.exception.IdempotentException;
import io.github.arun0009.idempotent.core.exception.IdempotentKeyConflictException;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.github.arun0009.idempotent.core.serialization.IdempotentPayloadCodec;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.Objects;

/**
 * Dynamo idempotent store.
 */
public class DynamoIdempotentStore implements IdempotentStore {
    private final DynamoDbEnhancedClient dynamoEnhancedClient;
    private final String dynamoTableName;
    private final IdempotentPayloadCodec payloadCodec;

    /**
     * Instantiates a new Dynamo idempotent store.
     *
     * @param dynamoEnhancedClient the dynamo v2 enhanced client
     * @param dynamoTableName      the dynamo table name
     * @param payloadCodec         shared idempotent payload codec
     */
    public DynamoIdempotentStore(
            DynamoDbEnhancedClient dynamoEnhancedClient, String dynamoTableName, IdempotentPayloadCodec payloadCodec) {
        this.dynamoEnhancedClient = dynamoEnhancedClient;
        this.dynamoTableName = dynamoTableName;
        this.payloadCodec = payloadCodec;
    }

    private DynamoDbTable<IdempotentItem> getTable() {
        return dynamoEnhancedClient.table(dynamoTableName, TableSchema.fromBean(IdempotentItem.class));
    }

    private IdempotentItem toItem(IdempotentKey idempotentKey, Value value) {
        var item = new IdempotentItem();
        item.setKey(idempotentKey.key());
        item.setProcessName(idempotentKey.processName());
        item.setStatus(value.status());
        item.setExpirationTimeInMilliSeconds(value.expirationTimeInMilliSeconds());
        item.setResponse(
                value.response() == null
                        ? ""
                        : Objects.requireNonNull(
                                payloadCodec.serializeToString(value.response()),
                                "Serialized response must not be null"));
        return item;
    }

    @Override
    public @Nullable Value getValue(IdempotentKey idempotentKey, Class<?> returnType) {
        var dynamoKey = Key.builder()
                .partitionValue(idempotentKey.key())
                .sortValue(idempotentKey.processName())
                .build();
        var idempotentItem = getTable().getItem(dynamoKey);
        if (idempotentItem == null) {
            return null;
        }
        String serializedResponse = idempotentItem.getResponse();
        Object response = serializedResponse == null || serializedResponse.isEmpty()
                ? null
                : payloadCodec.deserializeFromString(serializedResponse, returnType);
        return new Value(idempotentItem.getStatus(), idempotentItem.getExpirationTimeInMilliSeconds(), response);
    }

    @Override
    public void store(IdempotentKey idempotentKey, Value value) {
        try {
            var putRequest = PutItemEnhancedRequest.builder(IdempotentItem.class)
                    .item(toItem(idempotentKey, value))
                    .conditionExpression(Expression.builder()
                            .expression("attribute_not_exists(#pk) AND attribute_not_exists(#sk)")
                            .putExpressionName("#pk", "key")
                            .putExpressionName("#sk", "processName")
                            .build())
                    .build();
            getTable().putItem(putRequest);
        } catch (ConditionalCheckFailedException e) {
            throw new IdempotentKeyConflictException("Idempotent key already exists in DynamoDB", idempotentKey);
        } catch (IllegalArgumentException e) {
            throw new IdempotentException("error storing idempotent item", e);
        }
    }

    @Override
    public void remove(IdempotentKey idempotentKey) {
        getTable()
                .deleteItem(
                        b -> b.key(k -> k.partitionValue(idempotentKey.key()).sortValue(idempotentKey.processName())));
    }

    @Override
    public void update(IdempotentKey idempotentKey, Value value) {
        try {
            getTable().putItem(toItem(idempotentKey, value));
        } catch (IllegalArgumentException e) {
            throw new IdempotentException("error updating idempotent item", e);
        }
    }
}
