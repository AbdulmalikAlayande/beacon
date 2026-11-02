# CLAUDE.md

Rules for every Claude session in this repository. Rules and links only; the linked documents explain.

## What Beacon is

Beacon is a Spring Boot starter that sends push, SMS and email notifications from inside a host application: the host adds the dependency, implements `UserPreferenceResolver`, and calls `NotificationService.send()`. It promises no notification for work that rolled back, no duplicates, and no missed sends, using nothing beyond the host's own PostgreSQL. Java 21, Spring Boot 4.0.x, PostgreSQL 14+, Maven modules `beacon-api`, `beacon-core`, `beacon-spring-boot-starter`, `beacon-test`.

Docs: decisions in [`docs/adr/`](docs/adr/README.md), status in [`docs/ROADMAP.md`](docs/ROADMAP.md), testing in [`docs/testing.md`](docs/testing.md), contributor rules in [`CONTRIBUTING.md`](CONTRIBUTING.md), and the full decision history (D-xxx decisions, G-xx gaps, O-xx open questions) in [`docs/archive/decision-register.md`](docs/archive/decision-register.md).

## Working agreement (read first)

- **Abdulmalik writes all production code.** Claude explains, reviews, finds bugs, and writes tests or documents only when he asks.
- When something in his code needs changing, point to the file and lines. Do not rewrite the file.
- Plain language, big picture first, one step at a time. Answer the question that was asked; do not pile on alternatives.
- Check the latest code before listing what is left. Never repeat stale or already-fixed items.
- Claude never pushes to `main` and never merges a pull request.
- Claude never posts to GitHub (comments, reviews, issues, pull requests) without Abdulmalik's approval. Draft first, as a file, and show him.
- Ask before running any command that changes state. Read-only commands and the test command are fine.

## Module rules

- `beacon-api`: contracts only, plain Java 21. Its only allowed dependency is `jakarta.validation-api`. No Spring. ([ADR 0004](docs/adr/0004-api-plain-java-split-by-implementer.md))
- `beacon-core` depends on `beacon-api`; the starter depends on `beacon-core`; `beacon-test` depends on `beacon-api` and `beacon-core`. Nothing depends on the starter except hosts. ([ADR 0003](docs/adr/0003-four-modules-dependency-direction.md))
- `spi/` = the host implements it; `port/` = Beacon implements it; `service/` = the host calls it. ([ADR 0004](docs/adr/0004-api-plain-java-split-by-implementer.md))
- GroupId `io.github.abdulmalikalayande` for every module; base package `io.github.abdulmalikalayande.beacon`. ([ADR 0003](docs/adr/0003-four-modules-dependency-direction.md))
- Library modules are plain jars: no `@SpringBootApplication`, no `application.properties`, no `spring-boot-maven-plugin`. ([ADR 0003](docs/adr/0003-four-modules-dependency-direction.md))
- Web and Spring Security dependencies belong in the starter only. ([ADR 0003](docs/adr/0003-four-modules-dependency-direction.md))
- Beacon's own tests live in `beacon-core/src/test`, never in `beacon-test`. ([ADR 0003](docs/adr/0003-four-modules-dependency-direction.md))

## Architecture rules

