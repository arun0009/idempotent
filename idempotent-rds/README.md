# idempotent-rds

JDBC-backed `IdempotentStore` using `JdbcTemplate`.

Upgrading from 2.x? See [docs/MIGRATION.md](../docs/MIGRATION.md#upgrading-to-30-from-2x).

## Dependency

```xml
<dependency>
  <groupId>io.github.arun0009</groupId>
  <artifactId>idempotent-rds</artifactId>
  <version>${idempotent.version}</version>
</dependency>
```

This module does not include a connection pool or JDBC driver. Add `spring-boot-starter-jdbc` plus your driver (e.g. `mysql-connector-j`, `postgresql`) and configure `spring.datasource.*` as usual.

## Schema

Default table name: `idempotent`.

```sql
CREATE TABLE idempotent (
  key_id VARCHAR(255) NOT NULL,
  process_name VARCHAR(255) NOT NULL,
  status VARCHAR(50) NOT NULL,
  expires_at BIGINT NOT NULL,
  response TEXT,
  PRIMARY KEY (key_id, process_name)
);

CREATE INDEX idx_expires_at ON idempotent (expires_at);
```

`expires_at` is epoch milliseconds. Upgrading from 2.x? Rename column `expiration_time_millis` → `expires_at` — see [MIGRATION.md](../docs/MIGRATION.md#upgrading-to-30-from-2x).

## Properties

Core settings: [idempotent-core – Configuration](../idempotent-core/README.md#configuration).

| Property | Default | Description |
|----------|---------|-------------|
| `idempotent.rds.enabled` | `true` | Disable RDS auto-configuration |
| `idempotent.rds.table-name` | `idempotent` | Table name |
| `idempotent.rds.cleanup.enabled` | `true` | Scheduled cleanup of expired rows |
| `idempotent.rds.cleanup.fixed-delay` | `PT1M` | Delay between cleanup runs (`60s`, `PT1M`, …) |
| `idempotent.rds.cleanup.batch-size` | `1000` | Rows deleted per batch |

Serialization: `idempotent.serialization.strategy` (`json` | `java`). See [payload serialization](../idempotent-core/README.md#payload-serialization).

## Cleanup

RDS has no native TTL. `RdsCleanupTask` deletes expired rows on a schedule. Safe to run on multiple instances; each row is deleted at most once. Expired rows are also removed on read via lazy delete in `getValue`.

## Notes

- Keep the composite primary key `(key_id, process_name)` for fast lookups.
- Size `response` reasonably — large payloads add read/write latency.
- Tune your connection pool (HikariCP) for your concurrency level.
