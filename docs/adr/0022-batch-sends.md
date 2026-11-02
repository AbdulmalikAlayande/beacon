# 0022. Batch sends

- Status: Accepted
- Date: 2026-11-02
- Register entries: D-110
- Document status: Approved, last checked against the code on 2026-11-02

## Context

Besides notifications triggered by one user's action, hosts need to send one message to a large audience: a promotion to every active user, or an announcement to a list of users. Loading every user into memory at once is not an option for a large audience.

## Decision

- `NotificationService.sendBatch` takes a `BatchNotificationRequest`, whose `BatchAudience` names the recipients.
- The host's `UserBatchSource` is the source of users, read in pages rather than all at once.
- Build order (proposed, not final): batch sends are item 13.

## Consequences

- A host can reach a large audience without building its own loop around `send()`.
- The host decides who is in the audience; Beacon never reads the host's user tables (ADR 0002).
- How a batch request fits the request-row design of ADR 0010 is not decided yet (O-22).

## Alternatives rejected

The register records no rejected alternatives.

## In the code

- Contracts: `beacon-api/src/main/java/io/github/abdulmalikalayande/beacon/api/request/BatchNotificationRequest.java`, `request/BatchAudience.java`, `enums/AudienceType.java`, `spi/UserBatchSource.java`, `service/NotificationService.java` (`sendBatch`).
- Not built yet: `NotificationServiceImplementation.sendBatch` returns `null` (register G-08).
