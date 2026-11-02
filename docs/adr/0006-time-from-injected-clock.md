# 0006. Time comes from an injected `Clock`, stored as `Instant` and `timestamptz`

- Status: Accepted
- Date: 2026-11-02
- Register entries: D-020, D-021
- Document status: Approved, last checked against the code on 2026-11-02

## Context

Much of Beacon's behaviour depends on the current time: quiet hours, scheduled sends, retry backoff and stuck-row recovery. Tests must be able to run that behaviour at a chosen moment. Timestamps also have to survive a round trip through PostgreSQL unchanged, or tests that compare stored values fail for the wrong reason.

## Decision

- Nothing in `beacon-core` calls `Instant.now()`, `LocalTime.now()` or the database's `now()` for business logic. Time is read from an injected `java.time.Clock`.
- `BeaconCoreConfig` provides `Clock.systemUTC()` with `@ConditionalOnMissingBean`, so a host or a test can supply its own.
- Tests use a fixed clock: `2026-05-31T03:00:00Z`, zone `Africa/Lagos`.
- Java code uses `Instant`; PostgreSQL columns use `timestamptz`.
- When a time is passed to SQL (for example the claim query's `:now`), it comes from the Clock, never from the database.

## Consequences

- Every class that needs the time takes a `Clock` in its constructor.
- Time-dependent behaviour can be tested at exact boundaries (for example, the first and last instant of a quiet-hours window).
- PostgreSQL stores microseconds and rounds anything finer. Test data must therefore come from the fixed clock, which has no nanoseconds, not from `Instant.now()`. Truncating values in assertions does not work, because PostgreSQL rounds rather than truncates.

## Alternatives rejected

- **`LocalDateTime`.** Rejected because it has no timezone, so it does not name a single moment.
- **`OffsetDateTime`.** Rejected because PostgreSQL's `timestamptz` stores the moment but not the offset. The offset is lost on the round trip, and equality checks break. `Instant` is exactly what `timestamptz` stores.

## In the code

- `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/config/BeaconCoreConfig.java` (`clock()`)
- `beacon-core/src/test/java/io/github/abdulmalikalayande/beacon/core/config/BeaconCoreTestConfig.java` (the fixed clock)
- Clock use: `NotificationServiceImplementation`, `NotificationRequestedEventListener`, `DatabaseNotificationQueue`, `NotificationStatusEntity.fromTask`, `DefaultDeduplicationStore`, `RedisDeduplicationStore` (all under `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/`).
- Columns: `beacon-core/src/main/resources/db/migration/beacon/V1__init_beacon_schema.sql`.
- Not yet true: `deduplication_store.created_at` is filled by the database's `now()`, because `JdbcDeduplicationStore` inserts only the key (register G-29).
