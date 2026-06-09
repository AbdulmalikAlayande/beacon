# Contributing to Beacon

Thanks for your interest in contributing. Beacon is an active project and contributions are welcome — whether that's fixing a bug, improving documentation, adding a provider adapter, or building out one of the planned features.

Before you write any code, read this document. It will save you time.

---

## Table of contents

- [Before you start](#before-you-start)
- [Project structure](#project-structure)
- [Architecture and design decisions](#architecture-and-design-decisions)
- [Setting up your development environment](#setting-up-your-development-environment)
- [How to contribute](#how-to-contribute)
- [Coding standards](#coding-standards)
- [Testing standards](#testing-standards)
- [Submitting a pull request](#submitting-a-pull-request)
- [What we are looking for](#what-we-are-looking-for)
- [What to avoid](#what-to-avoid)
- [Getting help](#getting-help)

---

## Before you start

**Open an issue before writing code.** If you want to fix a bug, add a feature, or change behaviour, open an issue first. This prevents wasted effort — a change might already be in progress, out of scope, or need design discussion before implementation. Small, obvious fixes (typos, documentation, broken links) can go straight to a PR.

**Read the technical spec.** Beacon was built from a detailed technical specification that documents every architectural decision, the database schema, the delivery lifecycle, and the reliability guarantees. Before making any changes to core behaviour, read it: [`notification-system-tech-spec.md`](docs/notification-system-tech-spec.md). Changes that contradict the spec without a strong reason will not be merged.

---

## Project structure

```
beacon/
├── beacon-api/                 # Public contracts — no Spring dependency
│   └── src/main/java/io/github/abdulmalikalayande/beacon/api/
│       ├── enums/              # NotificationChannel, NotificationStatus, etc.
│       ├── dto/                # Immutable records: NotificationPreference, DeliveryTask, etc.
│       ├── request/            # NotificationRequest, BatchNotificationRequest
│       ├── response/           # NotificationResponse
│       ├── exception/          # NotificationException hierarchy
│       ├── service/            # NotificationService (the main entry point)
│       ├── spi/                # Interfaces the HOST implements
│       └── port/               # Interfaces the LIBRARY implements (pluggable strategies)
│
├── beacon-core/                # All implementation
│   └── src/
│       ├── main/java/io/github/abdulmalikalayande/beacon/core/
│       │   ├── config/         # BeaconCoreConfig (Clock bean, etc.)
│       │   ├── dedup/          # DefaultDeduplicationStore, JdbcDeduplicationStore
│       │   ├── defaults/       # DefaultNotificationTypeRegistry
│       │   ├── entity/         # DeliveryTaskEntity, NotificationStatusEntity
│       │   ├── event/          # NotificationRequestedEvent (internal, not public API)
│       │   ├── listener/       # NotificationRequestedEventListener (@TransactionalEventListener)
│       │   ├── repository/     # Spring Data JPA repositories
│       │   ├── service/        # NotificationServiceImplementation
│       │   └── template/       # DefaultTemplateEngine
│       ├── main/resources/
│       │   └── db/migration/beacon/  # Flyway migrations (V1__, V2__, ...)
│       └── test/               # All core tests live here, not in beacon-test
│
├── beacon-spring-boot-starter/ # Autoconfiguration, property binding, @EnableBeacon
│
└── beacon-test/                # Test fixtures for HOST applications (not core tests)
    └── MockBeacon, InMemoryNotificationQueue, BeaconTestAssertions, etc.
```

**Key distinction:** `beacon-test` is for engineers who *use* Beacon and need test utilities. Your tests for Beacon itself live in `beacon-core/src/test`.

---

## Architecture and design decisions

These are locked decisions. Contributions that deviate from them need a very strong justification and a design discussion before code is written.

**The integration boundary is sacred.** Beacon never queries a host application's user tables. All user data flows through `UserPreferenceResolver`. This is a hard boundary — no exceptions.

**The transactional event mechanism is not optional.** `NotificationService.send()` publishes a Spring `ApplicationEvent`. The actual queue write happens in a `@TransactionalEventListener(phase = AFTER_COMMIT)` with `REQUIRES_NEW` propagation. This is what prevents ghost notifications on transaction rollbacks. Do not bypass this mechanism.

**The database-backed queue is the zero-infrastructure default.** The default queue uses `notification_queue` with `SELECT FOR UPDATE SKIP LOCKED`. It does not use in-memory structures for anything durable. This is what makes the "no missed sends" guarantee true. In-memory queues are available for testing only (`QueueType.IN_MEMORY`).

**Flyway owns the schema, not Hibernate.** `spring.jpa.hibernate.ddl-auto=validate`. Hibernate validates, Flyway manages. Never change DDL through entity annotations alone — write a migration first.

**The Clock is always injected.** Nothing in `beacon-core` calls `Instant.now()` or `LocalTime.now()` directly. Time is read through an injected `java.time.Clock` bean. This makes time-dependent logic testable. If you add any time-dependent code, inject the clock.

**`beacon-api` has no Spring dependency.** The api module is pure Java 21. It can depend on `jakarta.validation-api` (the spec jar) but nothing else Spring-related. If your change requires a Spring annotation in `beacon-api`, it belongs in `beacon-core` or `beacon-spring-boot-starter` instead.

**Enums are stored as strings.** Every `@Enumerated` field uses `EnumType.STRING`. Never `EnumType.ORDINAL`.

**Timestamps are `Instant` mapped to `timestamptz`.** Not `LocalDateTime`, not `OffsetDateTime`. `Instant` in Java, `timestamptz` in PostgreSQL.

**`equals()` and `hashCode()` on JPA entities use the primary key only.** No business fields.

---

## Setting up your development environment

**Requirements:**
- Java 21+
- Maven 3.8+
- Docker (Testcontainers pulls a real Postgres image for integration tests)
- An IDE with Lombok support if you add Lombok — currently the project does not use it

**Clone and build:**

```bash
git clone https://github.com/AbdulmalikAlayande/beacon.git
cd beacon
mvn clean install -DskipTests
```

**Run the tests:**

```bash
mvn test
```

Integration tests in `beacon-core` spin up a Postgres container via Testcontainers. Docker must be running. The first run pulls the Postgres image — subsequent runs use the cache.

**Run a specific test class:**

```bash
mvn test -pl beacon-core -Dtest=TransactionalOutboxRepositoryTest
```

---

## How to contribute

**1. Fork and create a branch.**

Branch naming:
- `fix/short-description` for bug fixes
- `feat/short-description` for new features
- `docs/short-description` for documentation changes
- `test/short-description` for test-only changes

**2. Write the test first.**

Beacon is built TDD. For any behaviour change or new feature, write a failing test before writing the implementation. See [Testing standards](#testing-standards) below.

**3. Write the implementation.**

Follow the coding standards below. Keep changes focused — a PR that fixes a bug should not also refactor unrelated code.

**4. Verify locally.**

```bash
mvn clean test
```

All tests must pass. If your change touches `beacon-core`, the full integration test suite runs against Postgres.

**5. Open the pull request.**

Use the PR template. Link the related issue. Describe what changed and why.

---

## Coding standards

**Java style:**
- Java 21. Use records for immutable data carriers. Use `var` where the type is obvious from the right-hand side.
- No Lombok in `beacon-api`. Minimal Lombok in `beacon-core` if needed, but prefer explicit code.
- Constructor injection only — no field injection (`@Autowired` on fields).
- `final` on fields wherever possible.
- No bare `Instant.now()` or `LocalTime.now()`. Use the injected `Clock`.

**Naming:**
- Classes implementing a `port/` interface follow the pattern `Default*`, `Jdbc*`, or `Redis*` — e.g. `DefaultTemplateEngine`, `JdbcDeduplicationStore`.
- Internal Spring events go in the `event/` package and are plain POJOs (no `extends ApplicationEvent`).
- Test classes are named `*Test` for unit tests and `*IntegrationTest` for tests requiring a container.

**Documentation:**
- Every public class and method in `beacon-api` must have Javadoc.
- New port interfaces must document who implements them (host or library) and why.
- Non-obvious decisions in `beacon-core` should have a comment explaining the reason, not just what the code does.

**Migrations:**
- Migration files follow Flyway naming: `V{n}__{description}.sql` under `src/main/resources/db/migration/beacon/`.
- Migrations are immutable once merged. Never edit an existing migration — add a new one.
- Include a comment at the top of each migration explaining the architectural reason for the change.

---

## Testing standards

**The rule:** every behaviour must have a test that fails without the implementation and passes with it. Green tests that don't actually verify anything are worse than no tests.

**Test stack:**
- **JUnit 5** — all tests
- **Mockito** — unit tests, for mocking collaborators
- **AssertJ** — assertions (not JUnit's `assertEquals` where AssertJ is clearer)
- **Testcontainers (Postgres)** — integration tests. H2 is not a substitute. The default queue uses `FOR UPDATE SKIP LOCKED` which H2 does not support correctly.
- **WireMock** — provider adapter tests. Simulates Twilio, SendGrid, Firebase responses.
- **Awaitility** — async assertions. When testing workers or listeners, don't `Thread.sleep()` — use `await().atMost(2, SECONDS).until(...)`.

**What we require:**

*Unit tests (Mockito + real validator, no container):*
- Validation: every `@NotBlank`, `@NotNull` constraint on request objects has a test proving it fires.
- Dedup: sequential duplicate rejected. Concurrent race (same key, N threads, one wins) — use `CountDownLatch`.
- Template engine: every rendering path including null inputs, missing keys, null values, regex-special characters in values.
- Quiet hours: use a fixed `Clock` to test time-based branching.

*Integration tests (Testcontainers Postgres):*
- Schema: tables exist with the right columns — query `information_schema`.
- Round-trip: save an entity, read it back, assert every field.
- Constraints: compound unique constraints are tested with the happy path (allowed) and crash path (violation throws `DataIntegrityViolationException`). Use `saveAndFlush`, not `save`.
- `FOR UPDATE SKIP LOCKED`: concurrent workers claim distinct rows, no duplicates.
- Transactional rollback: `send()` called inside a transaction that rolls back → queue is empty. This is the most important test in the library.
- No-transaction fallback: `send()` called outside a transaction → task is queued immediately.

*Adversarial tests — required for core guarantees:*
- Concurrency: multi-thread same key → exactly one winner. Use `ExecutorService` + `CountDownLatch`.
- Durability: task written as QUEUED, worker stopped, worker restarted, task delivered. Proves the DB-backed queue survives restarts.
- PII masking: trigger a failure with known sensitive data in context, assert `failure_reason` column does not contain it.

**What we do not want:**
- Empty test bodies that pass trivially.
- `Thread.sleep()` instead of Awaitility.
- Assertions on `equals()` alone when field-by-field assertions are needed.
- Tests that pass because of `@ConditionalOnMissingBean` side effects rather than actual behaviour.

---

## Submitting a pull request

**PR title format:** `[type] short description` — e.g. `[fix] prevent NPE in DefaultTemplateEngine when context value is null` or `[feat] add JdbcDeduplicationStore`.

**PR description must include:**
- What problem this solves or what feature it adds.
- Link to the related issue.
- What tests were added or changed.
- Any decisions made during implementation that weren't obvious.

**Review process:**
- All PRs require at least one review before merge.
- The CI must pass — all tests green, no compile errors.
- Feedback will be specific. If a reviewer asks for a change, it means the change is needed, not optional.
- Be prepared for iteration. Infrastructure library code is held to a high standard because bugs in it affect every host application.

---

## What we are looking for

**High-value contributions right now:**

- **Provider adapters** — implement `NotificationProvider` for Twilio, Termii, SendGrid, Mailgun, and Firebase. Each provider needs WireMock-backed tests covering: success, HTTP 429 (must throttle, not trip circuit breaker), HTTP 503 (retryable), HTTP 400 (non-retryable), timeout, and webhook signature validation.
- **JdbcDeduplicationStore** — the JDBC-backed deduplication store using a `notification_dedup` table. Full integration test suite including the concurrent race test against real Postgres.
- **Queue poller** — the scheduled `SELECT FOR UPDATE SKIP LOCKED` poller that hands batches to workers.
- **Quiet hours implementation** — the time-zone-aware quiet hours check in the event listener, tested with a fixed `Clock`.
- **Encryption seam** — pluggable context encryption/decryption. The `encryptContext`/`decryptContext` stub is in the listener and worker respectively; wire a real implementation.
- **Documentation** — the spec, the README, and Javadoc. Good documentation that helps people understand the system is as valuable as code.

---

## What to avoid

- **Breaking the public API in `beacon-api`.** Hosts depend on these interfaces. Additive changes are fine; removals and renames are breaking changes and require a version bump discussion.
- **Adding Spring dependencies to `beacon-api`.** It must remain pure Java.
- **Bypassing the transactional event mechanism.** No direct queue writes from `send()`.
- **Using `Instant.now()` directly.** Always use the injected `Clock`.
- **Storing enums as ordinals.** Always `EnumType.STRING`.
- **Editing existing Flyway migrations.** Add a new one.
- **Writing tests that pass trivially.** A test that passes without the implementation is not a test.
- **Mixing unrelated concerns in one PR.** One PR, one purpose.

---

## Getting help

Open an issue with the `question` label. Tag it clearly — `question: architecture`, `question: testing`, `question: setup` — so the right person can respond quickly.

For larger design questions or proposals, open a discussion rather than an issue.
