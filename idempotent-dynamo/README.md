# Idempotent Cache with DynamoDB Storage/Cache

To integrate the idempotent cache with DynamoDB storage/cache into your project, add the following dependency to your
pom.xml file:

```xml
<dependency>
	<groupId>io.github.arun0009</groupId>
	<artifactId>idempotent-dynamo</artifactId>
	<!-- get latest idempotent version from maven central -->
	<version>${idempotent.version}</version>
</dependency>
```

## Overview

This project provides an idempotent request handling mechanism using DynamoDB for storage/cache. The idempotent cache
ensures that duplicate requests are handled safely and effectively, avoiding unintended side effects.
This is particularly useful in scenarios where the same request might be sent multiple times due to retries or client errors.

## Configuration Properties

Below are the properties that can be configured for the idempotent cache. These properties can be set in your
application's configuration file (e.g., application.properties or application.yml).

### General Properties

* Idempotent Key Header

		Property: idempotent.key.header
		Default Value: X-Idempotency-Key
		Description: The header name used to pass the idempotency key in HTTP requests.

* In-Progress Request Max Retries

		Property: idempotent.inprogress.max.retries
		Default Value: 5
		Description: The maximum number of retries allowed for in-progress requests to ensure only one request wins.

* In-Progress Status Check Retry Initial Interval

		Property: idempotent.inprogress.retry.initial.intervalMillis
		Default Value: 100
		Description: The initial interval (in milliseconds) between retries for checking the status of in-progress requests.

* In-Progress Retry Multiplier

		Property: idempotent.inprogress.retry.multiplier
		Default Value: 2
		Description: The multiplier used for exponential backoff during retries.

### DynamoDB Configuration

* AWS Region

		Property: idempotent.aws.region
		Default Value: (empty)
		Description: The AWS region where DynamoDB is hosted.

* DynamoDB Endpoint

		Property: idempotent.dynamodb.endpoint
		Default Value: (empty)
		Description: The DynamoDB endpoint URL. Useful for local testing with LocalStack or TestContainers.

* AWS Access Key

		Property: idempotent.aws.accessKey
		Default Value: (empty)
		Description: The AWS access key for authentication.

* AWS Access Secret

		Property: idempotent.aws.accessSecret
		Default Value: (empty)
		Description: The AWS access secret for authentication.

* Use Local DynamoDB

		Property: idempotent.dynamodb.use.local
		Default Value: false
		Description: Flag to indicate whether to use a local DynamoDB instance (e.g., LocalStack or TestContainers).

* Create DynamoDB Table

		Property: idempotent.dynamodb.table.create
		Default Value: false
		Description: Flag to indicate whether the DynamoDB client should create the table.

* DynamoDB Table Name

		Property: idempotent.dynamodb.table.name
		Default Value: Idempotent
		Description: The name of the DynamoDB table used for storing idempotent requests.

## Custom DynamoDbEnhancedClient Bean

By default, the library will create and configure the DynamoDbEnhancedClient using the provided properties.
However, if you want to pass your own DynamoDbEnhancedClient bean, you can do so by defining it as follows:

### Custom DynamoDbEnhancedClient Bean Configuration

If you prefer to configure the DynamoDbEnhancedClient yourself, you can define it as follows:

```java
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class DynamoDBConfig {

		@Bean
		@ConditionalOnMissingBean(DynamoDbEnhancedClient.class)
		public DynamoDbEnhancedClient dynamoDbEnhancedClient() {
				DynamoDbClient dynamoDbClient = DynamoDbClient.builder()
						.region(Region.of("your-aws-region"))
						.endpointOverride(URI.create("your-dynamodb-endpoint"))  // Optional, for local testing
						.credentialsProvider(StaticCredentialsProvider.create(
								AwsBasicCredentials.create("your-access-key", "your-secret-key")
						))
						.build();

				return DynamoDbEnhancedClient.builder()
						.dynamoDbClient(dynamoDbClient)
						.build();
		}
}
```

## Example Application Configuration

Here is an example of how you might configure your application using application.properties:

```properties
# Idempotent Cache General Properties
idempotent.key.header=X-Idempotency-Key
idempotent.inprogress.max.retries=5
idempotent.inprogress.retry.initial.intervalMillis=100
idempotent.inprogress.retry.multiplier=2

# DynamoDB Configuration
idempotent.aws.region=us-west-2
idempotent.dynamodb.endpoint=http://localhost:8000
idempotent.aws.accessKey=your-access-key
idempotent.aws.accessSecret=your-secret-key
idempotent.dynamodb.use.local=true
idempotent.dynamodb.table.create=true
idempotent.dynamodb.table.name=Idempotent
```

By following these steps and configurations, you can effectively manage idempotent requests using DynamoDB, ensuring
robust and reliable handling of duplicate requests in your application. If you need to customize the DynamoDB client,
you can provide your own DynamoDbEnhancedClient bean.
