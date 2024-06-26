package io.github.arun0009.idempotent.dynamo;

import io.github.arun0009.idempotent.core.aspect.IdempotentAspect;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.dynamodb.model.TimeToLiveSpecification;
import software.amazon.awssdk.services.dynamodb.model.UpdateTimeToLiveRequest;

import java.net.URI;

/**
 * The type Dynamo config.
 */
@Configuration
public class DynamoConfig {

    // dynamodb aws region
    @Value("${idempotent.aws.region:}")
    private String awsRegion;

    // dynamodb endpoint, set this if using localstack or testcontainers (local dynamodb)
    @Value("${idempotent.dynamodb.endpoint:}")
    private String dynamoDbEndpoint;

    // aws access key
    @Value("${idempotent.aws.accessKey:}")
    private String awsAccessKey;

    // aws access secret
    @Value("${idempotent.aws.accessSecret:}")
    private String awsAccessSecret;

    // set to true if using local dynamo e.g localstack or testcontainers
    @Value("${idempotent.dynamodb.use.local:false}")
    private boolean useLocalDynamoDb;

    // set to true if you want dynamo client to create table
    @Value("${idempotent.dynamodb.table.create:false}")
    private boolean createTable;

    // set idempotent table name, defaults to Idempotent
    @Value("${idempotent.dynamodb.table.name:Idempotent}")
    private String dynamoTableName;

    /**
     * Bean to create Enhanced Dynamo Client .
     *
     * @return the dynamodb v2 enhanced client
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient() {
        DynamoDbClientBuilder dynamoDbClientBuilder = DynamoDbClient.builder().region(Region.of(awsRegion));

        if (useLocalDynamoDb) {
            dynamoDbClientBuilder
                    .endpointOverride(URI.create(dynamoDbEndpoint))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(awsAccessKey, awsAccessSecret)));
        } else {
            dynamoDbClientBuilder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        DynamoDbEnhancedClient dynamoEnhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClientBuilder.build())
                .build();
        if (createTable) {
            dynamoEnhancedClient
                    .table(dynamoTableName, TableSchema.fromBean(IdempotentItem.class))
                    .createTable();
        }
        UpdateTimeToLiveRequest ttlRequest = UpdateTimeToLiveRequest.builder()
                .tableName(dynamoTableName)
                .timeToLiveSpecification(TimeToLiveSpecification.builder()
                        .enabled(true)
                        .attributeName("expirationTimeInMilliSeconds")
                        .build())
                .build();
        dynamoDbClientBuilder.build().updateTimeToLive(ttlRequest);
        return dynamoEnhancedClient;
    }

    /**
     * Create a Dynamo based Idempotent Store
     *
     * @param dynamoEnhancedClient dynamo v2 client
     * @return Dynamo IdempotentStore
     */
    @Bean
    public IdempotentStore dynamoIdempotentStore(DynamoDbEnhancedClient dynamoEnhancedClient) {
        return new DynamoIdempotentStore(dynamoEnhancedClient, dynamoTableName);
    }

    /**
     *
     * @param idempotentStore DynamoDB IdempotentStore
     * @return IdempotentAspect which uses dynamo for store
     */
    @Bean
    public IdempotentAspect idempotentAspect(IdempotentStore idempotentStore) {
        return new IdempotentAspect(idempotentStore);
    }
}
