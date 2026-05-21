package io.github.arun0009.idempotent.dynamo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "idempotent")
public record DynamoIdempotentProperties(
        @DefaultValue Aws aws, @DefaultValue DynamoDb dynamodb) {

    /** AWS client settings used when the application does not provide its own {@code DynamoDbClient}. */
    public record Aws(
            @DefaultValue("") String region,
            @DefaultValue("") String accessKey,
            @DefaultValue("") String accessSecret) {}

    /** DynamoDB table and endpoint settings for the idempotent store. */
    public record DynamoDb(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("") String endpoint,
            @DefaultValue("false") boolean tableCreate,
            @DefaultValue("true") boolean ttlEnabled,
            @DefaultValue("Idempotent") String tableName) {}
}
