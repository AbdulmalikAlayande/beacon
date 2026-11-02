# 0004. `beacon-api` is plain Java, split by who implements each contract

- Status: Accepted
- Date: 2026-11-02
- Register entries: D-011, D-012, D-013, D-014
- Document status: Approved, last checked against the code on 2026-11-02

## Context

`beacon-api` holds every public contract. Anything that only needs the types should not have to pull in Spring. The module also mixes two kinds of interface: ones the host implements and ones Beacon implements. An earlier layout put both in a single `spi/` folder, so the folder name did not tell a reader who was responsible for an implementation.

## Decision

- `beacon-api` is plain Java 21. Its only allowed dependency is `jakarta.validation-api` (the specification jar, not an implementation).
- Request records declare their rules with `jakarta.validation` annotations (`@NotBlank`, `@NotNull`, `@Valid`, `List<@NotNull X>`). `beacon-core` runs a real `Validator` (Hibernate Validator) inside `send()` and throws `ConstraintViolationException`.
- Packages:
  - `enums/`, `dto/`, `request/`, `response/`, `exception/`;
  - `service/`: what the host calls (`NotificationService`);
  - `spi/`: what the host implements (`UserPreferenceResolver`, `UserBatchSource`, `NotificationTypeRegistry`, `TemplateResolver`);
  - `port/`: what Beacon implements and a host may swap (`NotificationProvider`, `NotificationQueue`, `DeduplicationStore`, `SuppressionList`, `NotificationStatusTracker`, `TemplateEngine`, `ProviderRateLimiter`, `ProviderCircuitBreaker`, `WebhookHandler`, `ReconciliationJob`).
- Requests and DTOs are records. Collections passed to a request record are copied in its canonical constructor (`List.copyOf`, `Map.copyOf`).

## Consequences

- A Spring annotation can never be added to `beacon-api`; if a change needs one, it belongs in `beacon-core` or the starter.
- Validation runs only where `beacon-core` calls the `Validator`. Today that is `send()`.
- The package name tells a reader who owns an implementation: `spi/` means the host, `port/` means Beacon.
- Because `List.copyOf` and `Map.copyOf` reject null elements, a null channel or context value fails with a `NullPointerException` in the constructor before validation can report it (register G-24).

## Alternatives rejected

- **One flat `spi/` folder for every interface** (the earlier layout). Rejected because the folder name should tell you who owns the implementation.
- **Request records without defensive copies.** Rejected because records are only shallowly immutable. Without copies, a host that changes its list or map after calling `send()` would change what Beacon queues. The copied collections are also safe to hand from the host's thread to the listener.

## In the code

- Packages: `beacon-api/src/main/java/io/github/abdulmalikalayande/beacon/api/`.
- Validation annotations: `beacon-api/src/main/java/io/github/abdulmalikalayande/beacon/api/request/NotificationRequest.java`, `BatchNotificationRequest.java`, `BatchAudience.java`.
- Validation at runtime: `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/service/NotificationServiceImplementation.java` (`send`).
- Not yet true: `beacon-api/pom.xml` depends on `spring-boot-starter` and `hibernate-validator`, `BeaconApiApplication` is a `@SpringBootApplication` class, and `port/NotificationQueue.java` imports Spring's `@Validated` without applying it (register G-01).
