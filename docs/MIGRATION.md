# Migration guide

Breaking changes for major releases are documented here by version. When upgrading, read the section for your target release.

## Upgrading to 3.0 (from 2.x)

This release contains intentional breaking changes to simplify configuration and align module behavior.

### Platform baseline

- Java 17+
- Spring Boot 4.x
- Jackson 3 (`tools.jackson.*`)

### Breaking changes

#### `@Idempotent` API

- Removed `ttlInSeconds` (2.x releases before 2.4); use `duration` only (ISO-8601), e.g. `duration = "PT5M"`

#### Redis module

- Removed Jedis-specific internal connection setup
- Removed `idempotent.redis.standalone.*`, `cluster.*`, `sentinel.*`, `auth.*`, `ssl.*` properties
- Library now uses your app's `RedisConnectionFactory`
- Configure Redis with standard `spring.data.redis.*` properties
- JSON serialization now uses the shared `IdempotentPayloadCodec` (same Jackson setup as core), not `enableUnsafeDefaultTyping()`

#### DynamoDB module

- Removed `idempotent.dynamodb.use-local`; use `idempotent.dynamodb.endpoint` for local/test DynamoDB
- New: `idempotent.dynamodb.enabled` (default `true`)
- New: `idempotent.dynamodb.ttl-enabled` (default `true`; set `false` if TTL is already configured on your table)
- TTL attribute is `expiresAtEpochSeconds` (epoch seconds)

#### Jackson customizer interfaces

Removed module-specific deprecated interfaces:

- `IdempotentJacksonJsonBuilderCustomizer` (redis/nats)
- `RdsJacksonJsonBuilderCustomizer` (rds)

Use `IdempotentJsonMapperCustomizer` from `idempotent-core`.

#### NATS naming and exception

- Property renamed: `idempotent.nats.enable` -> `idempotent.nats.enabled`
- Exception renamed: `NatsIdempotentExceptions` -> `NatsIdempotentException`

#### RDS module

- Added top-level enable flag: `idempotent.rds.enabled`
- Added cleanup enable flag: `idempotent.rds.cleanup.enabled`

#### Multiple storage modules

- Include only one backend module on the classpath (redis, dynamo, nats, or rds), or provide your own `IdempotentStore` bean explicitly.

### Behavior updates

- `IdempotentPayloadCodecException` now extends `IdempotentException`
- `IdempotentStore.Value.status` now uses enum `IdempotentStore.Status`

### Recommended rollout checklist

1. Update properties according to the module sections above.
2. Add `spring-boot-starter-aop` if you use `@Idempotent`.
3. Run integration tests against your selected backend (Redis/DynamoDB/NATS/RDS).
4. Verify serialization strategy explicitly with `idempotent.serialization.strategy`.
