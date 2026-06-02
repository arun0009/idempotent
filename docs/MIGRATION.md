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
- Table item expiry: single attribute `expiresAtEpochSeconds` (epoch seconds, used for TTL). Removed legacy `expirationTimeInMilliSeconds` from 2.x.

#### RDS storage schema

- Column `expiration_time_millis` renamed to `expires_at` (BIGINT, epoch milliseconds). Recreate or migrate your table before upgrading.

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
- `IdempotentStore.Value.status` uses enum `IdempotentStore.Status` (`IN_PROGRESS`, `COMPLETED`; was `INPROGRESS` in 2.x)
- `IdempotentStore.Value.expiresAt` is an `Instant` (replaces millis-based expiry on the domain model)
- `IdempotentStore` now requires `loadValue(key, returnType)` — a raw persistence read. Expiry is
  enforced centrally by the new default `getValue`, which wraps `loadValue` and best-effort removes
  expired entries on read so a subsequent strict `store` can reuse the key. **Custom stores must
  implement `loadValue` instead of `getValue`** and must not filter on `expiresAt` themselves. The
  expired-read and the delete are not atomic in distributed backends; a fresh concurrent insert
  could be removed (rare and recoverable — the next caller succeeds with its own strict insert).
- Expiry is evaluated against wall-clock time (`expiresAt`). Keep nodes clock-synchronized (NTP)
  and size TTLs above your slowest operation plus expected skew, as in Stripe and AWS Powertools.
- `IdempotentStore.update` is now **no-op when the key is missing** in every backend. It never
  resurrects a deleted or expired entry. Use `store` to insert.
- On `IdempotentKeyConflictException`, the library always follows the existing-entry path; it
  does not start a new operation when the conflicting entry cannot be read.
- `IdempotentService.execute` now **rethrows the original exception** thrown by your operation
  instead of wrapping it in `IdempotentException`. If you previously caught `IdempotentException`
  to handle domain failures, catch your domain exception (or `RuntimeException`) instead. The
  in-progress entry is still cleaned up before the throw.
- **Null and void responses are now cached** as `COMPLETED`. Previously the in-progress entry
  was removed when the operation returned `null` (or a `void` method completed), which meant
  duplicate calls re-executed the operation. From 3.0, a successful operation that returns
  `null` (or returns nothing for `void`) is cached and subsequent calls short-circuit. Non-2xx
  `ResponseEntity` results are still removed so the caller can retry.
- The `IdempotentAspect` bean is now only registered when Spring AOP is on the classpath
  (`spring-boot-starter-aop`). Without it, the bean previously existed but never intercepted
  anything — a silent footgun. `IdempotentService` is still registered either way.
- `IdempotentService` adds typed `execute(key, returnType, operation, ttl)` overloads. Prefer
  them when the response type is known: they let `RDS` and `DynamoDB` deserialize directly into
  the target type without relying on Jackson polymorphic `@class` metadata.
- `IdempotentAspect` now delegates the idempotency state machine to `IdempotentService`. If you
  previously constructed `IdempotentAspect(idempotentStore, properties)` directly, switch to
  `IdempotentAspect(idempotentService, properties)`.
- `@Idempotent(duration = ...)` now accepts both ISO-8601 (`PT5M`) and Spring's short form
  (`5m`, `100ms`, `30s`). Previous releases required strict ISO-8601.
- `@Idempotent` logs a warning (once per method) when the resolved key is empty so accidental
  misconfiguration is visible instead of silently skipping idempotency.

### Dynamo AWS properties

- `idempotent.aws.region`, `access-key`, and `access-secret` are optional (`null` when unset); omit them to use the default AWS credential chain
- `idempotent.dynamodb.endpoint` is optional (`null` when unset) instead of defaulting to an empty string

### Duration-based configuration

- `idempotent.inprogress.retry.initial.interval-millis` (integer ms) → `idempotent.inprogress.retry.initial.interval` (`Duration`, e.g. `100ms`, `PT0.1S`)
- `idempotent.rds.cleanup.fixed-delay` (integer ms) → `Duration` (default `PT1M`, e.g. `60s`)

### Recommended rollout checklist

1. Update properties according to the module sections above.
2. Add `spring-boot-starter-aop` if you use `@Idempotent`.
3. Run integration tests against your selected backend (Redis/DynamoDB/NATS/RDS).
4. Verify serialization strategy explicitly with `idempotent.serialization.strategy`.
