# Idempotent

Idempotent is a lightweight Spring Java library for idempotency at the **method and service** level—not only HTTP APIs. Use `@Idempotent` on any Spring-managed method (controllers, services, scheduled jobs) or call `IdempotentService` directly from your code. Duplicate invocations with the same key return the same result; concurrent duplicates wait for the first run to finish. Storage backends include Redis, DynamoDB, NATS, and RDS (in-memory when none is configured).

<img src="./idempotent.png" alt="Idempotent">

Upgrading from 2.x? See [docs/MIGRATION.md](docs/MIGRATION.md#upgrading-to-30-from-2x) (Java 17+, Spring Boot 4.x).

## How it works

1. **First request** — store an `IN_PROGRESS` entry, run the operation, then mark `COMPLETED` (or remove the entry on failure).
2. **Duplicate while in progress** — poll with exponential backoff until `COMPLETED` or retries are exhausted.
3. **Duplicate after success** — return the cached response without re-running the operation.
4. **Expired key** — entry is removed on read; a new request may claim the key.

Set TTL longer than your slowest operation so an entry is not evicted while work is still running.

## Features

- **Annotation or programmatic API** — `@Idempotent` on methods, or `IdempotentService` for service/function-level control without AOP
- **Pluggable storage** — Redis, DynamoDB, NATS KV, JDBC (RDS), or in-memory
- **Client or server keys** — HTTP header (`X-Idempotency-Key` by default) or SpEL on `@Idempotent`
- **Shared serialization** — one `IdempotentPayloadCodec` across persistent stores ([core docs](idempotent-core/README.md#payload-serialization))

## Getting started

### `@Idempotent` on methods

Works on any Spring bean method. REST controllers often pair it with an idempotency HTTP header; services can use SpEL keys only. Add `spring-boot-starter-aop` so the aspect is woven in (not needed for `IdempotentService` alone).

```xml
<dependency>
  <groupId>io.github.arun0009</groupId>
  <artifactId>idempotent-redis</artifactId>
  <version>${idempotent.version}</version>
</dependency>
```

Configure your store ([Redis](idempotent-redis/README.md), [DynamoDB](idempotent-dynamo/README.md), [NATS](idempotent-nats/README.md), or [RDS](idempotent-rds/README.md)), then annotate methods:

```java
@Idempotent(key = "#paymentDetails", duration = "PT5M", hashKey = true)
@PostMapping("/payments")
public PaymentResponse postPayment(@RequestBody PaymentDetails paymentDetails) {
  return paymentService.charge(paymentDetails);
}
```

`duration` accepts ISO-8601 (`PT5M`) or Spring short form (`5m`, `100ms`).

### `IdempotentService` (programmatic)

Use from `@Service` classes, batch jobs, or anywhere you prefer explicit keys and TTL without annotations:

```java
return idempotentService.execute(
    paymentId,
    "process-payment",
    () -> callPaymentGateway(paymentId),
    Duration.ofMinutes(30));
```

When the return type is known, prefer the typed overload (`execute(key, MyType.class, operation, ttl)`) so stores can deserialize without relying on Jackson `@class` metadata.

See [idempotent-core](idempotent-core/README.md) for configuration, serialization, and custom stores.

## Contributing

Issues and pull requests are welcome on [GitHub](https://github.com/arun0009/idempotent).
