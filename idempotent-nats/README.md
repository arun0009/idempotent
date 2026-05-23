# idempotent-nats

NATS JetStream Key-Value backed `IdempotentStore`.

Upgrading from 2.x? See [docs/MIGRATION.md](../docs/MIGRATION.md#upgrading-to-30-from-2x).

## Dependency

```xml
<dependency>
  <groupId>io.github.arun0009</groupId>
  <artifactId>idempotent-nats</artifactId>
  <version>${idempotent.version}</version>
</dependency>
```

## Connection

Auto-configuration creates a NATS `Connection` and KV bucket from properties unless you provide your own `Connection` bean.

## Properties

Core settings: [idempotent-core – Configuration](../idempotent-core/README.md#configuration).

| Property | Default | Description |
|----------|---------|-------------|
| `idempotent.nats.enabled` | `true` | Disable NATS auto-configuration |
| `idempotent.nats.servers` | `nats://localhost:4222` | Server URLs |
| `idempotent.nats.verbose` | `false` | Verbose connection tracing |
| `idempotent.nats.ping-interval` | `2m` | Ping interval |
| `idempotent.nats.max-reconnects` | `60` | Max reconnect attempts |
| `idempotent.nats.reconnect-wait` | `2s` | Delay between reconnects |
| `idempotent.nats.connection-timeout` | `2s` | Connection timeout |
| `idempotent.nats.auth.type` | — | `BASIC` or `TOKEN` |
| `idempotent.nats.auth.username` | — | BASIC username |
| `idempotent.nats.auth.password` | — | BASIC password |
| `idempotent.nats.auth.token` | — | TOKEN value |
| `idempotent.nats.bucket-config.name` | `idempotent` | KV bucket name |
| `idempotent.nats.bucket-config.ttl` | `1d` | Bucket max age |
| `idempotent.nats.bucket-config.limit-marker` | `1s` | Limit marker TTL |
| `idempotent.nats.bucket-config.storage-type` | `Memory` | Bucket storage type |

Serialization: `idempotent.serialization.strategy` (`json` | `java`). See [payload serialization](../idempotent-core/README.md#payload-serialization).

### SSL/TLS

Configure a Spring SSL bundle named `nats-client` — see [Spring Boot SSL](https://docs.spring.io/spring-boot/reference/features/ssl.html).

```properties
spring.ssl.bundle.jks.nats-client.keystore.location=classpath:client.p12
spring.ssl.bundle.jks.nats-client.keystore.password=password
spring.ssl.bundle.jks.nats-client.truststore.location=classpath:ca.jks
spring.ssl.bundle.jks.nats-client.truststore.password=password
```

### Key encoding

Invalid KV key characters are handled by Base64-encoding the key and process name when needed. This is transparent to callers.

## Example

```properties
idempotent.nats.enabled=true
idempotent.nats.servers=nats://localhost:4222
idempotent.nats.bucket-config.name=idempotent
```

Insert uses `kv.create` (strict). Update uses revision-based CAS so missing keys are not resurrected.
