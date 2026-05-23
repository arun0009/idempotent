# idempotent-dynamo

DynamoDB-backed `IdempotentStore` using the AWS SDK v2 enhanced client.

Upgrading from 2.x? See [docs/MIGRATION.md](../docs/MIGRATION.md#upgrading-to-30-from-2x).

## Dependency

```xml
<dependency>
  <groupId>io.github.arun0009</groupId>
  <artifactId>idempotent-dynamo</artifactId>
  <version>${idempotent.version}</version>
</dependency>
```

## DynamoDB client

If the application defines `DynamoDbClient` and/or `DynamoDbEnhancedClient` beans, those are used. Otherwise the library creates clients from `idempotent.aws.*` and `idempotent.dynamodb.*`.

```java
@Bean
DynamoDbClient dynamoDbClient() {
  return DynamoDbClient.builder().region(Region.US_EAST_1).build();
}

@Bean
DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient client) {
  return DynamoDbEnhancedClient.builder().dynamoDbClient(client).build();
}
```

## Properties

Core settings: [idempotent-core – Configuration](../idempotent-core/README.md#configuration).

### `idempotent.aws.*` (default client only)

| Property | Description |
|----------|-------------|
| `idempotent.aws.region` | AWS region when no `DynamoDbClient` bean is provided |
| `idempotent.aws.access-key` | Optional static credentials (e.g. local DynamoDB) |
| `idempotent.aws.access-secret` | Optional static secret |

### `idempotent.dynamodb.*`

| Property | Default | Description |
|----------|---------|-------------|
| `idempotent.dynamodb.enabled` | `true` | Disable DynamoDB auto-configuration |
| `idempotent.dynamodb.endpoint` | — | Endpoint override (local/test) |
| `idempotent.dynamodb.table-name` | `Idempotent` | Table name |
| `idempotent.dynamodb.table-create` | `false` | Create table on startup |
| `idempotent.dynamodb.ttl-enabled` | `true` | Enable TTL on `expiresAtEpochSeconds` at startup; set `false` if TTL is already configured |

Serialization: `idempotent.serialization.strategy` (`json` | `java`). See [payload serialization](../idempotent-core/README.md#payload-serialization).

## Example

```properties
idempotent.aws.region=us-east-1
idempotent.dynamodb.table-name=Idempotent
idempotent.dynamodb.table-create=true
```

Partition key: `key`. Sort key: `processName`. TTL attribute: `expiresAtEpochSeconds` (epoch seconds).
