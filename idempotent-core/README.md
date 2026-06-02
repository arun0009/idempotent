<div align="center">

# idempotent-core

[![Maven Central](https://img.shields.io/maven-central/v/io.github.arun0009/idempotent-core?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.arun0009/idempotent-core)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://adoptium.net)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-brightgreen.svg)](https://spring.io/projects/spring-boot)

</div>

The engine behind every backend: **one state machine, one store contract, one serialization pipeline.** Whether you annotate a controller, call `IdempotentService` from a job, or plug in your own database — the guarantees are identical.

## Two paths, same guarantees

| | `@Idempotent` annotation | `IdempotentService` (programmatic) |
|---|---|---|
| **Use when** | Spring-managed methods (controllers, services, `@Scheduled`) | Code outside AOP — message consumers, batch jobs, libraries |
| **Requires** | `spring-boot-starter-aop` | Nothing extra |
| **Key source** | HTTP header (wins) or SpEL | Explicit `String` you provide |
| **Return type** | Inferred from method signature | Explicit `Class<T>` (preferred) or `Object.class` |
| **TTL** | `duration = "5m" \| "PT5M" \| "100ms"` | `Duration` parameter |

You can mix both in the same app — they share the same `IdempotentStore`.

## Install

Pulled in transitively by every storage module. Add directly if you implement your own store:

```xml
<dependency>
	<groupId>io.github.arun0009</groupId>
	<artifactId>idempotent-core</artifactId>
	<version>${idempotent.version}</version>
</dependency>
```

With no storage module on the classpath, `InMemoryIdempotentStore` is auto-configured — ideal for tests and local development.

## `@Idempotent`

```java
@Idempotent(key = "#orderId", duration = "5m", hashKey = false)
public Order fulfill(String orderId) { ... }
```

| Attribute | Default | Meaning |
|-----------|---------|---------|
| `key` | `""` | SpEL expression. Combined with HTTP `X-Idempotency-Key` when present — header wins. |
| `duration` | `"PT5M"` | Entry TTL. Accepts ISO-8601 (`PT5M`) or Spring short form (`5m`, `100ms`). |
| `hashKey` | `false` | Store SHA-256 of the key (handy for large request bodies or PII). |

> **Empty key?** The method runs **without** idempotency and the library logs a warning once per method — so misconfiguration is impossible to miss in production logs.

## `IdempotentService`

```java
// Same key + different process name = independent entries
Order order = idempotentService.execute(
		"order-789",        // key
		"fulfill-order",    // process scope
		Order.class,        // explicit return type (preferred)
		() -> fulfillment.run("order-789"),
		Duration.ofMinutes(30));
```

Other overloads exist — `execute(key, supplier, ttl)`, `execute(key, processName, supplier, ttl)`, untyped variants — see the [Javadoc](src/main/java/io/github/arun0009/idempotent/core/service/IdempotentService.java).

### What happens when…

| Situation | Behavior |
|-----------|----------|
| Operation succeeds (incl. `null` / `void`) | Cached as `COMPLETED` for the TTL |
| Operation throws | In-progress entry removed; exception propagates unchanged |
| Operation returns non-2xx `ResponseEntity` | Entry removed; client can retry |
| Wait budget exhausted | `IdempotentWaitExhaustedException` |
| Two callers race the strict insert | Loser sees `IdempotentKeyConflictException`, re-fetches, joins the existing flow |

`IdempotentException` and `IdempotentWaitExhaustedException` are library-only. Your domain exceptions stay yours.

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `idempotent.key.header` | `X-Idempotency-Key` | HTTP header consulted by the aspect |
| `idempotent.inprogress.max.retries` | `5` | Polls while another caller holds `IN_PROGRESS` |
| `idempotent.inprogress.retry.initial.interval` | `PT0.1S` | Initial backoff (`100ms`, `PT0.1S`, …) |
| `idempotent.inprogress.retry.multiplier` | `2` | Exponential multiplier |

### Payload serialization

All persistent stores route responses through one **`IdempotentPayloadCodec`**.

| Property | Default | Description |
|----------|---------|-------------|
| `idempotent.serialization.strategy` | `json` | `json` (Jackson) or `java` (requires `Serializable`) |

`json` enables permissive polymorphic typing by default — convenient for `Object.class` reads, with a startup warning. Lock it down with a customizer bean:

```java
@Bean
IdempotentJsonMapperCustomizer customizer() {
	var ptv = BasicPolymorphicTypeValidator.builder()
			.allowIfBaseType("com.myapp.")
			.build();
	return builder -> builder.polymorphicTypeValidator(ptv);
}
```

Or replace serialization entirely with your own `IdempotentPayloadCodec` bean.

## Custom `IdempotentStore`

Four methods, explicit contracts — the same shape Redis, Dynamo, NATS, and RDS satisfy. The `IdempotentValues` helper covers expiry and lazy delete, so you implement only the backend primitives.

| Method | Contract |
|--------|----------|
| `store(key, value)` | Strict insert. Throw `IdempotentKeyConflictException` if the key exists. Never overwrite. |
| `update(key, value)` | No-op if missing. Never resurrect a deleted or expired entry. |
| `getValue(key, type)` | Return `null` for missing/expired. Lazy-delete expired entries (best-effort). |
| `remove(key)` | Idempotent delete; tolerate missing keys. |

```java
@Bean
@Primary
IdempotentStore myStore() {
	return new MyIdempotentStore();
}
```

Back to the [project overview](../README.md).
