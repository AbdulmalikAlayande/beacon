# Beacon roadmap

- Purpose: the single place that records what is built, what is next, which code findings are open, and which decisions are still open.
- Document status: Draft, last checked against the code on 2026-11-02

## How to read this file

Every other document describes design and behaviour; only this file records status. A ticked box means the code at the date above does it. Item numbers are the build order. Items 1 to 9 are agreed; items 10 to 16 are a **proposed** order, not final.

Design decisions are in [`docs/adr/`](adr/README.md). Gap (G-xx) and open-question (O-xx) numbers refer to the decision register, [`docs/archive/decision-register.md`](archive/decision-register.md).

## Items 1 to 9

### Item 1. Clock

- [x] `Clock` bean (`Clock.systemUTC()`, replaceable by the host) in `BeaconCoreConfig`
- [x] Fixed test clock in `BeaconCoreTestConfig`
- [x] No `Instant.now()`, `LocalTime.now()` or `LocalDateTime.now()` in `beacon-core/src/main`
- [ ] `deduplication_store.created_at` still comes from the database's `now()` (G-29)

### Item 2. The two tables

- [x] `V1__init_beacon_schema.sql`: `notification_queue` and `notification_status`
- [x] `DeliveryTaskEntity` and `NotificationStatusEntity`, with their repositories
- [x] `UNIQUE (idempotency_key, channel)` and the partial poll index
- [x] Beacon's own Flyway history table in tests (`beacon_flyway_schema_history`)
- [ ] Beacon's own Flyway instance in the starter (item 10)

### Item 3. Template engine

- [x] `DefaultTemplateEngine` with 25 tests
- [ ] Delete `AdvancedTemplateEngine` (G-13)

### Item 4. Three deduplication stores

- [x] `DefaultDeduplicationStore` (in memory)
- [x] `JdbcDeduplicationStore` and `V2__create_deduplication_store.sql`
- [x] `RedisDeduplicationStore`
- [x] Same-key race tests for all three
- [ ] The JDBC store as the default bean, selected by a property (item 10)
- [ ] Retention for `deduplication_store` (O-07)

### Item 5. Ingestion

- [x] `send()`: validate, claim the key, derive the notification ID, publish the event, return `CREATED`
- [x] After-commit listener: resolve the user, resolve channels and priority, apply quiet hours, encrypt the context, write channel tasks and status rows
- [x] `NotificationIngestionTransactionalTest` (19 tests), `QuietHoursPolicyTest` (14), `AesGcmContextEncryptorTest` (8)

The after-commit design is superseded by item 6's request table ([ADR 0029](adr/0029-after-commit-listener-writes-queue-rows.md), [ADR 0010](adr/0010-send-writes-request-row-transactional-outbox.md)). It stays in the code until item 6 replaces it.

### Item 6. Request table and queue poller (in progress)

Decisions: [ADR 0010](adr/0010-send-writes-request-row-transactional-outbox.md), [ADR 0017](adr/0017-delivery-task-is-the-queue-contract.md), [ADR 0018](adr/0018-claiming-the-database-queue.md).

- [x] `DatabaseNotificationQueue.enqueue`
- [ ] Migration for the request table (proposed name `notification_request`)
- [ ] `send()` inserts the request row in the host's transaction; no event, no listener
- [ ] The expand step: claim request rows with `SKIP LOCKED`, then expand each into channel tasks and status rows in one transaction with marking it expanded
- [ ] Claim tasks: `claim(batchSize)` with `SKIP LOCKED`, setting `PROCESSING`
- [ ] Stuck-row recovery: `recoverStuck(timeout)`
- [ ] The queue poller: each tick recovers, expands, then claims, and never lets an exception escape
- [ ] Rework the ingestion tests for the request table (most of `NotificationIngestionTransactionalTest`; rewrite `listenerWaitsForCommit_notForPublish` and `noTransaction_tasksAreQueuedImmediately`)
- [ ] Fill in the stub tests in `DatabaseNotificationQueueTest` (24) and `OutboxPollerTest` (8)
- Open: O-02 (claim order, timeout, batch size, interval, scheduler), O-03 (`enqueueDelayed`), O-19 (retrying a failing expansion), O-20 (unknown users), O-21 (keep or delete expanded request rows)

