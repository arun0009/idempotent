# Idempotent Cache with DynamoDB Storage

To integrate the idempotent cache with DynamoDB into your project, add the following dependency to your `pom.xml` file:

```xml
<dependency>
	<groupId>io.github.arun0009</groupId>
	<artifactId>idempotent-dynamo</artifactId>
	<!-- get latest idempotent version from Maven Central -->
	<version>${idempotent.version}</version>
</dependency>
```

## Overview

This project provides an idempotent request handling mechanism using DynamoDB for storage/cache. The idempotent cache
ensures that duplicate requests are handled safely and effectively, avoiding unintended side effects.

## DynamoDB Client

If your app provides `DynamoDbClient` and/or `DynamoDbEnhancedClient` beans, the library uses them. Otherwise it
creates defaults from `idempotent.aws.*` (client/region/credentials) and `idempotent.dynamodb.*` (endpoint/table).

```java
@Configuration
public class DynamoDBConfig {

		@Bean
		public DynamoDbClient dynamoDbClient() {
				return DynamoDbClient.builder()
						.region(Region.of("us-east-1"))
						.build();
		}

		@Bean
		public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
				return DynamoDbEnhancedClient.builder()
						.dynamoDbClient(dynamoDbClient)
						.build();
		}
}
```

## Configuration Properties

### Core configuration

See [idempotent-core – Configuration](../idempotent-core/README.md#configuration) for `idempotent.key.header`, in-progress retry settings, and serialization.

### AWS client properties (`idempotent.aws.*`)

Used when the library creates the default `DynamoDbClient`.

* Region

		Property: idempotent.aws.region
		Default Value: (empty)
		Description: AWS region for default client creation when no `DynamoDbClient` bean is provided.

* Access Key

		Property: idempotent.aws.access-key
		Default Value: (empty)
		Description: Optional static access key used with a custom DynamoDB endpoint.

* Access Secret

		Property: idempotent.aws.access-secret
		Default Value: (empty)
		Description: Optional static access secret used with a custom DynamoDB endpoint.

### DynamoDB properties (`idempotent.dynamodb.*`)

* Enabled

		Property: idempotent.dynamodb.enabled
		Default Value: true
		Description: Set to false to disable DynamoDB auto-configuration.

* Endpoint

		Property: idempotent.dynamodb.endpoint
		Default Value: (empty)
		Description: Optional endpoint override (useful for local/test DynamoDB).

* Table Name

		Property: idempotent.dynamodb.table-name
		Default Value: Idempotent
		Description: The name of the DynamoDB table used for storing idempotent requests.

* Create Table

		Property: idempotent.dynamodb.table-create
		Default Value: false
		Description: Whether to create the DynamoDB table on startup.

* TTL setup

		Property: idempotent.dynamodb.ttl-enabled
		Default Value: true
		Description: Whether to enable TTL on `expiresAtEpochSeconds` at startup. Set to `false` when you manage TTL on an existing table.

## Serialization

Stored responses use the shared `idempotent.serialization.strategy` setting (`json` or `java`).
See [idempotent-core – Payload serialization](../idempotent-core/README.md#payload-serialization-persistent-stores).

## Example Application Configuration

```properties
# AWS client (when library creates DynamoDbClient)
idempotent.aws.region=us-east-1

# DynamoDB table
idempotent.dynamodb.table-name=Idempotent
idempotent.dynamodb.table-create=true
```
