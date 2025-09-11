package io.github.arun0009.idempotent.dynamo;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

@Configuration
@SpringBootApplication(scanBasePackages = "io.github.arun0009.idempotent.dynamo")
public class DynamoTestConfig {
    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            TestPropertyValues.of(
                            "idempotent.dynamodb.endpoint=" + "http://"
                                    + SharedDynamoContainer.DYNAMO_CONTAINER.getHost() + ":"
                                    + SharedDynamoContainer.DYNAMO_CONTAINER.getFirstMappedPort(),
                            "idempotent.dynamodb.use.local=true",
                            "idempotent.dynamodb.table.name=Idempotent",
                            "idempotent.dynamodb.table.create=true",
                            "idempotent.aws.accessKey=accessKey",
                            "idempotent.aws.accessSecret=secretKey",
                            "idempotent.aws.region=us-east-1")
                    .applyTo(context.getEnvironment());
        }
    }

    @Bean
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .endpointOverride(URI.create("http://"
                        + SharedDynamoContainer.DYNAMO_CONTAINER.getHost() + ":"
                        + SharedDynamoContainer.DYNAMO_CONTAINER.getFirstMappedPort()))
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create("accessKey", "secretKey")))
                .region(Region.US_EAST_1)
                .build();
    }
}
