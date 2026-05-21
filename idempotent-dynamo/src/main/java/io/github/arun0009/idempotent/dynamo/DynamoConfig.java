package io.github.arun0009.idempotent.dynamo;

import io.github.arun0009.idempotent.core.exception.IdempotentException;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.github.arun0009.idempotent.core.serialization.IdempotentPayloadCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.Assert;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.UpdateTimeToLiveRequest;

import java.net.URI;

/**
 * DynamoDB auto-configuration for the Idempotent store.
 *
 * <p>Creates default {@link DynamoDbClient} and {@link DynamoDbEnhancedClient} beans from
 * {@code idempotent.aws.*} and {@code idempotent.dynamodb.*} properties only when the application
 * does not provide them. If the application defines either client bean, the library backs off.
 */
@AutoConfiguration
@ConditionalOnClass(DynamoDbEnhancedClient.class)
@ConditionalOnProperty(prefix = "idempotent.dynamodb", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(DynamoIdempotentProperties.class)
public class DynamoConfig {
    private static final Logger log = LoggerFactory.getLogger(DynamoConfig.class);

    @Bean
    @ConditionalOnMissingBean({DynamoDbClient.class, DynamoDbEnhancedClient.class})
    public DynamoDbClient dynamoDbClient(DynamoIdempotentProperties properties) {
        var aws = properties.aws();
        var dynamodb = properties.dynamodb();
        var builder = DynamoDbClient.builder();

        if (!dynamodb.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(dynamodb.endpoint()));
            // Local/test endpoints still require credentials in the SDK client, any static values work.
            if (!aws.accessKey().isBlank() && !aws.accessSecret().isBlank()) {
                builder.credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(aws.accessKey(), aws.accessSecret())));
            } else {
                builder.credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "dummy")));
            }
            builder.region(aws.region().isBlank() ? Region.US_EAST_1 : Region.of(aws.region()));
            return builder.build();
        }

        Assert.hasText(aws.region(), "idempotent.aws.region must be provided when no DynamoDbClient bean is supplied");
        builder.region(Region.of(aws.region()));
        builder.credentialsProvider(DefaultCredentialsProvider.builder().build());
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean(DynamoDbEnhancedClient.class)
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDbClient).build();
    }

    @Bean
    @ConditionalOnMissingBean(IdempotentStore.class)
    public IdempotentStore dynamoIdempotentStore(
            DynamoDbEnhancedClient dynamoEnhancedClient,
            DynamoDbClient dynamoDbClient,
            DynamoIdempotentProperties properties,
            IdempotentPayloadCodec idempotentPayloadCodec) {
        initializeTableIfRequested(dynamoEnhancedClient, dynamoDbClient, properties);
        return new DynamoIdempotentStore(
                dynamoEnhancedClient, properties.dynamodb().tableName(), idempotentPayloadCodec);
    }

    private void initializeTableIfRequested(
            DynamoDbEnhancedClient dynamoEnhancedClient,
            DynamoDbClient dynamoDbClient,
            DynamoIdempotentProperties properties) {
        var dynamodb = properties.dynamodb();
        var tableName = dynamodb.tableName();
        if (dynamodb.tableCreate()) {
            dynamoEnhancedClient
                    .table(tableName, TableSchema.fromBean(IdempotentItem.class))
                    .createTable();
            log.info("Created DynamoDB table: {}", tableName);
        }

        if (dynamodb.ttlEnabled()) {
            try {
                var ttlRequest = UpdateTimeToLiveRequest.builder()
                        .tableName(tableName)
                        .timeToLiveSpecification(s -> s.enabled(true).attributeName("expiresAtEpochSeconds"))
                        .build();
                dynamoDbClient.updateTimeToLive(ttlRequest);
            } catch (AwsServiceException | SdkClientException e) {
                throw new IdempotentException("Failed to enable TTL on DynamoDB table: " + tableName, e);
            }
        }
    }
}
