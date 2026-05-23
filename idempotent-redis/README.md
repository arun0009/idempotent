# idempotent-redis

Redis-backed `IdempotentStore` using Spring Data Redis.

Upgrading from 2.x? See [docs/MIGRATION.md](../docs/MIGRATION.md#upgrading-to-30-from-2x).

## Dependency

```xml
<dependency>
  <groupId>io.github.arun0009</groupId>
  <artifactId>idempotent-redis</artifactId>
  <version>${idempotent.version}</version>
</dependency>
```

## Redis connection

Uses your application's `RedisConnectionFactory` (Lettuce by default in Spring Boot). Configure with standard `spring.data.redis.*` properties:

```properties
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

Cluster, sentinel, auth, and SSL use the usual Spring Boot Redis properties — see [Spring Boot Redis docs](https://docs.spring.io/spring-boot/reference/data/nosql.html#data.nosql.redis).

## Properties

Core settings (`idempotent.key.header`, in-progress retries, serialization): [idempotent-core – Configuration](../idempotent-core/README.md#configuration).

| Property | Default | Description |
|----------|---------|-------------|
| `idempotent.redis.enabled` | `true` | Set `false` to disable Redis auto-configuration |

Serialization: `idempotent.serialization.strategy` (`json` | `java`). See [payload serialization](../idempotent-core/README.md#payload-serialization).

### Custom Redis serializer

```java
@Bean("idempotentRedisSerializer")
RedisSerializer<Object> idempotentRedisSerializer(IdempotentPayloadCodec codec) {
  return new IdempotentPayloadRedisSerializer<>(codec, Object.class);
}
```

## Example

```properties
idempotent.key.header=X-Idempotency-Key
idempotent.inprogress.max.retries=5
idempotent.inprogress.retry.initial.interval=100ms
idempotent.inprogress.retry.multiplier=2

spring.data.redis.host=localhost
spring.data.redis.port=6379
```

Keys use Redis TTL aligned with each entry's `expiresAt` (`SET NX` on insert, `SET XX` on update).
