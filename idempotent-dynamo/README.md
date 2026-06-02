<div align="center">

# idempotent-dynamo

[![Maven Central](https://img.shields.io/maven-central/v/io.github.arun0009/idempotent-dynamo?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.arun0009/idempotent-dynamo)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://adoptium.net)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-brightgreen.svg)](https://spring.io/projects/spring-boot)

</div>

**Serverless-grade idempotency on AWS.** Conditional `PutItem` for atomic key claims, native TTL on `expiresAtEpochSeconds`, and the AWS SDK v2 enhanced client — zero servers to operate.

## When DynamoDB is the right choice

- You run on **AWS** (Lambda, ECS, EKS, Fargate) and want a managed store.
- You need **horizontal scale** without provisioning clusters.
- You want **TTL-driven expiry** so no cleanup job is required.
- You already use a DynamoDB **single-table** pattern and want to colocate idempotency keys.

## Install

```xml
<dependency>
	<groupId>io.github.arun0009</groupId>
	<artifactId>idempotent-dynamo</artifactId>
	<version>${idempotent.version}</version>
</dependency>
```

### Use your own DynamoDB clients (recommended in production)

If your app exposes `DynamoDbClient` and/or `DynamoDbEnhancedClient` beans, the library uses them — credentials, region, retry policy, and HTTP client are entirely yours to control.

```java
@Bean
DynamoDbClient dynamoDbClient() {
	return DynamoDbClient.builder().region(Region.US_EAST_1).build();
}

@Bean
DynamoDbEnhancedClient enhanced(DynamoDbClient client) {
	return DynamoDbEnhancedClient.builder().dynamoDbClient(client).build();
}
```

### Or let the library configure one

Useful for local development and tests:

```properties
idempotent.aws.region=us-east-1
idempotent.dynamodb.endpoint=http://localhost:8000     # DynamoDB Local
idempotent.aws.access-key=local
idempotent.aws.access-secret=local
```

## Under the hood

| Operation | DynamoDB mechanism |
|-----------|--------------------|
| First claim | `PutItem` with `attribute_not_exists(key) AND attribute_not_exists(processName)` |
| Complete | `PutItem` with `attribute_exists(...)` — no-op if the row is already gone |
| Read | `GetItem` + shared lazy delete on expiry |
| Expiry | `expiresAtEpochSeconds` attribute; **table TTL** auto-enabled at startup |

Partition key `key`, sort key `processName`. One row per `(idempotency key, process scope)`.

## Configuration

Shared retry / header / serialization properties: [idempotent-core – Configuration](../idempotent-core/README.md#configuration).

### `idempotent.aws.*` (library-created client only)

| Property | Description |
|----------|-------------|
| `idempotent.aws.region` | AWS region |
| `idempotent.aws.access-key` / `access-secret` | Static credentials (e.g. DynamoDB Local). Skip in real AWS — let the SDK resolve creds. |

### `idempotent.dynamodb.*`

| Property | Default | Description |
|----------|---------|-------------|
| `idempotent.dynamodb.enabled` | `true` | Disable auto-configuration |
| `idempotent.dynamodb.endpoint` | — | Endpoint override (DynamoDB Local, LocalStack, …) |
| `idempotent.dynamodb.table-name` | `Idempotent` | Table name |
| `idempotent.dynamodb.table-create` | `false` | Create the table on startup (use only in dev) |
| `idempotent.dynamodb.ttl-enabled` | `true` | Enable TTL on `expiresAtEpochSeconds` at startup; set `false` if TTL is already configured |
| `idempotent.serialization.strategy` | `json` | Shared codec strategy |

## Pre-existing tables

If you manage the table yourself, use partition key `key` (String), sort key `processName` (String), and enable TTL on `expiresAtEpochSeconds`.

Back to the [project overview](../README.md).
