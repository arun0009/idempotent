# Idempotent Core

A Java library providing idempotency utilities for Java applications. Supports both annotation-based (AOP) and programmatic service-based approaches.

## Features
- **Annotation-based** idempotency with Spring AOP
- **Programmatic API** for fine-grained control
- **Extensible storage** with multiple implementations
- **Configurable** behavior through properties

## Quick Start

### Maven Dependency

```xml
<dependency>
		<groupId>io.github.arun0009</groupId>
		<artifactId>idempotent-core</artifactId>
		<version>${idempotent.version}</version>
</dependency>
```

## Configuration

### Core Properties

| Property | Default | Description |
|----------|---------|-------------|
| `idempotent.key.header` | `X-Idempotency-Key` | HTTP header for idempotency key |
| `idempotent.inprogress.max.retries` | `5` | Max retries for in-progress requests |
| `idempotent.inprogress.retry.initial.intervalMillis` | `100` | Initial retry interval in ms |
| `idempotent.inprogress.retry.multiplier` | `2` | Exponential backoff multiplier |

### Payload serialization (persistent stores)

Redis, RDS, DynamoDB, and NATS share one payload codec (`IdempotentPayloadCodec`) for serializing stored responses.

| Property | Default | Description |
|----------|---------|-------------|
| `idempotent.serialization.strategy` | `json` | `json` (Jackson) or `java` (JDK native serialization) |

- `json` strategy uses Jackson with permissive polymorphic typing (logs a warning at startup).
- `java` strategy uses JDK serialization and requires payloads to implement `Serializable`.

#### Customizing Jackson (JSON strategy)

Define an `IdempotentJsonMapperCustomizer` bean to restrict polymorphic typing or add Jackson modules.
If you use `Object.class` deserialization with final payload types (Kotlin data classes, Java records), use
an all-types resolver:

```java
@Bean
IdempotentJsonMapperCustomizer idempotentJsonMapperCustomizer() {
		return builder -> {
				var ptv = BasicPolymorphicTypeValidator.builder()
								.allowIfBaseType("com.myapp.")
								.build();
				builder.polymorphicTypeValidator(ptv)
								.setDefaultTyping(new DefaultTypeResolverBuilder(
												ptv,
												DefaultTyping.NON_FINAL,
												JsonTypeInfo.As.PROPERTY,
												JsonTypeInfo.Id.CLASS,
												"@class") {
										@Override
										public boolean useForType(JavaType t) {
												return true;
										}
								});
		};
}
```

> **Important:** when you define your own `IdempotentJsonMapperCustomizer`, you take full ownership of the mapper
> configuration. The library's default permissive typing is no longer applied. If your payloads require polymorphic
> typing (e.g., `Object.class` retrieval, interfaces, records, Kotlin data classes), configure it explicitly.

#### Fully replacing the codec

Define an `IdempotentPayloadCodec` bean to replace the default serialization entirely:

```java
@Bean
IdempotentPayloadCodec idempotentPayloadCodec() {
		return new MyCustomPayloadCodec();
}
```

The `IdempotentPayloadCodec` interface has four methods: `serializeToBytes`, `deserializeFromBytes`,
`serializeToString`, and `deserializeFromString`. The byte-oriented methods are used by NATS; the
string-oriented methods are used by RDS and DynamoDB. Redis has its own serializer path via
`GenericJacksonJsonRedisSerializer` and honors the `idempotent.serialization.strategy` setting independently.

### Storage Backends

#### In-Memory (Default)
No additional configuration needed. The in-memory store is used by default if no other store is configured.

#### Redis
For Redis configuration and customizations, please refer to the [idempotent-redis README](../idempotent-redis/README.md).

#### DynamoDB
For DynamoDB configuration and customizations, please refer to the [idempotent-dynamo README](../idempotent-dynamo/README.md).

#### Custom Store
To implement a custom store, create a class that implements `IdempotentStore` and define it as a `@Bean`:

```java
@Configuration
public class CustomIdempotentConfig {
		@Bean
		@Primary
		public IdempotentStore customIdempotentStore() {
				return new CustomIdempotentStore();
		}
}
```

## Usage

### Annotation-based (AOP)

```java
@Idempotent(key = "#paymentDetails", duration = "PT1M", hashKey=true)
@PostMapping("/payments")
public PaymentResponse postPayment(@RequestBody PaymentDetails paymentDetails) {
		// Method implementation - only executes once per unique paymentDetails
}
```

### Programmatic API

```java
// Basic usage with Duration
String result = idempotentService.execute("payment-123", () -> {
		return processPayment("123");
}, Duration.ofMinutes(1));

// Or using ISO-8601 duration string
String result2 = idempotentService.execute("payment-123", "payment-process", () -> {
		return processPayment("123");
}, Duration.parse("PT1M"));

// Different operations with same key
String email = idempotentService.execute("user-456", "send-email",
		() -> sendWelcomeEmail("user-456"), Duration.ofMinutes(10));

// Advanced usage with IdempotentKey
IdempotentKey key = new IdempotentKey("order-789", "process-payment");
PaymentResult result = idempotentService.execute(key,
		() -> paymentGateway.processPayment(order), Duration.ofMinutes(30));
```

### Supported Return Types

```java
// Primitives
int count = idempotentService.execute("count",
		() -> userRepo.count(), Duration.ofMinutes(1));

// Complex objects
Order order = idempotentService.execute("order-123",
		() -> orderService.getOrder("123"), Duration.ofMinutes(5));

// Null values
String data = idempotentService.execute("optional",
		() -> maybeGetData(), Duration.ofMinutes(2));
```

## Custom Storage

Implement the `IdempotentStore` interface to use your preferred storage:

```java
public class CustomIdempotentStore implements IdempotentStore {
		// Implement required methods
}
```

Then configure it in your Spring configuration:

```java
@Configuration
public class IdempotentConfig {
		@Bean
		public IdempotentStore idempotentStore() {
				return new CustomIdempotentStore();
		}

		@Bean
		public IdempotentAspect idempotentAspect(IdempotentStore idempotentStore, IdempotentProperties properties) {
				return new IdempotentAspect(idempotentStore, properties);
		}
}
```

## Available Implementations

- **In-Memory**: `InMemoryIdempotentStore` (default)
- **Redis**: [idempotent-redis](../idempotent-redis)
- **DynamoDB**: [idempotent-dynamo](../idempotent-dynamo)

## Advanced Topics

### Error Handling

- Failed operations are automatically cleaned up
- In-progress state is maintained during retries
- Configurable retry policies for concurrent requests

### Performance Considerations

- Use `hashKey=true` for large objects to store hashes instead of serialized objects
- Choose appropriate TTL values based on your use case
- Monitor storage usage for high-throughput applications

### Redis and DynamoDB Implementations
While idempotent-core provides the core functionality, you can use specific implementations like Redis or DynamoDB
by including the respective modules ([idempotent-redis](../idempotent-redis), [idempotent-dynamo](../idempotent-dynamo)).
However, if you prefer to use a different storage solution, you can implement your own store as described above.
