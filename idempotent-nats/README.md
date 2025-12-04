# Idempotent Cache with Nats Storage/Cache

To integrate the idempotent cache with Nats into your project, add the following dependency to your pom.xml file:

```xml

<dependency>
		<groupId>io.github.arun0009</groupId>
		<artifactId>idempotent-nats</artifactId>
		<!-- get latest idempotent version from maven central -->
		<version>${idempotent.version}</version>
</dependency>
```

## Overview

This project provides an idempotent request handling mechanism using Nats KV for storage/cache. The idempotent cache
ensures that duplicate requests are handled safely and effectively, avoiding unintended side effects. This is
particularly useful in scenarios where the same request might be sent multiple times due to retries or client errors.

## Configuration Properties

Below are the properties that can be configured for the idempotent Nats cache. These properties can be set in your
application's configuration file (e.g., application.properties or application.yml).

### General Properties

* NATS Client Enabled

		Property: idempotent.nats.enable
		Default Value: true
		Description: Enable or disable NATS client configuration.

* NATS Servers

		Property: idempotent.nats.servers
		Default Value: [nats://localhost:4222]
		Description: Add an array of servers to the list of known servers.

* NATS Verbose Mode

		Property: idempotent.nats.verbose
		Default Value: false
		Description: Turn on verbose mode with the server and Enable connection trace messages. Messages are printed to standard out. This option is for very fine-grained debugging of connection issues.

* NATS Ping Interval

		Property: idempotent.nats.ping-interval
		Default Value: 2 minutes
		Description: Set the interval between attempts to ping the server.

* NATS Maximum Reconnects

		Property: idempotent.nats.max-reconnects
		Default Value: 60
		Description: Maximum number of reconnect attempts to the server.

* NATS Reconnect Wait

		Property: idempotent.nats.reconnect-wait
		Default Value: 2 seconds
		Description: Set the time to wait between reconnect attempts to the same server.

* NATS Connection Timeout

		Property: idempotent.nats.connection-timeout
		Default Value: 2 seconds
		Description: Set the timeout for connection attempts.

* NATS Connection Timeout

			Property: idempotent.nats.connection-timeout
			Default Value: 2 seconds
			Description: Set the timeout for connection attempts.

* NATS Authentication Type

		Property: idempotent.nats.auth.type
		Default Value: null
		Description: Type of authentication to use. Can be either BASIC or TOKEN.

* NATS Username

		Property: idempotent.nats.auth.username
		Default Value: null
		Description: The username to use for BASIC authentication.

* NATS Password

		Property: idempotent.nats.auth.password
		Default Value: null
		Description: The password to use for BASIC authentication.

* NATS Token

		Property: idempotent.nats.auth.token
		Default Value: null
		Description: The token to use for TOKEN authentication.

* NATS Bucket Name

		Property: idempotent.nats.bucket-config.name
		Default Value: idempotent
		Description: Name of the bucket used by the idempotent NATS client.

* NATS Bucket TTL

		Property: idempotent.nats.bucket-config.ttl
		Default Value: 1 day
		Description: The maximum age for items in the bucket.

* NATS Bucket Limit Marker

		Property: idempotent.nats.bucket-config.limit-marker
		Default Value: 1 second
		Description: The limit marker TTL duration. Server accepts 1 second or more. Null or empty has the effect of clearing the limit marker ttl.

* NATS Bucket Storage Type

		Property: idempotent.nats.bucket-config.storage-type
		Default Value: Memory
		Description: Storage type used for the bucket.

## SSL/TLS Configuration

To use SSL/TLS for secure communication with the NATS server, configure an SSL bundle in Spring Boot,
see [spring-ssl-documentation](https://docs.spring.io/spring-boot/reference/features/ssl.html).
The NATS client will automatically use an SSL bundle named **nats-client** defined in the application configuration to
establish secure connections.

Example:

```properties
spring.ssl.bundle.jks.nats-client.protocol=TLSv1.3,
spring.ssl.bundle.jks.nats-client.keystore.location=classpath:client.p12,
spring.ssl.bundle.jks.nats-client.keystore.password=password,
spring.ssl.bundle.jks.nats-client.truststore.location=classpath:ca.jks,
spring.ssl.bundle.jks.nats-client.truststore.password=password
```

## Using Custom Nats Configuration

By default, the library will create and configure the Connection and KV using the provided properties. If you need to
customize the NATS connection, you can define your own Connection bean. The configuration is conditional and will only
be applied if a Connection bean is not already defined in your application context.

## Key Encoding

NATS KV does not accept all characters as valid keys. The library automatically handles this by validating keys and
Base64-encoding them when they contain invalid characters. This encoding process is transparent and ensures
compatibility with NATS key-value storage without requiring any additional configuration.

## Example Application Configuration

Here is an example of how you might configure your application using application.properties:

```properties
# Idempotent Cache General Properties
idempotent.key.header=X-Idempotency-Key
idempotent.inprogress.max.retries=5
idempotent.inprogress.retry.initial.intervalMillis=100
idempotent.inprogress.retry.multiplier=2
# Nats Configuration
idempotent.nats.enable=true
idempotent.nats.servers=nats://localhost:4222
idempotent.nats.bucket-config.name=idempotent
```

## Usage

By following these steps and configurations, you can effectively manage idempotent requests using Nats,
ensuring robust and reliable handling of duplicate requests in your application. If you need to customize the Nats
connection, you can provide your own Connection bean in your application context.
