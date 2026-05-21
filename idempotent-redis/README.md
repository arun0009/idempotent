# Idempotent Cache with Redis Storage

To integrate the idempotent cache with Redis into your project, add the following dependency to your `pom.xml` file:

```xml
<dependency>
	<groupId>io.github.arun0009</groupId>
	<artifactId>idempotent-redis</artifactId>
	<!-- get latest idempotent version from Maven Central -->
	<version>${idempotent.version}</version>
</dependency>
```

## Overview

This project provides an idempotent request handling mechanism using Redis for storage/cache. The idempotent cache
ensures that duplicate requests are handled safely and effectively, avoiding unintended side effects. This is
particularly useful in scenarios where the same request might be sent multiple times due to retries or client errors.

## Redis Connection

The library uses the standard Spring Data Redis `RedisConnectionFactory` abstraction. This means it works with
**any** Spring Data Redis driver — Lettuce (Spring Boot's default), Jedis, or any other implementation.

Configure your Redis connection using the standard `spring.data.redis.*` properties:

```properties
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

For cluster, sentinel, SSL, and authentication, use the standard Spring Boot properties:

```properties
# Cluster
spring.data.redis.cluster.nodes=host1:6379,host2:6379,host3:6379

# Sentinel
spring.data.redis.sentinel.master=mymaster
spring.data.redis.sentinel.nodes=host1:26379,host2:26379

# Authentication
spring.data.redis.username=myuser
spring.data.redis.password=secret

# SSL
spring.data.redis.ssl.enabled=true
```

See the [Spring Boot Redis documentation](https://docs.spring.io/spring-boot/reference/data/nosql.html#data.nosql.redis)
for the full list of supported properties.

## Configuration Properties

### Core configuration

See [idempotent-core – Configuration](../idempotent-core/README.md#configuration) for `idempotent.key.header`, in-progress retry settings, and serialization.

### Redis-specific properties

* Redis Enabled

		Property: idempotent.redis.enabled
		Default Value: true
		Description: Set to false to disable Redis auto-configuration entirely (e.g., for testing or training mode).

## Serialization Strategy

Redis supports the shared `idempotent.serialization.strategy` setting:
- `json` (default): serializes keys and values via the shared `IdempotentPayloadCodec` (same Jackson setup as core)
- `java`: uses `JdkSerializationRedisSerializer`

See [idempotent-core README – Payload serialization](../idempotent-core/README.md#payload-serialization-persistent-stores)
for the `IdempotentPayloadCodec` and `IdempotentJsonMapperCustomizer` documentation.

### Custom Redis serializer

To override Redis serialization, define a bean named `idempotentRedisSerializer` (used for both keys and values):

```java
@Bean("idempotentRedisSerializer")
RedisSerializer<Object> idempotentRedisSerializer(IdempotentPayloadCodec codec) {
		return new IdempotentPayloadRedisSerializer<>(codec, Object.class);
}
```

## Example Application Configuration

Here is an example of how you might configure your application using `application.properties`:

```properties
# Idempotent Cache General Properties
idempotent.key.header=X-Idempotency-Key
idempotent.inprogress.max.retries=5
idempotent.inprogress.retry.initial.intervalMillis=100
idempotent.inprogress.retry.multiplier=2

# Redis Connection (standard Spring Boot properties)
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

## Usage

By following these steps and configurations, you can effectively manage idempotent requests using Redis,
ensuring robust and reliable handling of duplicate requests in your application.
