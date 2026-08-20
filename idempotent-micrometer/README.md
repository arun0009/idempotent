<div align="center">

# idempotent-micrometer

[![Maven Central](https://img.shields.io/maven-central/v/io.github.arun0009/idempotent-micrometer?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.arun0009/idempotent-micrometer)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://adoptium.net)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-brightgreen.svg)](https://spring.io/projects/spring-boot)

</div>

**Micrometer meters for every idempotent execution.** Optional module: add it when you already have a `MeterRegistry`; leave it off and `IdempotentService` uses a no-op.

## Install

```xml
<dependency>
	<groupId>io.github.arun0009</groupId>
	<artifactId>idempotent-micrometer</artifactId>
	<version>${idempotent.version}</version>
</dependency>
```

No extra properties. If a `MeterRegistry` bean is present, auto-config wires `MicrometerIdempotentMetrics`. If it is absent, core falls back to `IdempotentMetrics.NOOP`.

## Meters

| Meter | Type | Tags |
|-------|------|------|
| `idempotent.executions` | Counter | `process`, `outcome` — `hit`, `hit_after_wait`, `new_success`, `new_failure`, `wait_exhausted` |
| `idempotent.operations` | Timer | `process`, `outcome` — `success`, `failure` |
| `idempotent.conflicts` | Counter | `process` |

Each `execute()` increments `idempotent.executions` once with its **terminal** outcome. The timer is recorded only when the operation actually ran (`NEW_SUCCESS` / `NEW_FAILURE`). A lost insert race increments `idempotent.conflicts` separately, then the request still records one terminal outcome (usually `hit` or `hit_after_wait`).

| Outcome | Meaning |
|---------|---------|
| `hit` | Completed entry already in the store |
| `hit_after_wait` | Waited for another caller, then returned their result |
| `new_success` | This caller ran the operation and cached it |
| `new_failure` | Operation threw, or returned a non-2xx `ResponseEntity` (not cached) |
| `wait_exhausted` | In-progress wait budget ran out |

`process` is the method's declaring type plus name, for example `__PaymentController.pay()`.

## Custom `IdempotentMetrics`

The SPI lives in `idempotent-core`. A user `IdempotentMetrics` bean replaces the Micrometer implementation (and the no-op) — it does not run alongside it.

Back to the [project overview](../README.md).
