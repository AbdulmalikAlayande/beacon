# Beacon Decision Register

**Purpose.** This file collects every design decision made for Beacon so far, with the reason behind it, the alternatives that were rejected, and whether it is in the code yet. Most of these decisions were made in design conversations that are not in the repository, so this file is the only written record of the reasoning. It is the raw material for the ADRs, the architecture overview, the technical spec and `CLAUDE.md`.

**Where it lives.** `docs/archive/decision-register.md`. It is source material, not user-facing documentation.

**Checked against.** The codebase snapshot of 2026-11-01, then rechecked on 2026-11-02 (branch `docs/rebuild`, HEAD `4ba9005`) during the documentation rebuild, with Abdulmalik's answers to the Phase 1 questions applied. Where the code and a decision disagree, the entry says so.

**How to read the status line.**

- **Accepted, in code**: decided and implemented.
- **Accepted, not yet in code**: decided, implementation pending.
- **Accepted, violated**: decided, but the current code breaks it. Listed again under Known Gaps.
- **Planned**: the agreed design for a part that is not built yet (items 7 to 9 and later).
- **Open**: not decided yet.
- **Superseded by D-xxx**: replaced by a later decision. Kept so the ADRs can record the history.
- **[CONFIRM]**: recommended by Claude, not yet confirmed by Abdulmalik. Review these first.

---

## 1. Product and scope

### D-001 Beacon is an embedded Spring Boot library, not a service
- **Status:** Accepted, partly in code (the starter module is still an empty scaffold).
- **Decision:** Beacon ships as a Spring Boot starter that runs inside the host application. The host adds a dependency and calls `NotificationService.send()`.
- **Why:** Teams should get reliable notifications without running and operating another service. The first design read like a microservice spec that needed Kafka and Redis, which would put off most adopters.
- **Rejected:** A standalone notification microservice with its own deployment, Kafka and Redis.

### D-002 Three channels: PUSH, SMS, EMAIL
- **Status:** Accepted, in code (`NotificationChannel`).

### D-003 The host owns user data; Beacon never reads host tables
- **Status:** Accepted, in code.
- **Decision:** All user data (contact details, enabled channels, timezone, quiet hours) reaches Beacon through the `UserPreferenceResolver` SPI, which the host implements. Beacon never queries the host's user tables.
- **Why:** A library must not assume anything about the host's schema. The SPI is the only integration boundary.

### D-004 No required infrastructure beyond the host's PostgreSQL
- **Status:** Accepted, in code for the queue and dedup tables.
- **Decision:** The defaults (queue, dedup, locking) run on the host's own PostgreSQL. Redis, Kafka, RabbitMQ and Pulsar are optional, pluggable alternatives.
- **Rejected:** Requiring Redis or a message broker out of the box.

### D-005 Platform requirements
- **Status:** Accepted, in code (the README still states the old range, see G-14).
- **Decision:** Java 21, PostgreSQL 14+ (needed for `FOR UPDATE SKIP LOCKED` in the default queue), Spring Boot 4.0.x.
- **Why 4.0.x:** The build uses Spring Boot 4.0.6, and the code depends on Jackson 3 (`tools.jackson`, used by `AesGcmContextEncryptor`) and Hibernate 7, so Spring Boot 3.x cannot work. Decided 2026-11-02 (was O-09).
- **Conflict:** The README says "Spring Boot 3.2+" (G-14).

---

## 2. Modules and boundaries

