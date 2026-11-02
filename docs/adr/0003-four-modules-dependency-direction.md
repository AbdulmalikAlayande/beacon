# 0003. Four modules with a fixed dependency direction

- Status: Accepted
- Date: 2026-11-02
- Register entries: D-010, D-015, D-017, D-018, D-019
- Document status: Approved, last checked against the code on 2026-11-02

## Context

Beacon has three kinds of consumer: code that only needs the contracts (types, interfaces, exceptions), host applications that want everything wired automatically, and host test suites that want fixtures. Each should depend on as little as possible. Some parts also bring heavy dependencies: webhook security needs Spring Security, and webhooks need a web stack.

## Decision

- Four Maven modules:
  - `beacon-api`: every public contract (enums, DTOs, requests, responses, exceptions, interfaces).
  - `beacon-core`: the implementations.
  - `beacon-spring-boot-starter`: auto-configuration, properties and webhook security wiring.
  - `beacon-test`: test fixtures for host applications. Beacon's own tests live in `beacon-core/src/test`, not here.
- Dependency direction: `beacon-core` depends on `beacon-api`; the starter depends on `beacon-core`; `beacon-test` depends on `beacon-api` and `beacon-core`. Nothing depends on the starter except host applications.
- Coordinates: groupId `io.github.abdulmalikalayande` for every module; base package `io.github.abdulmalikalayande.beacon`.
- The library modules are plain jars. They contain no Spring Initializr `@SpringBootApplication` classes and no `application.properties`, and they do not use `spring-boot-maven-plugin`. `BeaconCoreApplication` moves to `beacon-core/src/test`, where the test slices use it as their configuration root.
- `NotificationWebhookSecurityConfigurer` belongs in the starter, never in `beacon-api`.
- `spring-boot-starter-webmvc` is not a dependency of `beacon-core`. It is added to the starter when webhooks are built.

## Consequences

- A host depends on the starter only; everything else arrives transitively.
- `beacon-api` stays light enough to depend on anywhere (see ADR 0004).
- Anything that needs Spring Security or a web stack can only live in the starter.
- Beacon's own tests never go into `beacon-test`.

## Alternatives rejected

- **`NotificationWebhookSecurityConfigurer` in `beacon-api`.** Rejected because it depends on Spring Security (ADR 0028).
- **Keeping `spring-boot-starter-webmvc` in `beacon-core`.** Rejected because `beacon-core` has no web code and the dependency drags a web stack into every host.
- **Keeping the Initializr classes and `spring-boot-maven-plugin` in the library modules.** Rejected because the plugin repackages each jar as an executable application, which breaks it as a dependency, and `beacon-core`'s `application.properties` would land on every host's classpath.

## In the code

- `beacon-core` depends on `beacon-api`: `beacon-core/pom.xml`.
- Not yet true:
  - The starter does not depend on `beacon-core`, and `beacon-test` depends on no Beacon module (register G-37).
  - `beacon-api` and the starter use groupId `io.github.abdulmalikalayande.beacon` (G-19).
  - All four modules still carry an Initializr class, an `application.properties` and `spring-boot-maven-plugin` (G-12, G-40).
  - `beacon-core` still depends on `spring-boot-starter-webmvc` (G-11).
- Not built yet: the starter's auto-configuration and `NotificationWebhookSecurityConfigurer`; the `beacon-test` fixtures.
