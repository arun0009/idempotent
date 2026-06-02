package io.github.arun0009.idempotent.dynamo;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "idempotent")
public record DynamoIdempotentProperties(
        @DefaultValue Aws aws, @DefaultValue DynamoDb dynamodb) {

    /** AWS client settings used when the application does not provide its own {@code DynamoDbClient}. */
    public record Aws(
            @Nullable String region,
            @Nullable String accessKey,
            @Nullable String accessSecret) {}

    /** DynamoDB table and endpoint settings for the idempotent store. */
    public record DynamoDb(
            @DefaultValue("true") boolean enabled,
            @Nullable String endpoint,
            @DefaultValue("false") boolean tableCreate,
            @DefaultValue("true") boolean ttlEnabled,
            @DefaultValue("Idempotent") String tableName) {}
}
