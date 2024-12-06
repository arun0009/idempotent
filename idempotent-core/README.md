# Idempotent Core
The idempotent-core module provides utilities to help make your APIs idempotent. It includes mechanisms to handle
idempotent keys and manage in-progress requests with configurable retries and backoff intervals. This core library
can be used with an in-memory cache or integrated with your own persistence layer by implementing the
IdempotentStore interface.

### Features

* **Idempotent Key Header**: Configurable HTTP header to check for idempotent keys in requests.
* **Retry Mechanism**: Configurable retry logic for handling concurrent duplicate requests.

### Configuration Properties
The following properties can be configured in your application's properties file to customize the behavior of the
idempotent core module:

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


Example configuration in application.properties:

```properties
idempotent.key.header=X-Idempotency-Key
idempotent.inprogress.max.retries=5
idempotent.inprogress.retry.initial.intervalMillis=100
idempotent.inprogress.retry.multiplier=2
```
## Usage

#### Including Idempotent Core

To include idempotent-core in your project, add the following dependency to your pom.xml:

```xml
<dependency>
		<groupId>io.github.arun0009</groupId>
		<artifactId>idempotent-core</artifactId>
		<!-- get latest idempotent version from maven central -->
		<version>${idempotent.version}</version>
</dependency>
```

### In-Memory Cache

By default, the idempotent-core module can be used with an in-memory cache. This is useful for scenarios where the
application does not require a persistent store.

### Custom Persistence
To integrate with your own persistence layer, implement the IdempotentStore interface provided by the idempotent-core module.
This allows you to use a database or any other storage mechanism to store and manage idempotent keys.

Example implementation:

```java
public class MyCustomIdempotentStore implements IdempotentStore {
// Implement the methods for your custom persistence logic
}
```

Configure your application to use the custom implementation:

```java
@Configuration
public class IdempotentConfig {

		@Bean
		public IdempotentStore idempotentStore() {
				return new MyCustomIdempotentStore();
		}

		@Bean
		public IdempotentAspect idempotentAspect(IdempotentStore idempotentStore) {
				return new IdempotentAspect(idempotentStore);
		}
}
```

### Redis and DynamoDB Implementations
While idempotent-core provides the core functionality, you can use specific implementations like Redis or DynamoDB
by including the respective modules ([idempotent-redis](../idempotent-redis), [idempotent-dynamo](../idempotent-dynamo)).
However, if you prefer to use a different storage solution, you can implement your own store as described above.
