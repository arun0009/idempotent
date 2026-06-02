<div align="center">

# idempotent-redis

[![Maven Central](https://img.shields.io/maven-central/v/io.github.arun0009/idempotent-redis?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.arun0009/idempotent-redis)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://adoptium.net)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-brightgreen.svg)](https://spring.io/projects/spring-boot)

</div>

**Sub-millisecond idempotency on the Redis you already run.** Reuses your app's `RedisConnectionFactory` and aligns native Redis TTL with each entry's `expiresAt`.

## When Redis is the right choice

- Redis already lives in your stack (cache, sessions, rate limits).
- You want single-digit-ms lookups at high throughput.
- You want per-key TTL handled by Redis itself, not a cron.

## Install

```xml
<dependency>
	<groupId>io.github.arun0009</groupId>
	<artifactId>idempotent-redis</artifactId>
	<version>${idempotent.version}</version>
</dependency>
```

```properties
spring.data.redis.host=redis.internal
spring.data.redis.port=6379
```

Cluster, sentinel, TLS, and auth use standard Spring Boot properties — see [Spring Boot Redis docs](https://docs.spring.io/spring-boot/reference/data/nosql.html#data.nosql.redis). Now annotate a method (see [core README](../idempotent-core/README.md)).

## Under the hood

| Operation | Redis primitive | Why it matters |
|-----------|-----------------|----------------|
| First claim | `SET key value NX PX <ttl>` | Two callers cannot both think they were first |
| Complete | `SET key value XX PX <ttl>` | Never resurrects a key that was deleted or expired |
| Read | `GET key` + shared lazy delete | Expired entries are removed on read so the key is reusable |
| Expiry | Native Redis TTL aligned with `expiresAt` | Self-evicting — no cleanup job needed |

## Configuration

Shared retry / header / serialization properties live in [idempotent-core – Configuration](../idempotent-core/README.md#configuration).

| Property | Default | Description |
|----------|---------|-------------|
| `idempotent.redis.enabled` | `true` | Set `false` to disable Redis auto-configuration |
| `idempotent.serialization.strategy` | `json` | `json` (Jackson) or `java` (`Serializable`) |

### Custom serializer

Override Redis serialization with a single bean (used for keys and values):

```java
@Bean("idempotentRedisSerializer")
RedisSerializer<Object> idempotentRedisSerializer(IdempotentPayloadCodec codec) {
	return new IdempotentPayloadRedisSerializer<>(codec, Object.class);
}
```

Back to the [project overview](../README.md).
