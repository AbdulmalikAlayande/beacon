# 0005. Beans are registered with `@Bean` and conditions, never `@Component`

- Status: Accepted
- Date: 2026-11-02
- Register entries: D-016, D-097
- Document status: Approved, last checked against the code on 2026-11-02

## Context

Beacon's classes live in `io.github.abdulmalikalayande.beacon`. A host's component scanning covers only the host's own packages, so a `@Component` class in Beacon would silently never load. Some ports also have several implementations (for example, more than one `NotificationQueue`); `@Component` cannot be switched off, so they would collide. Finally, some beans need the database, and a test that loads a configuration class without a database breaks if that class declares one of them.

## Decision

- Implementation classes stay plain, with no stereotype annotations.
- They become beans through `@Bean` methods in configuration classes, guarded by `@ConditionalOnMissingBean` so a host can replace them, and by `@ConditionalOnProperty` where several implementations exist (for example `beacon.queue.type`).
- The final home of these configuration classes is the starter's auto-configuration.
- Beans that need the database go in their own configuration class, never in one that database-free tests load. The `DatabaseNotificationQueue` bean lives in `BeaconQueueConfig` in `src/main`, with `@ConditionalOnMissingBean(NotificationQueue.class)`, not in `BeaconCoreConfig`.

## Consequences

- Every implementation needs an explicit `@Bean` method somewhere. Until that method exists, the class is not part of a running application.
- A host replaces any default by declaring its own bean of the same type.
- Tests must import the configuration classes they need (see ADR 0023).

## Alternatives rejected

- **`@Component` on implementation classes.** Rejected because component scanning never reaches Beacon's package in a host, and a `@Component` cannot be switched off, so several implementations of one port would collide.
- **The queue bean in `BeaconCoreConfig`.** Rejected because `BeaconCoreConfigTest` loads `BeaconCoreConfig` without a database, and a bean that needs `DeliveryTaskRepository` breaks it.

## In the code

- `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/config/BeaconCoreConfig.java` (`Clock` bean)
- `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/config/BeaconQueueConfig.java` (queue bean)
- Not yet true:
  - `BeaconCoreConfig` still declares a second `databaseNotificationQueue` bean, and only that copy checks `beacon.queue.type` (register G-02). `BeaconCoreConfigTest` is likely to fail because of it (G-42).
  - No configuration class registers the service, the listener, a dedup store, the encryptor, the type registry or a template engine (G-18).
- Not built yet: the starter's auto-configuration.
