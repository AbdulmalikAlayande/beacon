# 0023. Testing uses strict TDD and real infrastructure

- Status: Accepted
- Date: 2026-11-02
- Register entries: D-120, D-121, D-122, D-123, D-124, D-125
- Document status: Approved, last checked against the code on 2026-11-02

## Context

Beacon's guarantees (no notification after a rollback, no duplicates, no missed sends) depend on database behaviour, transactions and concurrency. Those are exactly the things a mock or an in-memory database gets wrong. H2, for example, does not behave like PostgreSQL for `SKIP LOCKED`. Several earlier tests also passed for the wrong reason.

## Decision

- **Strict TDD:** red, then green, then refactor, for every behaviour.
- **Real infrastructure in integration tests:**
  - PostgreSQL through Testcontainers (`jdbc:tc:postgresql:18-alpine:///testdb`), never H2;
  - Redis through a Testcontainers `GenericContainer` (`redis:7.0.11-alpine`) with `@ServiceConnection`.
- **Toolkit:** JUnit Jupiter, AssertJ and JUnit assertions, Mockito, Awaitility, Testcontainers. WireMock is planned for provider tests.
- **Adversarial categories every component must cover where relevant:**
  - concurrency, using ready, start and done latches so that all threads really start together;
  - transactional rollback;
  - crash and restart durability;
  - idempotency under a race, not only sequentially;
  - provider failure paths, including a 429 not tripping the breaker;
  - time, with a fixed Clock;
  - personal data masking.
- **Rules learned the hard way** (each one is explained in `docs/testing.md`):
  - a test that needs real commits uses `@Transactional(propagation = NOT_SUPPORTED)` and clears its tables before and after each test;
  - PostgreSQL aborts the whole transaction after any error, so the failing statement must be the last one in a shared transaction;
  - `@DataJpaTest` does not load your configuration classes; import them explicitly;
  - a test configuration shared by several test classes may hold only beans that every one of those tests can build;
  - Redis has no rollback between tests; flush it in `@BeforeEach`;
  - check stored data by reading raw rows (`JdbcTemplate.queryForMap`), not through the entity being tested;
  - build test timestamps from the fixed Clock;
  - never assert on wall-clock duration; use Awaitility with generous timeouts;
  - verify the exact type with Mockito instead of `any()` whenever the type matters;
  - use `saveAndFlush` when a constraint must fire inside the assertion;
  - watch for tests that pass for the wrong reason: empty test bodies, equality checks on ID-only `equals`, the wrong table or key, loose matchers.
- **Running tests locally:** Windows with Docker Desktop running and JDK 21. From the repo root, in Git Bash:

  ```bash
  JAVA_HOME="/c/Program Files/Java/jdk-21" ./mvnw -pl beacon-core -am test -Dtest=ClassName -Dsurefire.failIfNoSpecifiedTests=false
  ```

  The cmd form and the reasons for each flag are in `docs/testing.md`.

## Consequences

- Every behaviour has a test that failed before the code existed.
- Integration tests need Docker, and the first run downloads images.
- A green test suite says something about real PostgreSQL behaviour, including `SKIP LOCKED` and transaction rollback.

## Alternatives rejected

- **H2 instead of PostgreSQL.** Rejected because it does not behave like PostgreSQL for `SKIP LOCKED`.
- **`mvnw.cmd -pl beacon-core test -Dtest=ClassName`** (the earlier documented command). Replaced because it fails on a clean `~/.m2`, where `beacon-api` is not installed.

## In the code

- Tests: `beacon-core/src/test/java/io/github/abdulmalikalayande/beacon/core/`.
- Test configuration: `beacon-core/src/test/resources/application-test.properties`, `beacon-core/src/test/java/io/github/abdulmalikalayande/beacon/core/config/BeaconCoreTestConfig.java`.
- Redis container: `beacon-core/src/test/java/io/github/abdulmalikalayande/beacon/core/dedup/RedisDeduplicationStoreTest.java`.
- Not yet true: known problems in the current tests are listed as register gaps G-42 to G-50.
- Not built yet: tests for crash and restart durability, provider failures and personal data masking, because the code they test does not exist yet.