- Beacon runs inside the host; nothing beyond the host's PostgreSQL is required. Redis and brokers are optional. ([ADR 0001](docs/adr/0001-embedded-starter-no-required-infrastructure.md))
- Beacon never reads host tables; user data comes only through `UserPreferenceResolver`. ([ADR 0002](docs/adr/0002-host-owns-user-data.md))
- Beans are registered with `@Bean` plus `@ConditionalOnMissingBean` (and `@ConditionalOnProperty` where there are several implementations), never `@Component`. Database beans go in their own configuration class. ([ADR 0005](docs/adr/0005-beans-registered-with-bean-never-component.md))
- Time comes from the injected `Clock`; never `Instant.now()`, `LocalTime.now()` or SQL `now()` in business logic. `Instant` in Java, `timestamptz` in PostgreSQL. ([ADR 0006](docs/adr/0006-time-from-injected-clock.md))
- Flyway owns the schema (`ddl-auto=validate`); history table `beacon_flyway_schema_history`; never edit a merged migration. ([ADR 0007](docs/adr/0007-flyway-owns-the-schema.md))
- `notification_queue` is the small work table; `notification_status` is the permanent history, unique on `(idempotency_key, channel)`. ([ADR 0008](docs/adr/0008-two-tables-queue-and-status-history.md))
- Entities hold data only: no `@PrePersist`/`@PreUpdate`, `EnumType.STRING`, `equals`/`hashCode` on the ID only, UUID IDs created in code. ([ADR 0009](docs/adr/0009-entity-rules.md))
- `send()` writes one request row in the host's transaction; the queue poller expands it later. This is decided but **not built**; the code still uses the after-commit listener. ([ADR 0010](docs/adr/0010-send-writes-request-row-transactional-outbox.md), [ADR 0029](docs/adr/0029-after-commit-listener-writes-queue-rows.md))
- The notification ID is derived from the idempotency key; a repeated key throws `DuplicateNotificationException`. ([ADR 0011](docs/adr/0011-notification-id-from-idempotency-key.md))
- Channels: explicit channels or the type's channels, intersected with the user's enabled channels. Unknown user or no overlap: log and queue nothing. ([ADR 0012](docs/adr/0012-channel-resolution-and-unknown-users.md))
- Missing priority comes from the type registry. HIGH and MEDIUM ignore quiet hours; LOW waits. ([ADR 0013](docs/adr/0013-priorities-type-registry-quiet-hours.md))
- The template context is encrypted (AES-256-GCM) while queued. ([ADR 0014](docs/adr/0014-context-encrypted-while-queued.md))
- Deduplication claims a key in one atomic step (`markSeen`); never read first, then write. `isSeen` never decides. ([ADR 0015](docs/adr/0015-deduplication-atomic-claim-three-stores.md))
- Templates use Beacon's own `${key}` engine and are rendered at the worker, never before queuing. ([ADR 0016](docs/adr/0016-simple-template-engine-render-at-worker.md))
- `DeliveryTask` is the queue contract; it becomes `DeliveryTaskEntity` only inside `DatabaseNotificationQueue`. ([ADR 0017](docs/adr/0017-delivery-task-is-the-queue-contract.md))
- Claiming uses short transactions with `FOR UPDATE SKIP LOCKED` and the `PROCESSING` status; each poller tick recovers, expands, then claims; no exception escapes a tick. ([ADR 0018](docs/adr/0018-claiming-the-database-queue.md))
- A 429 never trips the circuit breaker. ([ADR 0019](docs/adr/0019-providers-classification-rate-limits-circuit-breakers.md))
- Suppression is checked before sending; no personal data in logs or failure reasons. ([ADR 0020](docs/adr/0020-delivery-suppression-outcomes-no-personal-data.md))
- Webhooks are verified by HMAC signature. ([ADR 0021](docs/adr/0021-webhooks-hmac-and-daily-reconciliation.md))
- Superseded designs are kept as ADRs 0024 to 0029; do not reintroduce them. ([index](docs/adr/README.md))

## Tests

Run one class from the repo root (Git Bash; JDK 21 and Docker Desktop required; the machine default `JAVA_HOME` is JDK 25):

```bash
JAVA_HOME="/c/Program Files/Java/jdk-21" ./mvnw -pl beacon-core -am test -Dtest=ClassName -Dsurefire.failIfNoSpecifiedTests=false
```

Rules that prevent known failures ([`docs/testing.md`](docs/testing.md) has the reasons and the cmd form):

- Strict TDD. Real PostgreSQL and Redis through Testcontainers; never H2. ([ADR 0023](docs/adr/0023-strict-tdd-and-real-infrastructure.md))
- Tests that need real commits: `@Transactional(propagation = NOT_SUPPORTED)`, and clear tables before and after each test.
- In a shared PostgreSQL transaction, the failing statement must be the last one.
- `@DataJpaTest` does not load configuration classes; `@Import` them.
- A shared test configuration holds only beans every test using it can build.
- Flush Redis in `@BeforeEach`.
- Check stored data with raw rows (`JdbcTemplate.queryForMap`), not the entity under test.
- Test timestamps come from the fixed Clock (`BeaconCoreTestConfig`: `2026-05-31T03:00:00Z`, `Africa/Lagos`).
- No wall-clock duration assertions; Awaitility with generous timeouts.
- Mockito: verify exact types, not `any()`, when the type matters. `saveAndFlush` when a constraint must fire.
- Concurrency tests use ready, start and done latches.
- Watch for tests that pass for the wrong reason (empty bodies, ID-only `equals`, wrong table or key, loose matchers).

## Status

Status lives only in [`docs/ROADMAP.md`](docs/ROADMAP.md): items, code findings and open decisions. This file never says which item the project is on. Read the roadmap, and check the code, before saying what is done or left.

## When sources disagree

- The code is the truth about what exists today. The decision register and the ADRs are the truth about what was decided and why. Older documents in `docs/archive/` are history only.
- When two sources disagree, report the conflict and ask. Never pick a side silently.
- Never invent a reason for a decision. If none is recorded, say "reason not recorded" and ask.
- Label anything designed but not built **Planned**, and anything undecided **Open** with its O-xx number.
- No licence is stated anywhere until O-18 is decided.
