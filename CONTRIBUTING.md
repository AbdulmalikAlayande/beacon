# Contributing to Beacon

- Purpose: how to contribute to Beacon: what to read, how to set up, how to work, and how to open a pull request.
- Document status: Draft, last checked against the code on 2026-11-02

Thank you for helping. Beacon is a library that runs inside other people's applications, so its code is held to a high standard. This page is short on purpose; it links to the documents that explain the details.

## Before you start

1. **Wait to be assigned an issue.** Pick an open issue and ask to work on it in a comment. Start only once the maintainer has assigned it to you. This avoids two people doing the same work, or work that clashes with a design change in progress. Small fixes to documentation and typos can go straight to a pull request.
2. **Do not start work on ingestion yet.** Ingestion (`NotificationService.send()`, the event and its listener) is being redesigned as a transactional outbox in item 6 ([ADR 0010](docs/adr/0010-send-writes-request-row-transactional-outbox.md)). Do not start work that touches it until the request-table change has landed.
3. **Read how Beacon is built.** At minimum, read the ADR index ([`docs/adr/README.md`](docs/adr/README.md)) and the ADRs for the area you are changing.
4. **Check the roadmap.** [`docs/ROADMAP.md`](docs/ROADMAP.md) is the only place that records what is built, what is next, and which code findings are open.

## Setup

You need:

- JDK 21;
- Docker, running (the integration tests start PostgreSQL 18 and Redis 7 with Testcontainers);
- on Windows, Git Bash or cmd.

Clone the repository and run one test class from the repository root:

```bash
./mvnw -pl beacon-core -am test -Dtest=JdbcDeduplicationStoreTest -Dsurefire.failIfNoSpecifiedTests=false
```

If your default JDK is not 21, point `JAVA_HOME` at JDK 21 for the command. [`docs/testing.md`](docs/testing.md) explains every flag, gives the cmd form, and describes the test setup.

## How to work

- **Test first, always.** Write a failing test, make it pass, then refactor. Every behaviour needs a test that fails without your change. See [`docs/testing.md`](docs/testing.md) for the test kinds, the required adversarial categories and the rules that keep tests honest.
- **One purpose per branch and per pull request.** A bug fix does not also refactor unrelated code.
- **Branch names:** `fix/short-description`, `feat/short-description`, `docs/short-description`, `test/short-description`.

## Rules you must not break

Each rule is an accepted decision; the link explains why.

- Time comes from the injected `Clock`. Never call `Instant.now()`, `LocalTime.now()` or the database's `now()` in business logic. ([ADR 0006](docs/adr/0006-time-from-injected-clock.md))
- `beacon-api` has no Spring dependency. If a change needs a Spring annotation, it belongs in `beacon-core` or the starter. ([ADR 0004](docs/adr/0004-api-plain-java-split-by-implementer.md))
- Modules depend in one direction only: `beacon-core` on `beacon-api`, the starter on `beacon-core`. ([ADR 0003](docs/adr/0003-four-modules-dependency-direction.md))
- Beans are registered with `@Bean` methods and conditions, never with `@Component`. ([ADR 0005](docs/adr/0005-beans-registered-with-bean-never-component.md))
- Flyway owns the schema. Change it with a new migration in `beacon-core/src/main/resources/db/migration/beacon/`; never edit a migration that has been merged. ([ADR 0007](docs/adr/0007-flyway-owns-the-schema.md))
- Entities hold data only: no lifecycle callbacks, enums stored as strings, `equals` and `hashCode` on the ID only, IDs created in code. ([ADR 0009](docs/adr/0009-entity-rules.md))
- `DeliveryTask` is converted to `DeliveryTaskEntity` only inside `DatabaseNotificationQueue`. ([ADR 0017](docs/adr/0017-delivery-task-is-the-queue-contract.md))
- Beacon never reads the host's tables; user data comes only through `UserPreferenceResolver`. ([ADR 0002](docs/adr/0002-host-owns-user-data.md))
- No personal data in logs or stored failure reasons. ([ADR 0020](docs/adr/0020-delivery-suppression-outcomes-no-personal-data.md))
- Public types in `beacon-api` are contracts that hosts depend on. Adding is fine; removing or renaming needs a discussion first.

If your change seems to need breaking one of these rules, open an issue and discuss it before writing code.

## Pull requests

The pull request description must say:

- what problem it solves, and the issue it closes;
- which tests you added or changed, and what they prove;
- any decision you made that a reviewer would not guess from the code.

Every pull request is reviewed before it is merged, and the tests must pass. Expect review comments to be specific; a requested change is needed, not optional.

## Questions

Ask in the issue you are working on, or open a new issue with the `question` label.
