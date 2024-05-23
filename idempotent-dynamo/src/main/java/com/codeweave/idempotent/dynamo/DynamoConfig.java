package com.codeweave.idempotent.dynamo;

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

@Configuration
public class DynamoConfig {

    @Value("${aws.region}")
    private String awsRegion;

    @Value("${aws.dynamodb.endpoint:}")
    private String dynamoDbEndpoint;

    @Value("${use.local.dynamodb:false}")
    private boolean useLocalDynamoDb;

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
}
