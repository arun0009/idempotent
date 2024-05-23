package io.github.arun0009.idempotent.dynamo;

import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

/**
 * The type Dynamo config.
 */
@Configuration
public class DynamoConfig {

    @Value("${aws.region}")
    private String awsRegion;

    @Value("${aws.dynamodb.endpoint:}")
    private String dynamoDbEndpoint;

    @Value("${use.local.dynamodb:false}")
    private boolean useLocalDynamoDb;

    /**
     * Bean to create Enhanced Dynamo Client .
     *
     * @return the dynamodb v2 enhanced client
     */
    @Bean
    @Primary
    public DynamoDbEnhancedClient dynamoDbEnhancedClient() {
        DynamoDbClient dynamoDbClient;

        if (useLocalDynamoDb) {
            dynamoDbClient = DynamoDbClient.builder()
                    .endpointOverride(URI.create(dynamoDbEndpoint))
                    .credentialsProvider(
                            StaticCredentialsProvider.create(AwsBasicCredentials.create("accesskey", "secretkey")))
                    .region(Region.of(awsRegion))
                    .build();
        } else {
            dynamoDbClient = DynamoDbClient.builder()
                    .region(Region.of(awsRegion))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
        }

        return DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDbClient).build();
    }

    /**
     * Create a Dynamo based Idempotent Store
     * @param dynamoEnhancedClient dynamo v2 client
     * @return Dynamo IdempotentStore
     */
    @Bean
    public IdempotentStore dynamoIdempotentStore(DynamoDbEnhancedClient dynamoEnhancedClient) {
        return new DynamoIdempotentStore(dynamoEnhancedClient);
    }
}
