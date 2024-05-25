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

import java.net.URI;

/**
 * The type Dynamo config.
 */
@Configuration
public class DynamoConfig {

    @Value("${idempotent.aws.region}")
    private String awsRegion;

    @Value("${idempotent.dynamodb.endpoint:}")
    private String dynamoDbEndpoint;

    @Value("${idempotent.aws.accessKey:}")
    private String awsAccessKey;

    @Value("${idempotent.aws.accessSecret:}")
    private String awsAccessSecret;

    @Value("${idempotent.dynamodb.use.local:false}")
    private boolean useLocalDynamoDb;

    @Value("${idempotent.dynamodb.table.create:false}")
    private boolean createTable;

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

    @Bean
    public IdempotentAspect idempotentAspect(IdempotentStore idempotentStore) {
        return new IdempotentAspect(idempotentStore);
    }
}