### D-010 Four modules
- **Status:** Accepted, in code (starter and test modules are scaffolds).
- **Decision:**
  - `beacon-api`: every public contract (enums, DTOs, requests, responses, exceptions, interfaces).
  - `beacon-core`: the implementations.
  - `beacon-spring-boot-starter`: auto-configuration, properties, webhook security wiring.
  - `beacon-test`: test fixtures for host applications (not where Beacon's own tests live).
- **Dependency direction:** `core` depends on `api`; `starter` depends on `core`; `beacon-test` depends on `api` and `core`. Nothing depends on `starter` except host applications.
- **Current state of the POMs:** only `core → api` exists. The starter does not depend on `core`, and `beacon-test` depends on no Beacon module (G-37).

### D-011 `beacon-api` has no Spring dependency
- **Status:** Accepted, **violated** (see Known Gaps G-01).
- **Decision:** `beacon-api` is plain Java 21. Its only allowed dependency is `jakarta.validation-api` (the specification jar, not an implementation).
- **Why:** Contracts must be usable without dragging Spring into anything that only needs the types.
- **Current violations:** `beacon-api/pom.xml` pulls `spring-boot-starter` and `hibernate-validator`; `BeaconApiApplication` is a `@SpringBootApplication` class; `NotificationQueue` imports Spring's `@Validated` (line 5) but never applies it.

### D-012 The API declares validation; the core runs it
- **Status:** Accepted, in code.
- **Decision:** Request records carry `jakarta.validation` annotations (`@NotBlank`, `@NotNull`, `@Valid`, `List<@NotNull X>`). `beacon-core` runs a real `Validator` (Hibernate Validator) inside `send()` and throws `ConstraintViolationException`.

### D-013 Package layout in `beacon-api`
- **Status:** Accepted, in code.
- **Decision:** `enums/`, `dto/`, `request/`, `response/`, `exception/`, `service/`, `spi/`, `port/`.
  - `service/`: what the host calls (`NotificationService`).
  - `spi/`: what the **host** implements: `UserPreferenceResolver`, `UserBatchSource`, `NotificationTypeRegistry`, `TemplateResolver`.
  - `port/`: what **Beacon** implements, swappable: `NotificationProvider`, `NotificationQueue`, `DeduplicationStore`, `SuppressionList`, `NotificationStatusTracker`, `TemplateEngine`, `ProviderRateLimiter`, `ProviderCircuitBreaker`, `WebhookHandler`, `ReconciliationJob`.
- **Why:** The folder name tells you who owns the implementation.
- **Rejected:** One flat `spi/` folder for everything (the earlier layout).

### D-014 Requests and DTOs are records with defensive copies
- **Status:** Accepted, in code. Collections are copied in the canonical constructor (`List.copyOf`, `Map.copyOf`).
- **Why:** Records are only shallowly immutable. Without copies, a host that changes its list or map after calling `send()` would change what Beacon queues. The copied collections are also safe to hand from the host's thread to the listener.

### D-015 Webhook security configuration lives in the starter
- **Status:** Planned.
- **Decision:** `NotificationWebhookSecurityConfigurer` belongs in `beacon-spring-boot-starter`, never in `beacon-api`, because it depends on Spring Security.

### D-016 Beans are registered with `@Bean`, never `@Component`
- **Status:** Accepted, partly in code.
- **Decision:** Implementation classes stay plain (no stereotype annotations). They become beans through `@Bean` methods in configuration classes, guarded by `@ConditionalOnMissingBean` and, where several implementations exist, `@ConditionalOnProperty` (for example `beacon.queue.type`). Their final home is the starter's auto-configuration.
- **Why:** Component scanning only covers the host's own packages, so `@Component` classes in `io.github.abdulmalikalayande.beacon` would silently never load. `@Component` also cannot be switched off, so several `NotificationQueue` implementations would collide.
- **Related rule:** Beans that need the database go in their own configuration class, never in one that database-free tests load (see D-124).
- **Current state:** only the `Clock` bean and the `DatabaseNotificationQueue` bean are registered. Nothing in `src/main` registers the service, the listener, a dedup store, the encryptor, the registry or a template engine, so Beacon cannot yet run inside a host (G-18).

### D-017 Coordinates
- **Status:** Accepted, **violated** (see G-19).
- **Decision:** GroupId `io.github.abdulmalikalayande` for every module, base package `io.github.abdulmalikalayande.beacon`. Confirmed 2026-11-02.
- **Current violation:** `beacon-api` and `beacon-spring-boot-starter` use groupId `io.github.abdulmalikalayande.beacon`. The base package is correct everywhere.

### D-018 Library modules are plain jars
- **Status:** Accepted, not yet in code (G-12). Decided 2026-11-02 (was O-10).
- **Decision:** `BeaconCoreApplication` moves to `beacon-core/src/test`, where the test slices use it as their configuration root. The other three Initializr `@SpringBootApplication` classes are deleted together with their `application.properties`. `spring-boot-maven-plugin` is removed from the library modules.
- **Why:** The plugin repackages each jar as an executable application, which breaks it as a dependency. Initializr classes and `application.properties` do not belong in a library, and core's `application.properties` would otherwise land on every host's classpath.

### D-019 The web dependency belongs to the starter
- **Status:** Accepted, not yet in code (G-11). Decided 2026-11-02 (was O-11).
- **Decision:** `spring-boot-starter-webmvc` is removed from `beacon-core`. It is added to `beacon-spring-boot-starter` when webhooks are built.
- **Why:** `beacon-core` has no web code, and the dependency drags a web stack into every host.

---

## 3. Time

### D-020 Time is always read from an injected `Clock`
- **Status:** Accepted, in code.
- **Decision:** Nothing in `beacon-core` calls `Instant.now()`, `LocalTime.now()` or the database's `now()` for business logic. `BeaconCoreConfig` provides `Clock.systemUTC()` with `@ConditionalOnMissingBean`. Tests use a fixed clock: `2026-05-31T03:00:00Z`, zone `Africa/Lagos`.
- **Why:** Quiet hours, scheduling, retry backoff and stuck-row recovery must be testable at chosen times.

### D-021 `Instant` in Java, `timestamptz` in PostgreSQL
- **Status:** Accepted, in code.
- **Rejected:** `LocalDateTime`, because it has no timezone and so does not name a single moment. `OffsetDateTime`, because `timestamptz` stores the moment but not the offset, so the offset is lost on the round trip and equality checks break. `Instant` is exactly what `timestamptz` stores.
- **Lesson:** PostgreSQL stores microseconds and rounds anything finer. Test data must come from the fixed clock (which has no nanoseconds), not from `Instant.now()`. Truncating in assertions does not work, because PostgreSQL rounds rather than truncates.

---

## 4. Persistence

### D-030 Flyway owns the schema
- **Status:** Accepted, in code.
- **Decision:** `spring.jpa.hibernate.ddl-auto=validate`. Migrations live in `beacon-core/src/main/resources/db/migration/beacon/`. Schema changes are made by writing a migration, never through entity annotations alone. A migration is never edited once merged; changes go in a new migration.
- **Why:** Hibernate generating or updating the schema would change a host's database in ways nobody reviewed, and a library must never do that; migrations are versioned and reviewable. An edited migration is rejected by Flyway's checksum once it has run, and hosts' databases have already run it.
- **Rejected:** Hibernate generating or updating the schema; editing a merged migration.

### D-031 Beacon's Flyway history is isolated from the host's
- **Status:** Accepted, partly in code (the short-term form). `beacon-core/src/test/resources/application-test.properties` sets `spring.flyway.table=beacon_flyway_schema_history`, confirmed by a test run on 2026-11-02. The final form is not built.
- **Decision:** Beacon records its migrations in `beacon_flyway_schema_history`, not the host's `flyway_schema_history`. Short term: `spring.flyway.table` in test properties. Final: the starter runs its own Flyway instance, so the host's Flyway is never touched.
- **Evidence:** Earlier test logs showed the default `public.flyway_schema_history` being used.

### D-032 Two tables: a work queue and an audit trail
- **Status:** Accepted, in code (`V1__init_beacon_schema.sql`).
- **Decision:**
  - `notification_queue` (entity `DeliveryTaskEntity`): the work table. One row per channel task. Rows are deleted when the task reaches a final state, so the table stays small and fast to poll.
  - `notification_status` (entity `NotificationStatusEntity`): the permanent, append-mostly history of every notification per channel.
- **Rejected:** Using `notification_status` itself as the queue (the old spec's section 5.6).
- **Naming:** "queue" rather than "outbox" was chosen for clarity, and `DeliveryTaskEntity` mirrors the `DeliveryTask` DTO.

### D-033 Uniqueness on the status table is per channel
- **Status:** Accepted, in code.
- **Decision:** `UNIQUE (idempotency_key, channel)` on `notification_status`.
- **Why:** One request fans out to several channels that share an idempotency key. The same key on a different channel is legitimate; the same key on the same channel is a duplicate.
- **Rejected:** `UNIQUE (idempotency_key)`, which crashes the moment a user has two channels enabled.

### D-034 Partial index for polling
- **Status:** Accepted, in code. `idx_notification_queue_poll ON notification_queue (available_at) WHERE status = 'QUEUED'`.
- **Open:** If D-099 decides HIGH is claimed before LOW, the index may need `priority` too.

### D-035 Enums are stored as strings
- **Status:** Accepted, in code. Every `@Enumerated` uses `EnumType.STRING`, never `ORDINAL`.
- **Why:** Reordering or inserting an enum constant would silently change the meaning of every row stored as an ordinal. Strings stay correct and are readable in SQL.

### D-036 Entity equality uses the ID only
- **Status:** Accepted, in code. `equals` and `hashCode` compare `id` and nothing else. Tests verify data with field-by-field assertions, never with `equals`.

### D-037 Entities hold data only
- **Status:** Accepted, in code.
- **Decision:** No `@PrePersist` or `@PreUpdate` callbacks setting timestamps. Whoever creates or changes a row sets its times from the injected `Clock`.
- **Why:** Lifecycle callbacks cannot reach the `Clock` bean, and they silently overwrote test data.

### D-038 IDs are UUIDs created in code
- **Status:** Accepted, in code for the queue; the status table still lets Hibernate generate its row ID.
- **Decision:** The task ID is created with `UUID.randomUUID()` when the `DeliveryTask` is built (in the listener). `DeliveryTaskEntity` stores it with a plain `@Id`, no `@GeneratedValue`. IDs are `UUID` everywhere (DTO, entities, event, columns).
- **Why:** A task needs its identity before it enters any queue, including brokers that have no database to generate one.
- **Correction (2026-11-02):** An earlier version of this entry also claimed that assigning the ID in code avoids an extra select before every insert. That claim is dropped: `DeliveryTaskEntity` does not implement `Persistable`, so Spring Data's `save()` still calls `merge()` and runs a select first (G-20).
- **Rejected:** `String` IDs (they mismatched the `UUID` columns and broke `ddl-auto=validate`); database-generated task IDs.

### D-039 A separate deduplication table
- **Status:** Accepted, in code (`V2__create_deduplication_store.sql`, table `deduplication_store`, `UNIQUE (idempotency_key)`).
- **Why:** Deduplication happens in `send()`, before channels are known, so the per-channel constraint on `notification_status` cannot do this job.
- **Open:** Nothing ever deletes old rows from `deduplication_store`, so it grows forever. A retention rule is needed (see O-07). The `created_at` index already exists to support a cleanup job.
- **Note:** The table's `id` is generated by the database and its `created_at` comes from the database's `now()`, and its `notification_id` and `metadata` columns are never written (G-29).

---

## 5. Ingestion (item 5)

### D-050 `send()` publishes an event; the real write happens after the host commits
- **Status:** Accepted, in code.
- **Decision:** `send()` publishes `NotificationRequestedEvent`. `NotificationRequestedEventListener` handles it with `@TransactionalEventListener(phase = AFTER_COMMIT)` and `@Transactional(propagation = REQUIRES_NEW)`.
- **Why:** If the host's transaction rolls back (a payment fails), no notification may exist for it. Writing directly in `send()` would leave a receipt for a payment that never happened.
- **Why `REQUIRES_NEW`:** In the after-commit phase the host's finished transaction is still attached to the thread. A plain `@Transactional` would join it and its writes would never be saved.
- **Limit of the guarantee:** Because the queue rows are written after the host commits, a failure in the listener loses the notification while its idempotency key stays claimed (G-17). Until O-13 is decided, the documents state that "no missed sends" starts once the queue rows are committed.

### D-051 `send()` also works outside a transaction
- **Status:** Accepted, in code (`fallbackExecution = true`).
- **Why:** Without it, `@TransactionalEventListener` silently drops the event when no transaction is active, and the host gets a success response for a notification that never exists. The `NotificationService` Javadoc promises immediate dispatch in this case.

### D-052 The event is a plain record with its own fields
- **Status:** Accepted, in code (`core/event/NotificationRequestedEvent`).
- **Decision:** A `record` in `beacon-core`, not extending `ApplicationEvent`, carrying its own copy of the fields (notificationId, idempotencyKey, userId, scheduledAt, type, priority, preferredChannels, context), built with `NotificationRequestedEvent.from(notificationId, request)`.
- **Why:** Modern Spring publishes any object. The event is internal, so it stays out of `beacon-api`. Its own fields let the internal contract change without touching the public request.
- **Rejected:** Extending `ApplicationEvent`; an empty `BeaconEvent` base class (records cannot extend classes, and the base held nothing); wrapping the raw `NotificationRequest`; the name `NotificationAcceptedEvent` (considered, not adopted).

### D-053 The order of work in `send()`
- **Status:** Accepted, in code.
- **Decision:** Validate, then claim the idempotency key (`markSeen`), then derive the notification ID, then publish the event, then return `NotificationResponse` with status `CREATED`.
- **Why:** No work happens for an invalid or duplicate request. The response is an acknowledgement ("accepted"), not "queued", because the rows are only written after commit.

### D-054 The notification ID is derived from the idempotency key
- **Status:** Accepted, in code (`uuidFrom(idempotencyKey)`: SHA-256 of the key, shaped into a version-4-style UUID).
- **Why:** `send()` must return the ID immediately, before any rows exist, so the database cannot create it. All channel rows of one notification share it. The same key always gives the same ID, which is what idempotency means.
- **Rejected:** A database-generated ID; hashing the key together with a timestamp (the ID changed on every call).

### D-055 A duplicate key throws
- **Status:** Accepted, in code. A repeated idempotency key throws `DuplicateNotificationException`.
- **Rejected:** Silently returning the original response.

### D-056 Channel resolution
- **Status:** Accepted, in code.
- **Decision:** If the host gave `preferredChannels`, start from those; otherwise start from the type's supported channels in `NotificationTypeRegistry`. Keep only channels the user has enabled. No overlap means log a warning and queue nothing, without throwing.
- **Note:** Explicit channels take precedence over the registry (for example, SMS for `ORDER_CONFIRMATION` when the host asks for it).
- **Consequence not yet reviewed:** Explicit channels are not checked against the type's supported channels at all, so an OTP can go out by EMAIL if the host asks for it (G-28). Whether to limit them is open (O-17).

### D-057 An unknown user is skipped quietly
- **Status:** Accepted, in code. If `UserPreferenceResolver.resolve()` returns `null`, the listener logs a warning and queues nothing.

### D-058 The listener writes the status record, not the queue
- **Status:** Accepted, in code.
- **Decision:** For each task, the listener calls `notificationQueue.enqueue(task)` and then saves a `QUEUED` row to `notification_status`.
- **Why:** The status table is the audit trail, not part of the queue. If a Kafka queue replaced the database queue, a status write inside `enqueue` would vanish with it.

### D-059 A missing priority comes from the type registry
- **Status:** Accepted, in code (`NotificationRequestedEventListener.resolvePriority`, tested by `missingPriority_isResolvedFromTypeRegistry`). Confirmed 2026-11-02.
- **Decision:** `priority = request.priority() ?? registry.getPriority(type)`, resolved in the listener.
- **Why:** The `priority` column is `NOT NULL`. Before this fix, a request without priority failed after the host had committed, and the notification was lost.

### D-060 When a task becomes available
- **Status:** Accepted, in code (listener `createDeliveryTask`).
- **Decision:** Start from `scheduledAt` if the host gave one, otherwise the Clock's now. Then apply quiet hours (D-061). The result is the task's `availableAt`.

### D-061 Quiet hours
- **Status:** Accepted, in code (`core/listener/QuietHoursPolicy`, tested by `QuietHoursPolicyTest` and `NotificationIngestionTransactionalTest`). The listener creates the policy itself with `new` rather than receiving it as a dependency.
- **Decision:** Implemented as a separate `QuietHoursPolicy` class. Only LOW priority is held. The window is read in the user's timezone, includes its start and excludes its end, and may wrap past midnight. A window whose start equals its end means no quiet hours. A missing or unknown timezone is treated as UTC rather than failing the send. A task inside the window becomes available when the window ends.

### D-062 What each priority means
- **Status:** Accepted, in code (`QuietHoursPolicy`, test `medium_insideWindow_unchanged`). MEDIUM confirmed 2026-11-02 (was O-01).
- **Decision:** HIGH ignores quiet hours. LOW waits for quiet hours to end. MEDIUM ignores quiet hours, because payment receipts and order confirmations are expected the moment the user acts, and differs from HIGH only in claim order (D-099, still open).
- **Note:** `NotificationPriority.MEDIUM` is now documented (commit `17ed082`). The `NotificationType.PAYMENT_RECEIPT` Javadoc still says "High priority", which contradicts the registry's MEDIUM (G-33).

### D-063 The default type registry
- **Status:** Accepted, in code (`DefaultNotificationTypeRegistry`).

| Type | Priority | Supported channels |
|---|---|---|
| OTP | HIGH | SMS, PUSH |
| SECURITY_ALERT | HIGH | PUSH, EMAIL, SMS |
| SYSTEM_ALERT | HIGH | PUSH, EMAIL, SMS |
| PAYMENT_FAILED | HIGH | PUSH, EMAIL, SMS |
| PAYMENT_RECEIPT | MEDIUM | EMAIL, PUSH |
| ORDER_CONFIRMATION | MEDIUM | EMAIL, PUSH |
| PROMO | LOW | PUSH, EMAIL |
| ENGAGEMENT | LOW | PUSH, EMAIL |
| any other (e.g. CUSTOM) | MEDIUM | EMAIL |

- **Why:** SMS is left out of promotional types to avoid telecom costs; email is the paper trail for financial records.

### D-064 The template context is encrypted while queued
- **Status:** Accepted, in code for encryption (`core/crypto/ContextEncryptor`, `core/crypto/AesGcmContextEncryptor`, called by the listener). Decryption by the worker is planned with the worker. No bean or property supplies the key yet.
- **Decision:** A `ContextEncryptor` interface with an `AesGcmContextEncryptor`: AES-256-GCM, a fresh random 12-byte IV per call, output `Base64(IV + ciphertext)`, context serialised as JSON. Encrypted once per event in the listener; decrypted by the worker just before rendering. A null context encrypts to null.
- **Why:** Contexts carry OTPs and personal data; they must never sit in plain text in the queue or a broker. GCM also detects tampering.
- **Open:** How the host supplies and rotates the key (see O-06, which records the current proposal).
- **Known issues:** Jackson exceptions are not converted to `NotificationException`, and no additional authenticated data ties a ciphertext to its row (G-32).

---

## 6. Deduplication

### D-070 Claiming a key is a single atomic step
- **Status:** Accepted, in code.
- **Decision:** `markSeen` both checks and claims in one atomic operation and throws `DuplicateNotificationException` if the key was already claimed. Never "read first, then write", which lets two simultaneous calls both pass. `isSeen` is a read-only query for status checks and is never used to make the dedup decision.
- **Superseded:** `isDuplicate` (it did the same as `isSeen` and has been removed from the interface).

### D-071 Three dedup stores
- **Status:** Accepted, in code.
- **In-memory (`DefaultDeduplicationStore`):** `ConcurrentHashMap.putIfAbsent`. Bounded at 100,000 keys with a one-hour retention window; when full, expired keys are removed first, then the oldest. Best-effort only, lost on restart. Decided not to polish its eviction further.
  - **Retention note (2026-11-02):** The one-hour window is enforced only when the map is full (`DefaultDeduplicationStore.java:62`), and `isSeen` ignores it (line 111). Below capacity, a key older than one hour is still rejected as a duplicate (G-21).
- **JDBC (`JdbcDeduplicationStore`):** inserts into `deduplication_store`; the unique constraint is the check. `DuplicateKeyException` becomes `DuplicateNotificationException`; other database errors become `NotificationException`.
- **Redis (`RedisDeduplicationStore`):** `SET key value NX` with a TTL (default one hour) under the namespace `beacon:dedup:`. Redis expires keys itself, so no cleanup code is needed.
- **Default (decided 2026-11-02, was O-04):** JDBC is the default store, and a property selects another. **Planned:** the wiring does not exist yet.

### D-072 What happens to a claimed key when the host rolls back
- **Status:** Accepted (documented limitation). Decided 2026-11-02 (was O-05).
- **Facts:** The JDBC store's insert joins the host's transaction, so the claim rolls back with it and the host can retry the same key (tested by `rolledBackSend_releasesDedupClaim_retrySucceeds`). The in-memory and Redis stores are outside the transaction, so after a rollback the key stays claimed and a retry is rejected as a duplicate.
- **Decision:** This is documented as a limitation of the in-memory and Redis stores. No compensation is built. No test yet documents the behaviour of those two stores (G-50).

### D-073 The delivery lock on `DeduplicationStore`
- **Status:** Open.
- **Facts:** `acquireDeliveryLock` and `releaseDeliveryLock` are stubs in all three stores. The old spec had workers take a Redis `SETNX` lock per notification. With the database queue, `FOR UPDATE SKIP LOCKED` plus the `PROCESSING` status already guarantee that only one worker holds a task.
- **Open:** Remove the lock methods from the port, or keep them for broker-based queues. Decide during item 8.

---

## 7. Templates

### D-080 Beacon's own simple template engine
- **Status:** Accepted, in code (`DefaultTemplateEngine`).
- **Decision:** A single regex pass over `${key}` placeholders using `appendReplacement` and `appendTail`.
  - Keys are flat strings (dots and dashes are part of the key, with no property navigation).
  - Keys are trimmed, so `${ name }` resolves `name`.
  - A missing key and a null value are treated the same: the placeholder is left as written and a warning is logged. Rendering never throws for this, so a missing optional field cannot break an OTP.
  - Values are escaped with `quoteReplacement`, so `$` and `\` in values are literal, and values are never expanded again.
  - Subject (email), title (push) and body are rendered separately. The destination is chosen by channel: phone for SMS, email for EMAIL, push token for PUSH.
- **Rejected:** Thymeleaf (too heavy for a library); a lexer, parser and syntax-tree engine with loops and conditions (more than notifications need).
- **`AdvancedTemplateEngine`:** delete it (decided 2026-11-02, was O-12). It is still in the code (G-13).
- **Open:** A null channel currently throws a raw `NullPointerException` (considered changing it to `NotificationException`).

### D-081 Rendering happens at the worker, not at enqueue
- **Status:** Accepted (worker planned).
- **Why:** Rendered HTML bodies multiplied across a large batch would bloat the queue. Only the encrypted context is queued.

---

## 8. Queue (item 6)

### D-090 `DeliveryTask` and `DeliveryTaskEntity` are separate, converted in one place
- **Status:** Accepted, in code.
- **Decision:** `DeliveryTask` (record in `beacon-api/dto`) is what the library passes around and what every queue implementation accepts. `DeliveryTaskEntity` (`beacon-core/entity`) is a row of `notification_queue`. Only `DatabaseNotificationQueue` converts between them. The listener builds `DeliveryTask`s and never touches the entity or its repository.
- **Why:** A Kafka message has no table row, so the shared contract cannot be a JPA entity.

### D-091 What `DeliveryTask` carries
- **Status:** Accepted, in code.
- **Decision:** `UUID taskId`, `UUID notificationId`, idempotency key, user ID, channel, type, priority, encrypted context, retry count, `createdAt`, `availableAt`.
- **Not on the DTO:** `status` and `updated_at`, which are bookkeeping for the database queue only.
- **Rejected:** `scheduledAt` on the task. The host's schedule is a wish set once; `availableAt` is when the task may be picked up, which quiet hours and retries also move. The queue only needs the latter.

### D-092 `enqueue` returns nothing and throws on failure
- **Status:** Accepted, in code.
- **Why:** The caller already has the task and its ID. A thrown exception rolls back the listener's transaction; a boolean return could be ignored.

### D-093 Claiming belongs to the database queue, not the port
- **Status:** Accepted, not yet in code.
- **Decision:** `claim(batchSize)` and `recoverStuck(timeout)` are methods of `DatabaseNotificationQueue`, not of `NotificationQueue`. What every queue shares on the way out is a small handler interface ("here is a task to deliver"), which the poller calls and the worker implements.
- **Why:** Claiming is a database idea. Brokers push messages to consumers; they do not let you claim them.

### D-094 A claim is a short transaction
- **Status:** Accepted, not yet in code.
- **Decision:** In one short transaction: select up to N ready rows (`status = 'QUEUED' AND available_at <= :now`) with `FOR UPDATE SKIP LOCKED`, set them to `PROCESSING` with `updated_at` from the Clock, commit, then hand them to the handler. `:now` comes from the Clock, never the database's `now()`.
- **Why:** `SKIP LOCKED` lets several servers poll at once without waiting for each other or claiming the same row. Marking rows `PROCESSING` keeps them claimed after the lock is released, so no transaction stays open during a slow provider call.
- **Rejected:** Holding the row lock until delivery finishes.

### D-095 Stuck tasks are recovered
- **Status:** Accepted, not yet in code.
- **Decision:** Rows that have been `PROCESSING` for longer than a timeout (judged by `updated_at` and the Clock) go back to `QUEUED` with `retry_count + 1`.
- **Why:** A server that dies after claiming would otherwise leave its tasks stuck forever. Counting the recovery lets the retry limit (item 9) stop a message that crashes the server every time.

### D-096 The poller never dies
- **Status:** Accepted, not yet in code.
- **Decision:** `OutboxPoller.tick()` recovers stuck rows, claims a batch and hands each task to the handler. One task failing does not stop the others, and no exception escapes `tick()`. The poller can be stopped, and stops claiming once stopped.
- **Why:** In Java, an exception that escapes a scheduled task cancels that schedule silently and permanently.
- **Naming (2026-11-02):** Documents call this component the "queue poller" in prose. The class name is decided when it is built; `OutboxPoller` is only the name used by the test scaffold. The "outbox" wording in V1's comment and in test class names contradicts D-032's naming (G-34).

### D-097 Where the queue bean is registered
- **Status:** Accepted, **violated** (see G-02).
- **Decision:** The `DatabaseNotificationQueue` bean lives in its own configuration class in `src/main` (`BeaconQueueConfig`), with `@ConditionalOnMissingBean(NotificationQueue.class)`, not in `BeaconCoreConfig`.
- **Why:** `BeaconCoreConfigTest` loads `BeaconCoreConfig` without a database; a bean needing `DeliveryTaskRepository` there breaks it.
- **Current violation (2026-11-02):** `BeaconQueueConfig` is now in `src/main`, but `BeaconCoreConfig` (lines 29-34) still declares the same bean. There are two definitions named `databaseNotificationQueue`, and only the one in `BeaconCoreConfig` checks `beacon.queue.type`.

### D-098 `enqueueDelayed`
- **Status:** Open. Removal agreed on 2026-11-02 (it stays Open until Abdulmalik removes it), since `availableAt` already expresses a delay and two ways to do one thing tend to drift apart.

### D-099 Item 6 parameters still to decide
- **Status:** Open. They will be decided during item 6. The test scaffolds suggest oldest first (`claim_oldestAvailableFirst`) and a stoppable poller, but nothing is decided.
  - Whether HIGH is claimed before MEDIUM before LOW, or strictly oldest first. Without priority ordering, an OTP can wait behind thousands of promotional rows that became ready earlier.
  - The stuck-row timeout, and whether a row at exactly the timeout counts as stuck.
  - Default batch size and poll interval.
  - The scheduling mechanism (a `ScheduledExecutorService` owned by the poller, or a Spring `TaskScheduler`).

---

## 9. Delivery (items 7 to 9)

### D-100 Providers sit behind one interface, with a registry
- **Status:** Planned. `NotificationProvider` per vendor (Twilio, Termii, SendGrid, Mailgun, Firebase, custom). A registry returns a channel's providers in fallback order. A fake provider is built first, for tests.

### D-101 Error classification
- **Status:** Planned. 429 and 503 are temporary (retry); 400 is permanent (no retry). **A 429 must never trip the circuit breaker:** it means Beacon is sending too fast, not that the provider is down, and failing over would only burn the secondary provider's limits as well.

### D-102 Outbound rate limiting per provider
- **Status:** Planned. A token bucket per provider, in process by default, distributed (Redis) optionally.

### D-103 A circuit breaker per provider, with fallback
- **Status:** Planned.

### D-104 The suppression check comes before sending
- **Status:** Planned. Hard bounces, spam complaints, unsubscribes and admin suppressions block delivery; the task is marked `SUPPRESSED`. This is a legal and sender-reputation requirement.

### D-105 What happens after a send attempt
- **Status:** Planned.
  - Success: delete the queue row; the status record becomes `SENT` with the provider name. `DELIVERED` arrives later through webhooks.
  - Temporary failure: `retry_count + 1`, `available_at` moved later with exponential backoff, back to `QUEUED`.
  - Permanent failure or retry limit reached: dead letter (`FAILED`), with a failure reason that contains no personal data.

### D-106 The worker looks up the user again at delivery time
- **Status:** Planned. The destination (phone, email, push token) is fetched from `UserPreferenceResolver` when delivering, not stored in the queue, which keeps contact details out of the queue and uses the latest values.

### D-107 Webhooks
- **Status:** Planned. Provider callbacks are verified with HMAC signatures (the real security control). The starter's `NotificationWebhookSecurityConfigurer` lets those paths through the host's Spring Security. Bounces and spam complaints add the contact to the suppression list automatically.
- **Path (Planned, 2026-11-02):** webhook endpoints live under `/notifications/webhooks/**`, as the README already states.

### D-108 No personal data in logs or failure reasons
- **Status:** Planned, to be proven by tests that check stored reasons and captured logs.

### D-109 A daily reconciliation job
- **Status:** Planned. Compares Beacon's records with provider reports and corrects mismatches.

### D-110 Batch sends
- **Status:** Planned. Decided 2026-11-02.
- **Decision:** `NotificationService.sendBatch` with `BatchNotificationRequest`, and the host's `UserBatchSource` as the source of users, as described in the old spec (users paged in chunks rather than loaded at once). The contracts exist in `beacon-api`; `sendBatch` returns `null` today (G-08).

### D-111 Later phases
- **Status:** Planned for later phases, after items 1 to 9. Decided 2026-11-02.
- **Scope:** monthly partitioning of `notification_status`, monitoring and alerting, and a dead-letter dashboard (all described in the old spec). None of them is designed in detail yet.

---

## 10. Testing

### D-120 Strict TDD
- **Status:** Accepted. Red, then green, then refactor, for every behaviour.

### D-121 Real infrastructure in integration tests
- **Status:** Accepted, in code.
- **Decision:** PostgreSQL through Testcontainers (`jdbc:tc:postgresql:18-alpine:///testdb`), never H2, which does not behave like PostgreSQL for `SKIP LOCKED`. Redis through a Testcontainers `GenericContainer` (`redis:7.0.11-alpine`) with `@ServiceConnection`.

### D-122 The test toolkit
- **Status:** Accepted. JUnit Jupiter, AssertJ and JUnit assertions, Mockito, Awaitility, Testcontainers. WireMock is planned for provider tests.

### D-123 The adversarial categories every component must cover where relevant
- **Status:** Accepted.
  - Concurrency, using ready, start and done latches so all threads really start together.
  - Transactional rollback.
  - Crash and restart durability.
  - Idempotency under a race, not only sequentially.
  - Provider failure paths, including 429 not tripping the breaker.
  - Time, with a fixed Clock.
  - Personal data masking.

### D-124 Testing rules learned the hard way
- **Status:** Accepted.
  - A test that needs real commits uses `@Transactional(propagation = NOT_SUPPORTED)` and clears its tables both before and after each test.
  - PostgreSQL aborts the whole transaction after any error, so the failing statement must be the last one in a shared transaction.
  - `@DataJpaTest` does not load your configuration classes; import them explicitly.
  - A test configuration shared by several test classes may only hold beans that every one of those tests can build.
  - Redis has no rollback between tests; flush it in `@BeforeEach`.
  - Check stored data by reading raw rows (`JdbcTemplate.queryForMap`), not through the entity being tested.
  - Build test timestamps from the fixed Clock.
  - Never assert on wall-clock duration; use Awaitility with generous timeouts.
  - Verify the exact type with Mockito instead of `any()` whenever the type matters.
  - Use `saveAndFlush` when a constraint must fire inside the assertion.
  - Watch for tests that pass for the wrong reason: empty test bodies, equality checks on ID-only `equals`, the wrong table or key, loose matchers.

### D-125 Running tests locally
- **Status:** Accepted. Command confirmed on 2026-11-02 (`JdbcDeduplicationStoreTest`: 17 run, 0 failures).
- **Decision:** Windows with Docker Desktop running and `JAVA_HOME` pointing to JDK 21. The machine's default `JAVA_HOME` is JDK 25, so set it to JDK 21 for the command. From the repo root:
  - Git Bash: `JAVA_HOME="/c/Program Files/Java/jdk-21" ./mvnw -pl beacon-core -am test -Dtest=ClassName -Dsurefire.failIfNoSpecifiedTests=false`
  - cmd: `set "JAVA_HOME=C:\Program Files\Java\jdk-21"`, then `mvnw.cmd -pl beacon-core -am test -Dtest=ClassName -Dsurefire.failIfNoSpecifiedTests=false`
- **Why the extra flags:** `-am` builds `beacon-api` in the same run (it is not installed in `~/.m2`). `-Dsurefire.failIfNoSpecifiedTests=false` stops `beacon-api` failing because it has no test matching `-Dtest`.
- **Superseded form:** `mvnw.cmd -pl beacon-core test -Dtest=ClassName` fails on a clean `~/.m2`.

---

## 11. Working agreement

### D-130 Who writes what
- **Status:** Accepted.
- **Decision:** Abdulmalik writes all production code. Claude explains, reviews, catches bugs and writes tests or documents when asked. Abdulmalik's boss reviews as well, and findings are passed between the two.

### D-131 How guidance is given
- **Status:** Accepted.
  - One step at a time, in plain language, big picture first.
  - Answer the question that was asked, without piling on extras.
  - Point to the lines to change in existing files instead of regenerating whole files.
  - Check the latest code before listing what's left; never repeat stale or already-fixed items.

---

## 12. Superseded decisions (for ADR history)

| Old decision | Replaced by | Why it changed |
|---|---|---|
| In-memory queue as the default (`QueueType.IN_MEMORY`) | D-004, D-032 database queue | Not durable; would break "no missed sends" |
| `notification_status` used as the queue | D-032 two tables | The work table must stay small; history must be permanent |
| `UNIQUE (idempotency_key)` on the status table | D-033 | Crashes on fan-out |
| `@PrePersist` and `@PreUpdate` setting timestamps | D-037 | Bypassed the Clock and overwrote test data |
| `@GeneratedValue` on the queue entity ID | D-038 | The ID is needed before any queue sees the task |
| `String` IDs on the entities and DTO | D-038 | Mismatched the `UUID` columns |
| Notification ID hashed from key plus timestamp | D-054 | Changed on every call |
| `isDuplicate` on `DeduplicationStore` | D-070 | Duplicate of `isSeen` |
| Event wrapping the raw request; `BeaconEvent` base class | D-052 | Coupled the internal event to the public API; blocked records |
| Listener saving through the repository directly | D-090 | Made queue implementations unswappable |
| Queue writing the status record | D-058 | The audit trail is not part of the queue |
| `scheduledAt` on `DeliveryTask` | D-091 | The queue needs the time it may be picked up |
| Rendering before queuing | D-081 | Queue bloat |
| Thymeleaf; a lexer, parser and syntax-tree template engine | D-080 | Too heavy for a library, more than needed |
| Redis `SETNX` per-notification lock as the worker's main guard | D-073 (open) | `SKIP LOCKED` and `PROCESSING` already guarantee one worker |
| `NotificationWebhookSecurityConfigurer` in `beacon-api` | D-015 | Spring Security dependency |

---

## 13. Known gaps in the code (first listed 2026-11-01, rechecked 2026-11-02)

Every gap below is a code finding for Abdulmalik to fix, except G-14, which is a documentation gap fixed by the README rewrite. Fixed gaps are kept, marked **Fixed**, so the history stays visible.

### 13.1 Gaps listed on 2026-11-01

- **G-01** `beacon-api` depends on Spring: the POM pulls `spring-boot-starter` and `hibernate-validator`, `BeaconApiApplication` exists, and `NotificationQueue` imports Spring's `@Validated` (never applied). Breaks D-011. *Still open.*
- **G-02** The queue bean placement breaks D-097. *Still open, changed:* `BeaconQueueConfig` is now in `src/main`, but `BeaconCoreConfig.java:29-34` still declares the same bean, so there are two `databaseNotificationQueue` definitions and only one checks `beacon.queue.type`. `BeaconCoreConfigTest` is likely to fail because of it (G-42).
- **G-03** `DatabaseNotificationQueueTest` never clears `notification_queue`, so the row count in "several tasks" depends on test order. *Still open:* `tearDown` is empty (line 55).
- **G-04** A request without priority fails after commit (D-059 not applied). **Fixed** (listener `resolvePriority`).
- **G-05** `encryptContext` returns `null`, so the context never reaches the worker (D-064 not applied). **Fixed** (`AesGcmContextEncryptor`, listener line 67). The key is not wired yet (O-06).
- **G-06** Quiet hours are not applied (D-061 not applied). **Fixed** (`QuietHoursPolicy`).
- **G-07** Flyway uses the default history table (D-031 not applied). **Fixed for tests** (`application-test.properties`); the final form of D-031 is not built.
- **G-08** `sendBatch`, `getStatus` and `cancelScheduled` return `null` or do nothing. *Still open* (`NotificationServiceImplementation.java:55`, `:60`, `:64-66`).
- **G-09** `NotificationStatusEntity.equals` throws a `NullPointerException` when `id` is still null (before save). *Still open* (`NotificationStatusEntity.java:187`).
- **G-10** `JdbcDeduplicationStore` logs a normal duplicate at ERROR level, with a stack trace. *Still open* (`JdbcDeduplicationStore.java:50`, seen in the 2026-11-02 test run).
- **G-11** `beacon-core` depends on `spring-boot-starter-webmvc`, which drags a web stack into every host. *Still open.* Resolution decided in D-019.
- **G-12** Every module (all four) contains a Spring Initializr `@SpringBootApplication` class, an `application.properties`, `HELP.md` and `spring-boot-maven-plugin`. *Still open.* Resolution decided in D-018.
- **G-13** `AdvancedTemplateEngine` is an empty stub. *Still open.* Decided: delete it (D-080).
- **G-14** README states Spring Boot 3.2+; the build uses 4.0.6. *Still open.* Documentation gap, not a code finding: fixed by the README rewrite in Phase 3 of the documentation rebuild.
- **G-15** `deduplication_store` has no cleanup. *Still open* (O-07).
- **G-16** The listener class still carries a `//todo: To implement tests for this class.` comment, although `NotificationIngestionTransactionalTest` now covers it. *Still open* (listener line 27).

### 13.2 Gaps added on 2026-11-02: production code and schema

- **G-17** A notification can be lost at ingestion while its key stays claimed. `send()` claims the idempotency key before the listener runs. If the after-commit listener throws (for example, the database is unavailable), nothing is queued and a retry with the same key is rejected as a duplicate; with the JDBC store the claim has already committed with the host's transaction. Without a transaction the listener runs inline, so its exception reaches the caller and the key stays claimed. See D-050 and O-13.
- **G-18** Nothing in `src/main` registers the service, the listener, a dedup store, the encryptor, the type registry or a template engine as beans. Beacon cannot yet run inside a host application. See D-016.
- **G-19** `beacon-api` and `beacon-spring-boot-starter` use groupId `io.github.abdulmalikalayande.beacon`; the decision is `io.github.abdulmalikalayande` for every module. Breaks D-017.
- **G-20** `DeliveryTaskEntity` does not implement `Persistable` and has no `@Version`, so `deliveryTaskRepository.save()` in `DatabaseNotificationQueue.enqueue` calls `merge()` and runs a select before every insert. See D-038.
- **G-21** `DefaultDeduplicationStore` enforces its one-hour retention only when the map is full (line 62), and `isSeen` ignores expiry (line 111). Below capacity, a key older than one hour is still rejected. The size check and eviction are not atomic (the code's own comments say so). See D-071.
- **G-22** `NotificationRequestedEventListener` line 96 calls `channels.retainAll(preference.enabledChannels())`, which throws a `NullPointerException` when the host returns a null `enabledChannels`.
- **G-23** `NotificationServiceImplementation.uuidFrom` marks a deterministic, name-based UUID as version 4 (random) (lines 85-86) and turns any failure into a bare `RuntimeException` (line 92).
- **G-24** `List.copyOf` and `Map.copyOf` in the request records throw a `NullPointerException` for a null element before Bean Validation runs, so `List<@NotNull …>` never reports the violation. Null context values fail the same way.
- **G-25** `notificationId` is a `UUID` in `DeliveryTask`, the event and the tables, but a `String` in `NotificationResponse`, `NotificationStatusRecord`, `NotificationStatusEvent`, `ProviderWebhookPayload`, `NotificationService.getStatus` and `cancelScheduled`, `NotificationStatusTracker.get`, and the dedup lock methods.
- **G-26** `NotificationStatusEntity` generates its own UUID, so a status row is linked to its `notification_queue` row only by `(idempotency_key, channel)`.
- **G-27** The listener reads the Clock twice per task (`requestedAt` at line 83, `createdAt` at line 87), and `NotificationStatusEntity.fromTask` reads it again, so with a real clock the timestamps can differ slightly.
- **G-28** Explicit `preferredChannels` are not checked against the type's supported channels, so an OTP can go out by EMAIL if the host asks for it. See D-056 and O-17.
- **G-29** `deduplication_store` has a database-generated `id` (`gen_random_uuid()`, against D-038) and a `created_at` from the database's `now()` (against D-020), because `JdbcDeduplicationStore` inserts only the key. Its `notification_id` and `metadata` columns are never written.
- **G-30** The `notification_status` indexes have no recorded decision. `idx_notification_status_failed` and `idx_notification_status_processing` are on `(status)`, which does not support the time-range queries they are meant for.
- **G-31** No CHECK constraints on the enum columns (`status`, `channel`, `type`, `priority`, `provider`) in either table.
- **G-32** `AesGcmContextEncryptor` does not convert Jackson exceptions from `writeValueAsBytes` and `readValue` to `NotificationException`, and binds no additional authenticated data, so a ciphertext copied to another row still decrypts. See D-064.
- **G-33** The `NotificationType.PAYMENT_RECEIPT` Javadoc says "High priority"; the registry maps it to MEDIUM (D-063).
- **G-34** The V1 migration comment calls `notification_queue` a "Transactional Outbox" that is "deleted when terminal", and test classes are named `OutboxPollerTest` and `TransactionalOutboxRepositoryTest`. D-032 chose "queue" over "outbox", and no code deletes rows yet.
- **G-35** `DatabaseNotificationQueue.enqueue` and `NotificationStatusEntity.fromTask` always write `QUEUED`, even when `availableAt` is in the future. `DELAYED` is never written, and `CREATED` appears only in the response. See O-14.

### 13.3 Gaps added on 2026-11-02: build and packaging

- **G-36** The root `pom.xml` sets `maven-compiler-plugin` to `<source>6</source><target>6</target>` (lines 66-67). The parent's `release=21` overrides it today, but it contradicts Java 21.
- **G-37** Only `beacon-core` inherits the root POM; the other three modules use `spring-boot-starter-parent` directly. The starter does not depend on `beacon-core`, and `beacon-test` depends on no Beacon module. See D-010.
- **G-38** The root `mvnw` and `mvnw.cmd` contain unfilled `@@project.version@@` placeholders (`mvnw:22`, `:223`, `:225`; `mvnw.cmd:21`, `:122`, `:136`), and the wrapper jar they fall back on is gitignored (`.gitignore:3`). Not tested on a fresh clone.
- **G-39** Testcontainers is pinned to 1.21.4 (`beacon-core/pom.xml:83`, `:94`) while the comment at line 79 says "Testcontainers 2.x".
- **G-40** `beacon-core/src/main/resources/application.properties` (`spring.application.name`, `spring.jpa.hibernate.ddl-auto=validate`) would land on every host's classpath. Covered by D-018.
- **G-41** `beacon-core/pom.xml` hard-codes the `beacon-api` version (`0.0.1-SNAPSHOT`) instead of `${project.version}`.
- **G-51** The licence is not settled. The `LICENSE` file is the GNU General Public License version 3, but the old README states Apache License 2.0 (its badge and its License section), and every POM has an empty `<license/>` element. No document states a licence until O-18 is decided.

### 13.4 Gaps added on 2026-11-02: tests

- **G-42** `BeaconCoreConfigTest` (4 tests) is likely to fail (not run): the runner loads `BeaconCoreConfig`, whose queue bean needs a `DeliveryTaskRepository` the runner does not provide. Caused by G-02.
- **G-43** `DatabaseNotificationQueueTest.putIn_null_rejectedClearly` (lines 131-134) expects `ConstraintViolationException`, but nothing validates `enqueue`; it is likely to hit a `NullPointerException` instead (not run).
- **G-44** Tests that pass for the wrong reason: `TransactionalOutboxRepositoryTest:174-193` checks the wrong repository; `TransactionalOutboxRepositoryTest:104` compares the cached instance with itself; `TransactionalOutboxRepositoryTest` Variant1 (lines 129-153) uses the database's `NOW()` and a `type` of `"WELCOME"`; `JdbcDeduplicationStoreTest:71-77` and `BeaconCoreApplicationTests` are empty.
- **G-45** `TransactionalOutboxRepositoryTest:226-247` holds a leftover `main` that loops forever and has an inverted condition at line 242.
- **G-46** Two `await()` calls have no timeout and can hang the build: `DefaultDeduplicationStoreTest:182-184`, `NotificationServiceImplementationTest:270-272`.
- **G-47** Weak checks in `NotificationServiceImplementationTest`: line 328 checks only that something was thrown; line 182 verifies `never()` for one argument; lines 284-300 and 304-319 use a mock store, so they cannot show independence or that the key stays reserved; lines 51-53 use a `Validator` after its factory is closed; line 40's `@DisplayName` names the wrong class.
- **G-48** Duplicated and copy-pasted tests: `DefaultDeduplicationStoreTest` `SeenStateTests` and `DuplicateDetectionTests` are identical; `RedisDeduplicationStoreTest` lines 191-218 and 264-281 repeat the same checks; `RedisDeduplicationStoreTest:143-144` says "blank key" in the null-key test.
- **G-49** `util/TestEncryptionUtil` uses AES/ECB, not the production GCM encryptor.
- **G-50** No test documents the rollback behaviour of the in-memory and Redis dedup stores (D-072).

---

## 14. Open questions

### 14.1 Resolved on 2026-11-02

- **O-01** Is MEDIUM's quiet-hours behaviour confirmed (D-062)? **Resolved:** yes, MEDIUM ignores quiet hours.
- **O-04** The default dedup store, and how a host picks another (D-071). **Resolved:** JDBC by default, another selected by a property (wiring Planned).
- **O-05** Rollback behaviour of the in-memory and Redis dedup stores (D-072). **Resolved:** documented as a limitation of those stores.
- **O-09** The supported Spring Boot range (D-005). **Resolved:** Spring Boot 4.0.x.
- **O-10** What happens to `BeaconCoreApplication` and the other Initializr classes (G-12). **Resolved:** D-018.
- **O-11** Where the web dependency belongs (G-11). **Resolved:** D-019.
- **O-12** `AdvancedTemplateEngine`: delete, or plan it. **Resolved:** delete (D-080).

### 14.2 Still open

- **O-02** The item 6 parameters (D-099). To be decided during item 6.
- **O-03** Remove `enqueueDelayed` (D-098)? Removal agreed; stays open until it is removed.
- **O-06** Encryption key: property name, required or optional, what happens when it's missing, and rotation (D-064). **Current proposal:** one required property holding a Base64-encoded 32-byte key; startup fails when it is missing. Property name not chosen. Rotation stays open.
- **O-07** Retention for `deduplication_store` (D-039).
- **O-08** The delivery lock methods (D-073). Open until item 8.
- **O-13** Should the queue rows be written inside the host's transaction (a true transactional outbox) instead of in the after-commit listener? Until this is decided, the documents state that "no missed sends" starts once the queue rows are committed (G-17, D-050).
- **O-14** Should a task held by quiet hours or `scheduledAt` show `DELAYED`, and should `CREATED` ever be written? Today `QUEUED` is the only status written, and `CREATED` appears only in the `send()` response (G-35).
- **O-15** Cross-channel fallback: when every provider for a channel fails, should Beacon try another channel the user has enabled, as the old spec describes?
- **O-16** A separate datasource for Beacon's tables (the README's `beacon.datasource.*`), or always the host's datasource?
- **O-17** Should explicit `preferredChannels` be limited to the type's supported channels (for example, no OTP by EMAIL)? See D-056 and G-28.
- **O-18** Which licence does Beacon use? The `LICENSE` file is GPL-3.0; the old README said Apache License 2.0 (G-51).
