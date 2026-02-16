# Idempotent Cache with RDS Storage (JDBC)

To integrate the idempotent cache with RDS (JDBC) storage into your project, add the following dependency to your pom.xml file:

```xml
<dependency>
		<groupId>io.github.arun0009</groupId>
		<artifactId>idempotent-rds</artifactId>
		<version>${idempotent.version}</version>
</dependency>
```

## Overview

This module provides an idempotent request handling mechanism using a Relational Database (via JDBC/JdbcTemplate) for storage. It requires a database table to be created.

## Database Schema

You must create a table for storing idempotent keys. The default table name is `idempotent`.

**MySQL / PostgreSQL Example:**

```sql
CREATE TABLE idempotent (
		key_id VARCHAR(255) NOT NULL,
		process_name VARCHAR(255) NOT NULL,
		status VARCHAR(50),
		expiration_time_millis BIGINT,
		response TEXT,
		PRIMARY KEY (key_id, process_name)
);

CREATE INDEX idx_expiration_time ON idempotent(expiration_time_millis);
```

## Configuration Properties

### General Properties

See main [README](../README.md) for general idempotent configuration.

### RDS Configuration

*   Table Name

		Property: `idempotent.rds.table.name`
		Default Value: `idempotent`
		Description: The name of the database table to use.

*   Cleanup Schedule

		Property: `idempotent.rds.cleanup.fixedDelay`
		Default Value: `60000` (1 minute)
		Description: Fixed delay in milliseconds for the cleanup task that removes expired keys.

*   Cleanup Batch Size

		Property: `idempotent.rds.cleanup.batch.size`
		Default Value: `1000`
		Description: Number of expired keys to delete in each batch to prevent long-running database locks.

## Distributed Systems Support

This module is designed to work in a distributed environment with multiple application instances.

1.  **Atomic Operations**: It relies on the database's primary key constraints (`key_id`, `process_name`) to ensure that only one instance can successfully `INSERT` (lock) a specific key.
2.  **Concurrency**: If multiple containers try to process the same request simultaneously, the database will reject duplicate inserts with a constraint violation. The application handles this by identifying it as a duplicate request.
3.  **Cleanup**: The `RdsCleanupTask` runs on all instances by default. It executes batched `DELETE` statements for expired rows to prevent long-running database locks. This is safe to run concurrently as database transactions ensure consistency (deleting an already deleted row is a no-op).

## Dependencies

This module relies on `spring-boot-starter-jdbc`. You must configure your `DataSource` as per standard Spring Boot configuration (e.g., `spring.datasource.url`, etc.).

## Performance Tuning

Since this implementation relies on a relational database, performance is critical. Here are some tips to ensure low latency:

1.  **Indexes**: The provided schema uses a composite Primary Key `(key_id, process_name)`. This creates a clustered index (in MySQL/InnoDB) which is the fastest way to look up records. **Do not remove this.**
2.  **Connection Pooling**: Use a production-grade connection pool like HikariCP (default in Spring Boot).
		*   Set `maximum-pool-size` appropriately for your concurrency level.
		*   Set `minimum-idle` to keep connections warm.
3.  **Payload Size**: The `response` column stores the JSON response. If your responses are very large (MBs), retrieving them will add latency. Consider keeping responses concise or using a hybrid approach if payloads are massive.
4.  **Database Hardware**: Ensure your RDS instance has sufficient IOPS, as idempotency checks involve frequent reads and writes.