### Item 7. Providers

Decision: [ADR 0019](adr/0019-providers-classification-rate-limits-circuit-breakers.md).

- [ ] A fake provider, for tests
- [ ] A provider registry returning a channel's providers in fallback order
- [ ] Error classification: 429 and 503 temporary, 400 permanent
- [ ] Vendor providers: Twilio, Termii, SendGrid, Mailgun, Firebase, with WireMock tests

### Item 8. The worker

Decisions: [ADR 0016](adr/0016-simple-template-engine-render-at-worker.md), [ADR 0019](adr/0019-providers-classification-rate-limits-circuit-breakers.md), [ADR 0020](adr/0020-delivery-suppression-outcomes-no-personal-data.md).

- [ ] The delivery handler, called by the queue poller
- [ ] Decrypt the context, look up the user again, render the template
- [ ] Suppression check before sending (`SuppressionList` implementation)
- [ ] Outbound rate limiter (token bucket per provider)
- [ ] Circuit breaker per provider, with fallback; a 429 never trips it
- [ ] On success: delete the queue row, status `SENT` with the provider's name
- [ ] Tests proving no personal data in logs or failure reasons
- Open: O-08 (the delivery-lock methods)

### Item 9. Retries and dead letters

Decision: [ADR 0020](adr/0020-delivery-suppression-outcomes-no-personal-data.md).

- [ ] Temporary failure: `retry_count + 1`, exponential backoff on `available_at`, back to `QUEUED`
- [ ] Permanent failure or retry limit: dead letter (`FAILED`) with a reason free of personal data
- [ ] `enqueueDead`
- Open: O-23 (retry limit and backoff numbers)

## After item 9 (proposed order, not final)

- **Item 10. Starter auto-configuration and wiring** (the first usable version): every bean registered with `@Bean` and conditions ([ADR 0005](adr/0005-beans-registered-with-bean-never-component.md)); the default dedup store and its property; the encryption key property (O-06); Beacon's own Flyway instance ([ADR 0007](adr/0007-flyway-owns-the-schema.md)).
- **Item 11. Webhooks** with delivery status and automatic suppression ([ADR 0021](adr/0021-webhooks-hmac-and-daily-reconciliation.md)).
- **Item 12. `getStatus`.**
- **Item 13. Batch sends** ([ADR 0022](adr/0022-batch-sends.md); O-22).
- **Item 14. Reconciliation** ([ADR 0021](adr/0021-webhooks-hmac-and-daily-reconciliation.md)).
- **Item 15. `beacon-test` fixtures** for host applications.
- **Item 16. First release.**

## Later phases

Monthly partitioning of `notification_status`, monitoring and alerting, and a dead-letter dashboard (register D-111). None is designed yet.

## Code findings

Every open gap from the decision register, as work items. Each line names the gap; the register has the full description and evidence.

### Production code and schema

