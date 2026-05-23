<div align="center">

# idempotent-nats

[![Maven Central](https://img.shields.io/maven-central/v/io.github.arun0009/idempotent-nats?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.arun0009/idempotent-nats)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://adoptium.net)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-brightgreen.svg)](https://spring.io/projects/spring-boot)

</div>

**Idempotency that lives in your messaging plane.** Stores keys in JetStream KV with revision-based CAS updates — no separate datastore for idempotency.

## When NATS KV is the right choice

- NATS is already your messaging or control plane.
- You want idempotency state replicated by the same JetStream cluster as your events.
- You'd rather not run Redis or DynamoDB just for idempotency keys.

## Install

```xml
<dependency>
  <groupId>io.github.arun0009</groupId>
  <artifactId>idempotent-nats</artifactId>
  <version>${idempotent.version}</version>
</dependency>
```

Auto-configuration creates a NATS `Connection` and KV bucket from properties — or supply your own `Connection` bean and the library will reuse it.

```properties
idempotent.nats.servers=nats://localhost:4222
idempotent.nats.bucket-config.name=idempotent
```

Now annotate (see [core README](../idempotent-core/README.md)) and you’re done.

## Under the hood

| Operation | JetStream KV |
|-----------|--------------|
| First claim | `kv.create` — fails if the key exists, surfaces as `IdempotentKeyConflictException` |
| Complete | `kv.update(key, value, revision)` — **revision CAS**, never resurrects a deleted key |
| Read | `kv.get` + shared lazy delete on expiry |
| Expiry | Per-message TTL from `expiresAt`, plus bucket TTL as a safety net |

### Key encoding (automatic)

NATS KV rejects some characters and wildcards. The library validates each key and **transparently Base64-encodes** invalid keys and the process-name suffix — no manual sanitization in your code.

## Configuration

Shared retry / header / serialization properties: [idempotent-core – Configuration](../idempotent-core/README.md#configuration).

| Property | Default | Description |
|----------|---------|-------------|
| `idempotent.nats.enabled` | `true` | Disable auto-configuration |
| `idempotent.nats.servers` | `nats://localhost:4222` | Broker URLs |
| `idempotent.nats.verbose` | `false` | Verbose connection tracing |
| `idempotent.nats.{ping-interval, max-reconnects, reconnect-wait, connection-timeout}` | `2m` / `60` / `2s` / `2s` | Standard NATS client tunables — defaults match the official client |
| `idempotent.nats.auth.type` | — | `BASIC` or `TOKEN` |
| `idempotent.nats.auth.username` / `password` | — | BASIC credentials |
| `idempotent.nats.auth.token` | — | TOKEN value |
| `idempotent.nats.bucket-config.name` | `idempotent` | KV bucket name |
| `idempotent.nats.bucket-config.ttl` | `1d` | Bucket max age (safety net) |
| `idempotent.nats.bucket-config.limit-marker` | `1s` | Per-message TTL marker |
| `idempotent.nats.bucket-config.storage-type` | `Memory` | `Memory` or `File` |
| `idempotent.serialization.strategy` | `json` | Shared codec strategy |

### SSL/TLS

Define a Spring SSL bundle named **`nats-client`** — the NATS connection picks it up automatically. See [Spring Boot SSL](https://docs.spring.io/spring-boot/reference/features/ssl.html).

```properties
spring.ssl.bundle.jks.nats-client.keystore.location=classpath:client.p12
spring.ssl.bundle.jks.nats-client.keystore.password=${NATS_KS_PASSWORD}
spring.ssl.bundle.jks.nats-client.truststore.location=classpath:ca.jks
spring.ssl.bundle.jks.nats-client.truststore.password=${NATS_TS_PASSWORD}
```

Back to the [project overview](../README.md).
