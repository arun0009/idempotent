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
@Idempotent(key = "#paymentDetails", ttlInSeconds = 60, hashKey=true)
@PostMapping("/payments")
public PaymentResponse postPayment(@RequestBody PaymentDetails paymentDetails) {
    // Method implementation - only executes once per unique paymentDetails
}
```

### Programmatic API

```java
// Basic usage
String result = idempotentService.execute("payment-123", () -> {
    return processPayment("123");
}, 300);

// Different operations with same key
String email = idempotentService.execute("user-456", "send-email", 
    () -> sendWelcomeEmail("user-456"), 600);

// Advanced usage with IdempotentKey
IdempotentKey key = new IdempotentKey("order-789", "process-payment");
PaymentResult result = idempotentService.execute(key, 
    () -> paymentGateway.processPayment(order), 1800);
```

### Supported Return Types

```java
// Primitives
int count = idempotentService.execute("count", 
    () -> userRepo.count(), 60);

// Complex objects
Order order = idempotentService.execute("order-123", 
    () -> orderService.getOrder("123"), 300);

// Null values
String data = idempotentService.execute("optional", 
    () -> maybeGetData(), 120);
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
    public IdempotentAspect idempotentAspect(IdempotentStore idempotentStore) {
        return new IdempotentAspect(idempotentStore);
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