- [ ] G-01 `beacon-api` depends on Spring (POM, `BeaconApiApplication`, an unused `@Validated` import)
- [ ] G-02 The queue bean is declared twice (`BeaconCoreConfig` and `BeaconQueueConfig`)
- [ ] G-08 `sendBatch`, `getStatus` and `cancelScheduled` are stubs
- [ ] G-09 `NotificationStatusEntity.equals` throws when the ID is null
- [ ] G-10 `JdbcDeduplicationStore` logs a normal duplicate at ERROR with a stack trace
- [ ] G-11 `beacon-core` depends on `spring-boot-starter-webmvc`
- [ ] G-12 Initializr classes, `application.properties` and `spring-boot-maven-plugin` in every module
- [ ] G-13 `AdvancedTemplateEngine` is an empty stub
- [ ] G-15 `deduplication_store` has no cleanup
- [ ] G-16 Stale `//todo` in the listener
- [ ] G-17 A listener failure after commit loses the notification (closed by item 6)
- [ ] G-18 No beans for the service, listener, dedup stores, encryptor, registry or template engine (item 10)
- [ ] G-19 `beacon-api` and the starter use the wrong groupId
- [ ] G-20 `DeliveryTaskEntity` does not implement `Persistable`, so every insert runs a select first
- [ ] G-21 The in-memory dedup store enforces retention only when full
- [ ] G-22 A null `enabledChannels` throws a `NullPointerException` in the listener
- [ ] G-23 `uuidFrom` marks a derived UUID as version 4 and throws a bare `RuntimeException`
- [ ] G-24 Null collection elements fail before validation runs
- [ ] G-25 `notificationId` is a `UUID` in some types and a `String` in others
- [ ] G-26 Status rows are linked to queue rows only by key and channel
- [ ] G-27 The Clock is read several times per task
- [ ] G-28 Explicit channels are not checked against the type's channels (O-17)
- [ ] G-29 `deduplication_store` gets its ID and `created_at` from the database
- [ ] G-30 The `notification_status` indexes on `(status)` do not support time-range queries
- [ ] G-31 No CHECK constraints on enum columns
- [ ] G-32 The encryptor does not wrap Jackson exceptions and binds no additional authenticated data
- [ ] G-33 The `PAYMENT_RECEIPT` Javadoc says "High priority"
- [ ] G-34 "Outbox" names in the V1 comment and test class names
- [ ] G-35 Only `QUEUED` is ever written (O-14)

### Build and packaging

- [ ] G-36 The root POM sets the compiler to source and target 6
- [ ] G-37 Only `beacon-core` inherits the root POM; the starter and `beacon-test` depend on no Beacon module
- [ ] G-38 The root `mvnw` and `mvnw.cmd` contain unfilled placeholders, and the wrapper jar is gitignored
- [ ] G-39 Testcontainers is pinned to 1.21.4 while the comment says 2.x
- [ ] G-40 `beacon-core`'s `application.properties` would land on hosts' classpath
- [ ] G-41 `beacon-core` hard-codes the `beacon-api` version
- [ ] G-51 The licence is not settled (O-18)

### Tests

- [ ] G-03 `DatabaseNotificationQueueTest` never clears `notification_queue`
- [ ] G-42 `BeaconCoreConfigTest` is likely to fail because of G-02
- [ ] G-43 `putIn_null_rejectedClearly` expects a validation error that nothing raises
- [ ] G-44 Tests that pass for the wrong reason
- [ ] G-45 A leftover `main` method that loops forever
- [ ] G-46 Two `await()` calls without a timeout
- [ ] G-47 Weak checks in `NotificationServiceImplementationTest`
- [ ] G-48 Duplicated and copy-pasted tests
- [ ] G-49 `TestEncryptionUtil` uses AES/ECB
- [ ] G-50 No test documents the in-memory and Redis rollback limitation

## Open decisions

| ID | Question | Needed by |
|---|---|---|
| O-02 | Claim order, stuck-row timeout, batch size, poll interval, scheduler | Item 6 |
| O-03 | Remove `enqueueDelayed` (removal agreed) | Item 6 |
| O-06 | How the host supplies and rotates the encryption key (proposal recorded) | Item 10 |
| O-07 | Retention for `deduplication_store` | Item 10 |
| O-08 | Keep or remove the delivery-lock methods | Item 8 |
| O-14 | Whether held tasks show `DELAYED`, and whether `CREATED` is ever written | Item 6 |
| O-15 | Cross-channel fallback | Item 8 |
| O-17 | Limit explicit channels to the type's channels | Item 6 |
| O-18 | Which licence | Before outside contributions |
| O-19 | Retry and backoff for a failing expansion | Item 6 |
| O-20 | What happens to a request whose user is unknown | Item 6 |
| O-21 | Keep or delete expanded request rows | Item 6 |
| O-22 | How batch sends fit the request-row design | Item 13 |
| O-23 | Retry limit and backoff numbers for delivery | Item 9 |
