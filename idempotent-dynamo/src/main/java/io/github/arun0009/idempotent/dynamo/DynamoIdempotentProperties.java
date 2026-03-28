package io.github.arun0009.idempotent.dynamo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "idempotent")
public record DynamoIdempotentProperties(
        @DefaultValue Aws aws, @DefaultValue DynamoDb dynamodb) {

    public record Aws(
            @DefaultValue("") String region,
            @DefaultValue("") String accessKey,
            @DefaultValue("") String accessSecret) {}

    public record DynamoDb(
            @DefaultValue("") String endpoint,
            @DefaultValue("false") boolean useLocal,
            @DefaultValue("false") boolean tableCreate,
            @DefaultValue("Idempotent") String tableName) {}
}
