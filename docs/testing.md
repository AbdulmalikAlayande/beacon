# Testing Beacon

- Purpose: how Beacon is tested, how to run the tests on Windows, and the rules that keep tests honest.
- Document status: Draft, last checked against the code on 2026-11-02

## 1. Strategy

Beacon's guarantees (no notification after a rollback, no duplicates, no missed sends) depend on transactions, concurrency and real database behaviour. Those are exactly what mocks and in-memory databases get wrong, so Beacon is tested against the real thing.

- **Strict TDD.** Every behaviour starts with a failing test: red, then green, then refactor. A test that would pass without the code it tests is not a test.
- **Real infrastructure.** Integration tests run against PostgreSQL and Redis in Docker containers started by Testcontainers. H2 is never used, because it does not behave like PostgreSQL for `FOR UPDATE SKIP LOCKED`.

The decision behind both is [ADR 0023](adr/0023-strict-tdd-and-real-infrastructure.md).

## 2. Tools

| Tool | Used for |
|---|---|
| JUnit Jupiter | Every test (`@Nested`, `@DisplayName`, `assertThrows`, `assertAll`) |
| AssertJ | Fluent assertions, and Spring context assertions in `BeaconCoreConfigTest` |
| Mockito | Collaborators in unit tests (`NotificationServiceImplementationTest`, the resolver in `NotificationIngestionTransactionalTest`) |
| Awaitility | Waiting for asynchronous results without `Thread.sleep` |
| Testcontainers | PostgreSQL 18 and Redis 7 |
| WireMock | **Planned** for provider tests (item 7) |

## 3. Running tests on Windows

You need:

