# Idempotent Cache with Redis Storage/Cache

To integrate the idempotent cache with Rediscache into your project, add the following dependency to your pom.xml file:

```xml
<dependency>
	<groupId>io.github.arun0009</groupId>
	<artifactId>idempotent-redis</artifactId>
	<!-- get latest idempotent version from maven central -->
	<version>${idempotent.version}</version>
</dependency>
```

## Overview

This project provides an idempotent request handling mechanism using Redis for storage/cache. The idempotent cache
ensures that duplicate requests are handled safely and effectively, avoiding unintended side effects. This is
particularly useful in scenarios where the same request might be sent multiple times due to retries or client errors.

## Configuration Properties
Below are the properties that can be configured for the idempotent redis cache. These properties can be set in your
application's configuration file (e.g., application.properties or application.yml) or you can pass your own `RedisConfig`
with `JedisConnectionFactory` (see below)

### General Properties

* Idempotent Key Header

		Property: idempotent.key.header
		Default Value: X-Idempotency-Key
		Description: The header name used to pass the idempotency key in HTTP requests.

* In-Progress Request Max Retries

		Property: idempotent.inprogress.max.retries
		Default Value: 5
		Description: The maximum number of retries allowed for in-progress requests to ensure only one request wins.

* In-Progress Status Check Retry Initial Interval

		Property: idempotent.inprogress.retry.initial.intervalMillis
		Default Value: 100
		Description: The initial interval (in milliseconds) between retries for checking the status of in-progress requests.

* In-Progress Retry Multiplier

		Property: idempotent.inprogress.retry.multiplier
		Default Value: 2
		Description: The multiplier used for exponential backoff during retries.

### Redis Configuration

* Redis Standalone Host

		Property: idempotent.redis.standalone.host
		Default Value: (empty)
		Description: The Redis host in hostname:port format.

* Redis Authentication Enabled

		Property: idempotent.redis.auth.enabled
		Default Value: false
		Description: Flag to enable Redis authentication.

* Redis SSL Enabled

		Property: idempotent.redis.ssl.enabled
		Default Value: false
		Description: Flag to enable SSL for Redis connections.

* Redis Authentication Username

		Property: idempotent.redis.auth.username
		Default Value: (empty)
		Description: The username for Redis authentication. Only set if authentication is enabled.

* Redis Authentication Password

		Property: idempotent.redis.auth.password
		Default Value: (empty)
		Description: The password for Redis authentication. Only set if authentication is enabled.

* Redis Cluster Mode Enabled

		Property: idempotent.redis.cluster.enabled
		Default Value: false
		Description: Flag to enable Redis cluster mode.

* Redis Cluster Hosts

		Property: idempotent.redis.cluster.hosts
		Default Value: (empty)
		Description: A comma-separated list of Redis cluster hosts in hostname:port format.

* Redis Sentinel Mode Enabled

		Property: idempotent.redis.sentinel.enabled
		Default Value: false
		Description: Flag to enable Redis Sentinel mode.

* Redis Sentinel Master Host

		Property: idempotent.redis.sentinel.master
		Default Value: (empty)
		Description: The master host in Redis Sentinel mode, in hostname:port format.

* Redis Sentinel Nodes

		Property: idempotent.redis.sentinel.nodes
		Default Value: (empty)
		Description: A comma-separated list of Redis Sentinel nodes in hostname:port format.

## Using Custom Redis Configuration

By default, the library will create and configure the JedisConnectionFactory using the provided properties. However, if you want to pass your own JedisConnectionFactory bean, you can do so by defining a bean named IdempotentCache. This configuration is conditional and will only be applied if a bean with the name IdempotentCache is not already present.

### Custom JedisConnectionFactory Bean Configuration

If you prefer to configure the JedisConnectionFactory yourself, you can define it as follows (you don't have to set Redis
Configuration in properties if you choose this option):

```java
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;

@Configuration
public class RedisConfig {

		@Bean(name = "IdempotentCache")
		public JedisConnectionFactory jedisConnectionFactory() {
				JedisConnectionFactory factory = new JedisConnectionFactory();
				// Customize the factory based on properties
				return factory;
		}
}
```


## Jackson Customization

By default, the library uses a permissive Jackson configuration to ensure compatibility with various return types (like
List, Map, etc.). This triggers a warning at startup: `Using an unrestricted polymorphic type validator...`
To secure your application and restrict deserialization to trusted packages, you can provide a bean of type
IdempotentJacksonJsonBuilderCustomizer[IdempotentJacksonJsonBuilderCustomizer.java](src/main/java/io/github/arun0009/idempotent/redis/IdempotentJacksonJsonBuilderCustomizer.java)
```java
@Bean
IdempotentJacksonJsonBuilderCustomizer myCustomizer() {
		return builder -> {
				builder.enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
								.allowIfBaseType(Object.class)
								.allowIfSubType("java.")
								.allowIfSubType("com.mycompany.dto.") // Add your trusted packages here
								.build());
				// Customize the builder as needed
		};
}
```

## Example Application Configuration

Here is an example of how you might configure your application using application.properties:

```properties
# Idempotent Cache General Properties
idempotent.key.header=X-Idempotency-Key
idempotent.inprogress.max.retries=5
idempotent.inprogress.retry.initial.intervalMillis=100
idempotent.inprogress.retry.multiplier=2

# Redis Configuration
idempotent.redis.standalone.host=localhost:6379
idempotent.redis.auth.enabled=false
idempotent.redis.ssl.enabled=false
idempotent.redis.cluster.enabled=false
idempotent.redis.sentinel.enabled=false
```

## Usage

By following these steps and configurations, you can effectively manage idempotent requests using Redis,
ensuring robust and reliable handling of duplicate requests in your application. If you need to customize the Redis
connection, you can provide your own JedisConnectionFactory bean named IdempotentCache.
