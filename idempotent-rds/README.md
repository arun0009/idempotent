<div align="center">

# idempotent-rds

[![Maven Central](https://img.shields.io/maven-central/v/io.github.arun0009/idempotent-rds?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.arun0009/idempotent-rds)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://adoptium.net)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-brightgreen.svg)](https://spring.io/projects/spring-boot)

</div>

**Idempotency on the database you already trust.** Auto-detects PostgreSQL, MySQL, MariaDB, and H2; one small table, no new infrastructure.

## When RDS / JDBC is the right choice

- You standardize on Postgres or MySQL and don't want a separate datastore just for idempotency.
- You want auditable, queryable idempotency state alongside your business data.
- You already replicate / back up this database.

## Install

```xml
<dependency>
  <groupId>io.github.arun0009</groupId>
  <artifactId>idempotent-rds</artifactId>
  <version>${idempotent.version}</version>
</dependency>
```

This module is intentionally lean — no driver, no connection pool. Add `spring-boot-starter-jdbc`, your JDBC driver, and configure `spring.datasource.*` as usual.

## Schema

Create the table once. Default name is `idempotent`:

```sql
CREATE TABLE idempotent (
  key_id       VARCHAR(255) NOT NULL,
  process_name VARCHAR(255) NOT NULL,
  status       VARCHAR(50)  NOT NULL,
  expires_at   BIGINT       NOT NULL,
  response     TEXT,
  PRIMARY KEY (key_id, process_name)
);

CREATE INDEX idx_expires_at ON idempotent (expires_at);
```

`expires_at` is epoch **milliseconds**. The composite primary key serves point lookups; the `expires_at` index serves the cleanup task. Unrecognized dialects fall back to a generic implementation with a startup warning.

## Under the hood

| Operation | SQL |
|-----------|-----|
| First claim | `INSERT` — duplicate primary key → `IdempotentKeyConflictException` |
| Complete | `UPDATE ... WHERE key_id = ? AND process_name = ?` — zero rows updated = no-op (safe) |
| Read | `SELECT` + shared lazy delete on expiry |
| Expiry | `RdsCleanupTask` batches deletes on a schedule; lazy delete also runs on every read |

## Configuration

Shared retry / header / serialization properties: [idempotent-core – Configuration](../idempotent-core/README.md#configuration).

| Property | Default | Description |
|----------|---------|-------------|
| `idempotent.rds.enabled` | `true` | Disable auto-configuration |
| `idempotent.rds.table-name` | `idempotent` | Table name |
| `idempotent.rds.cleanup.enabled` | `true` | Scheduled expiry sweep |
| `idempotent.rds.cleanup.fixed-delay` | `PT1M` | Time between cleanup runs (`60s`, `PT1M`, …) |
| `idempotent.rds.cleanup.batch-size` | `1000` | Rows per delete batch (avoids long locks) |
| `idempotent.serialization.strategy` | `json` | Shared codec strategy |

The cleanup task is safe on multiple instances — each expired row is deleted at most once.

Back to the [project overview](../README.md).