- **JDK 21.** If your default `JAVA_HOME` points to another JDK (on the maintainer's machine it is JDK 25), set it to JDK 21 for the command.
- **Docker Desktop, running.** Testcontainers starts PostgreSQL and Redis containers.
- **Git Bash** (or cmd, see below).

Run one test class from the repository root, in Git Bash:

```bash
JAVA_HOME="/c/Program Files/Java/jdk-21" ./mvnw -pl beacon-core -am test -Dtest=JdbcDeduplicationStoreTest -Dsurefire.failIfNoSpecifiedTests=false
```

The same in cmd:

```bat
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
mvnw.cmd -pl beacon-core -am test -Dtest=JdbcDeduplicationStoreTest -Dsurefire.failIfNoSpecifiedTests=false
```

Why each part is there:

- `-pl beacon-core` runs the tests of `beacon-core`, where all of Beacon's own tests live.
- `-am` also builds `beacon-api`, which `beacon-core` depends on. Without it, Maven looks for `beacon-api` in your local repository (`~/.m2`) and fails on a clean machine.
- `-Dsurefire.failIfNoSpecifiedTests=false` stops the `beacon-api` build from failing because it has no test matching `-Dtest`.

Leave out `-Dtest=...` and the surefire flag to run every test in `beacon-core`.

The first run takes several minutes, because Maven downloads dependencies and Docker pulls the images. On 2026-11-02 the first run of `JdbcDeduplicationStoreTest` took 5 minutes in total, with 17 tests, 0 failures.

## 4. Testcontainers setup

**PostgreSQL** uses the Testcontainers JDBC URL driver. There is no `@Container` field; the URL in `beacon-core/src/test/resources/application-test.properties` starts the container:

```properties
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration/beacon
spring.flyway.baseline-on-migrate=true
spring.flyway.table=beacon_flyway_schema_history

spring.datasource.url=jdbc:tc:postgresql:18-alpine:///testdb
spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
```

Test classes that need the database load it with `@TestPropertySource(locations = "classpath:application-test.properties")`. Flyway runs Beacon's real migrations, records them in `beacon_flyway_schema_history` ([ADR 0007](adr/0007-flyway-owns-the-schema.md)), and Hibernate validates the entities against them. There are no test-only migrations.

**Redis** is a `GenericContainer` declared in the test class, connected with `@ServiceConnection`:

```java
@Container
@ServiceConnection
static GenericContainer<?> redisContainer = new GenericContainer<>("redis:7.0.11-alpine")
        .withExposedPorts(6379);
```

See `beacon-core/src/test/java/io/github/abdulmalikalayande/beacon/core/dedup/RedisDeduplicationStoreTest.java`.

## 5. Test layout

- **All of Beacon's own tests live in `beacon-core/src/test`.** `beacon-test` is a separate module for host applications' tests; Beacon's tests never go there.
- Tests mirror the production packages: `config/`, `crypto/`, `dedup/`, `listener/`, `queue/`, `repository/`, `service/`, `template/`.
- The Spring test slices find `BeaconCoreApplication` as their configuration root.

**Which kind of test to write:**

| Kind | When | Example |
|---|---|---|
| Plain unit test | Pure logic with no Spring and no database | `QuietHoursPolicyTest`, `DefaultTemplateEngineTest`, `AesGcmContextEncryptorTest`, `DefaultDeduplicationStoreTest` |
| Unit test with Mockito | A class whose collaborators you want to control | `NotificationServiceImplementationTest` |
| `ApplicationContextRunner` | Bean definitions and their conditions | `BeaconCoreConfigTest` |
| `@JdbcTest` | Code that uses `JdbcTemplate` | `JdbcDeduplicationStoreTest` |
| `@DataJpaTest` | Entities, repositories, and anything that needs transactions | `TransactionalOutboxRepositoryTest`, `DatabaseNotificationQueueTest`, `NotificationIngestionTransactionalTest` |
| `@DataRedisTest` | Code that uses Redis | `RedisDeduplicationStoreTest` |

**The fixed clock.** `config/BeaconCoreTestConfig` provides a `Clock` bean fixed at `2026-05-31T03:00:00Z`, zone `Africa/Lagos`. Import it in any test that needs the time ([ADR 0006](adr/0006-time-from-injected-clock.md)). Some unit tests build their own fixed clock (`2026-07-25T15:00:00Z`, UTC).

## 6. Required adversarial categories

Every component must cover each category that applies to it. Coverage today:

| Category | How to write it | Covered today by |
|---|---|---|
| Concurrency | Start N threads with ready, start and done latches (`CountDownLatch`), so they really run at the same moment; assert exact counts | The same-key 20-thread races in `DefaultDeduplicationStoreTest`, `JdbcDeduplicationStoreTest`, `RedisDeduplicationStoreTest` and `NotificationServiceImplementationTest`; distinct-key races in the JDBC and Redis tests |
| Transactional rollback | Run the code inside a `TransactionTemplate` that throws or is marked rollback-only; assert nothing was written | `NotificationIngestionTransactionalTest` |
| Crash and restart durability | Write work, stop the component, start a new one, and assert the work is still done | Nothing yet (needs the queue poller, item 6) |
| Idempotency under a race | The concurrency pattern, with the same key | The same-key races listed above |
| Provider failure paths | Simulate 429, 503, 400 and timeouts; assert a 429 never trips the circuit breaker | Nothing yet (needs providers, item 7) |
| Time | Use the fixed Clock; test the exact boundary instants | `QuietHoursPolicyTest`, `NotificationIngestionTransactionalTest`, `NotificationServiceImplementationTest` |
| Personal data masking | Trigger a failure with known personal data; assert it appears in no stored reason and no captured log line | Nothing yet (needs the worker, item 8) |

## 7. Rules learned the hard way

Each rule comes from a real failure in this project.

1. **A test that needs real commits uses `@Transactional(propagation = NOT_SUPPORTED)` and clears its tables before and after each test.** Slice tests otherwise wrap each test in a transaction that rolls back, so after-commit behaviour never runs; and committed rows left behind change the results of later tests.
2. **PostgreSQL aborts the whole transaction after any error, so the failing statement must be the last one in a shared transaction.** Any statement after the error fails too, and the test then fails for the wrong reason.
3. **`@DataJpaTest` does not load your configuration classes; import them explicitly** (`@Import`). Otherwise the bean you meant to test is not there.
4. **A test configuration shared by several test classes may hold only beans that every one of those classes can build.** One bean that needs, say, a repository breaks every test class that loads the configuration without one.
5. **Redis has no rollback between tests; flush it in `@BeforeEach`.** Keys left by one test turn the next test's new key into a duplicate.
6. **Check stored data by reading raw rows (`JdbcTemplate.queryForMap`), not through the entity being tested.** Reading back through the same entity can return the cached instance and prove nothing about the database.
7. **Build test timestamps from the fixed Clock.** PostgreSQL stores microseconds and rounds anything finer, so values from `Instant.now()` do not survive the round trip, and truncating in the assertion does not help because PostgreSQL rounds rather than truncates.
8. **Never assert on wall-clock duration; use Awaitility with generous timeouts.** Timing assertions fail on a slow machine or a busy CI runner.
9. **Verify the exact type with Mockito instead of `any()` whenever the type matters.** `any()` also matches the wrong type.
10. **Use `saveAndFlush` when a constraint must fire inside the assertion.** A plain `save` may not reach the database until after the assertion has finished.
11. **Watch for tests that pass for the wrong reason:** empty test bodies, equality checks on entities whose `equals` compares only the ID, the wrong table or key, and loose matchers.

## 8. Known problems in the current tests

Some current tests break these rules, or are likely to fail. They are listed as code findings G-42 to G-50 in [`docs/ROADMAP.md`](ROADMAP.md). Two others affect test results directly: G-02 (a configuration class that breaks `BeaconCoreConfigTest`) and G-03 (a queue test that never clears its table).
