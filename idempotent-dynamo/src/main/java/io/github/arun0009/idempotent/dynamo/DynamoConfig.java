package io.github.arun0009.idempotent.dynamo;

import io.github.arun0009.idempotent.core.exception.IdempotentException;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.github.arun0009.idempotent.core.serialization.IdempotentPayloadCodec;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
 * The type Dynamo config.
 */
@AutoConfiguration
@ConditionalOnClass(DynamoDbEnhancedClient.class)
@EnableConfigurationProperties(DynamoIdempotentProperties.class)
public class DynamoConfig {

    /**
     * Bean to create Enhanced Dynamo Client .
     *
     * @return the dynamodb v2 enhanced client
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoIdempotentProperties properties) {
        var dynamoDbClientBuilder =
                DynamoDbClient.builder().region(Region.of(properties.aws().region()));

        if (properties.dynamodb().useLocal()) {
            dynamoDbClientBuilder
                    .endpointOverride(URI.create(properties.dynamodb().endpoint()))
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                            properties.aws().accessKey(), properties.aws().accessSecret())));
        } else {
            dynamoDbClientBuilder.credentialsProvider(
                    DefaultCredentialsProvider.builder().build());
        }

        var dynamoEnhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClientBuilder.build())
                .build();
        if (properties.dynamodb().tableCreate()) {
            dynamoEnhancedClient
                    .table(properties.dynamodb().tableName(), TableSchema.fromBean(IdempotentItem.class))
                    .createTable();
        }
        var ttlRequest = UpdateTimeToLiveRequest.builder()
                .tableName(properties.dynamodb().tableName())
                .timeToLiveSpecification(s -> s.enabled(true).attributeName("expirationTimeInMilliSeconds"))
                .build();
        try (var client = dynamoDbClientBuilder.build()) {
            client.updateTimeToLive(ttlRequest);
        } catch (AwsServiceException | SdkClientException e) {
            throw new IdempotentException("Failed to enable TTL on Dynamo table: "
                    + properties.dynamodb().tableName());
        }
        return dynamoEnhancedClient;
    }

    /**
     * Create a Dynamo based Idempotent Store
     *
     * @param dynamoEnhancedClient dynamo v2 client
     * @return Dynamo IdempotentStore
     */
    @Bean
    public IdempotentStore dynamoIdempotentStore(
            DynamoDbEnhancedClient dynamoEnhancedClient,
            DynamoIdempotentProperties properties,
            IdempotentPayloadCodec idempotentPayloadCodec) {
        return new DynamoIdempotentStore(
                dynamoEnhancedClient, properties.dynamodb().tableName(), idempotentPayloadCodec);
    }
}
