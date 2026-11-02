# 0011. The notification ID is derived from the idempotency key, and a duplicate key throws

- Status: Accepted
- Date: 2026-11-02
- Register entries: D-054, D-055
- Document status: Approved, last checked against the code on 2026-11-02

## Context

`send()` must return a notification ID immediately, before any channel rows exist, so the database cannot create it. Every channel row of one notification must share that ID. Idempotency also means that the same key always identifies the same notification. Finally, the host needs to know when it has sent the same key twice.

## Decision

- The notification ID is derived from the idempotency key: `uuidFrom(idempotencyKey)` takes the SHA-256 hash of the key and shapes it into a UUID. The same key always gives the same ID.
- A repeated idempotency key throws `DuplicateNotificationException`.

## Consequences

- `send()` can return the ID before anything is written.
- All channel tasks and status rows of one notification carry the same `notification_id`.
- A host that retries with the same key gets an exception it must handle, not a silent success.

## Alternatives rejected

- **A database-generated ID.** Rejected because `send()` must return the ID before any rows exist.
- **Hashing the key together with a timestamp.** Rejected because the ID changed on every call, which defeats idempotency.
- **Silently returning the original response for a duplicate key.** Rejected because a repeated key usually means an upstream retry or a bug in the host, and a silent no-op would hide it. A host that treats a duplicate as success can catch `DuplicateNotificationException` and move on.

## In the code

- `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/service/NotificationServiceImplementation.java` (`uuidFrom`, and `send` calling `markSeen`)
- `beacon-api/src/main/java/io/github/abdulmalikalayande/beacon/api/exception/DuplicateNotificationException.java`
- Tests: `NotificationServiceImplementationTest` (`sameRequest_ProducesSameNotificationId`, `differentRequests_ProduceDifferentNotificationIds`, `duplicateIdempotencyKey_secondSendRejected_noAdditionalPublish`).
- Not yet true: `uuidFrom` marks the UUID as version 4 (random) although it is derived deterministically, and wraps failures in a bare `RuntimeException` (register G-23).
