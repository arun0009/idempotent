# idempotent-core

Core idempotency for Spring applications: `@Idempotent` on methods, programmatic `IdempotentService`, in-memory store, and shared payload serialization.

Upgrading from 2.x? See [docs/MIGRATION.md](../docs/MIGRATION.md#upgrading-to-30-from-2x).

## Dependency

```xml
<dependency>
  <groupId>io.github.arun0009</groupId>
  <artifactId>idempotent-core</artifactId>
  <version>${idempotent.version}</version>
</dependency>
```

Add a storage module (Redis, DynamoDB, NATS, or RDS) for production. Without one, `InMemoryIdempotentStore` is used.

For `@Idempotent`, add `spring-boot-starter-aop`.

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `idempotent.key.header` | `X-Idempotency-Key` | HTTP header; takes precedence over SpEL `key` when set |
| `idempotent.inprogress.max.retries` | `5` | Max polls while another request holds `IN_PROGRESS` |
| `idempotent.inprogress.retry.initial.interval` | `PT0.1S` | Initial backoff (`100ms`, `PT0.1S`, …) |
| `idempotent.inprogress.retry.multiplier` | `2` | Exponential backoff multiplier |

### Payload serialization

Persistent stores share `IdempotentPayloadCodec`.

| Property | Default | Description |
|----------|---------|-------------|
| `idempotent.serialization.strategy` | `json` | `json` (Jackson) or `java` (JDK serialization; payloads must implement `Serializable`) |

`json` enables permissive polymorphic typing by default (a warning is logged at startup). Restrict types with an `IdempotentJsonMapperCustomizer` bean, or replace serialization entirely with a custom `IdempotentPayloadCodec` bean.

Example customizer (restrict packages and enable typing for records / `Object` retrieval):

```java
@Bean
IdempotentJsonMapperCustomizer idempotentJsonMapperCustomizer() {
  return builder -> {
    var ptv = BasicPolymorphicTypeValidator.builder()
        .allowIfBaseType("com.myapp.")
        .build();
    builder.polymorphicTypeValidator(ptv)
        .setDefaultTyping(new DefaultTypeResolverBuilder(
            ptv, DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY,
            JsonTypeInfo.Id.CLASS, "@class") {
          @Override
          public boolean useForType(JavaType t) {
            return true;
          }
        });
  };
}
```

Defining your own customizer replaces the library default; configure polymorphic typing explicitly if you need it.

## Usage

### `@Idempotent`

```java
@Idempotent(key = "#orderId", duration = "5m")
@PostMapping("/orders")
public Order createOrder(@RequestBody CreateOrderRequest request) {
  return orderService.create(request);
}
```

- **Key** — request header (if present), else SpEL `key`. Empty key: method runs without idempotency (warned once per method).
- **duration** — ISO-8601 or Spring short form (`5m`, `100ms`, `PT5M`).
- **hashKey** — SHA-256 hash of the key string before storage (useful for large or sensitive keys).

### `IdempotentService`

```java
// Untyped (uses Object.class for deserialization)
String result = idempotentService.execute("payment-123",
    () -> processPayment("123"),
    Duration.ofMinutes(5));

// With process scope (same key, different process = separate entries)
String result = idempotentService.execute("payment-123", "charge",
    () -> processPayment("123"),
    Duration.ofMinutes(5));

// Typed (preferred when return type is known)
Order order = idempotentService.execute("order-789", Order.class,
    () -> orderService.get("789"),
    Duration.ofMinutes(30));
```

**Behavior:**

- Successful results are cached, including `null` (void methods).
- Non-2xx `ResponseEntity` responses are not cached; the entry is removed so the client can retry.
- Operation exceptions propagate as-is; the in-progress entry is removed before the throw.
- `IdempotentException` / `IdempotentWaitExhaustedException` are reserved for library conditions.

## Storage

| Module | Link |
|--------|------|
| In-memory (default) | `InMemoryIdempotentStore` |
| Redis | [idempotent-redis](../idempotent-redis/README.md) |
| DynamoDB | [idempotent-dynamo](../idempotent-dynamo/README.md) |
| NATS KV | [idempotent-nats](../idempotent-nats/README.md) |
| JDBC | [idempotent-rds](../idempotent-rds/README.md) |

### Custom `IdempotentStore`

Implement `IdempotentStore` and register a `@Bean` (use `@Primary` if replacing the auto-configured store):

- **`store`** — strict insert only; conflicts throw `IdempotentKeyConflictException`.
- **`update`** — no-op when the key is missing (never resurrect deleted or expired entries).
- **`getValue`** — return `null` for missing or expired entries; remove expired entries on read (best-effort).
- **`remove`** — idempotent delete.

```java
@Bean
@Primary
IdempotentStore customIdempotentStore() {
  return new CustomIdempotentStore();
}
```
