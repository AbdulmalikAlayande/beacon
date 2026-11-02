# 0020. Delivery: suppression first, user looked up again, defined outcomes, no personal data in logs

- Status: Accepted
- Date: 2026-11-02
- Register entries: D-104, D-105, D-106, D-108
- Document status: Approved, last checked against the code on 2026-11-02

## Context

Once a task is claimed, the worker has to deliver it, and several things can go wrong. Unsubscribes must be honoured under laws such as CAN-SPAM and GDPR, and sending to hard-bounced addresses damages the sender's reputation. Contact details can change between the request and the delivery. A send can succeed, fail temporarily or fail for good, and each case needs a clear next state. Failures also produce logs and stored reasons, which must not leak an OTP or a phone number.

## Decision

- **The suppression check comes before sending.** Hard bounces, spam complaints, unsubscribes and admin suppressions block delivery, and the task is marked `SUPPRESSED`.
- **The worker looks up the user again at delivery time.** The destination (phone number, email address or push token) is fetched from `UserPreferenceResolver` when delivering, not stored in the queue.
- **What happens after a send attempt:**
  - Success: the queue row is deleted, and the status record becomes `SENT` with the provider's name. `DELIVERED` arrives later through webhooks (ADR 0021).
  - Temporary failure: `retry_count + 1`, `available_at` moved later with exponential backoff, and the row goes back to `QUEUED`.
  - Permanent failure, or the retry limit reached: the task becomes a dead letter (`FAILED`), with a failure reason that contains no personal data.
- **No personal data in logs or failure reasons**, proven by tests that check stored reasons and captured logs.
- Build order: the suppression check is part of item 8 (the worker); retries and dead letters are item 9.

## Consequences

- A suppressed contact is never sent to, which honours unsubscribes and protects the sender's reputation.
- Contact details never sit in the queue, and the latest values are always used.
- `SENT` means the provider accepted the message, not that the user received it.
- Every queue row eventually leaves the queue: deleted on success, or turned into a dead letter.
- The retry limit and the backoff numbers are not decided (O-23).
- Cross-channel fallback, when every provider for a channel fails, is open (O-15).

## Alternatives rejected

The register records no rejected alternatives for these decisions.

## In the code

Not built yet. What exists:

- `beacon-api/src/main/java/io/github/abdulmalikalayande/beacon/api/port/SuppressionList.java`, `dto/SuppressionEntry.java`, `enums/SuppressionReason.java`
- `beacon-api/src/main/java/io/github/abdulmalikalayande/beacon/api/enums/NotificationStatus.java` (`SENT`, `DELIVERED`, `FAILED`, `SUPPRESSED`)
- `beacon-api/src/main/java/io/github/abdulmalikalayande/beacon/api/exception/SuppressionException.java`
- `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/queue/DatabaseNotificationQueue.java`: `enqueueDead` has an empty body.
- The `retry_count` and `available_at` columns in `notification_queue`, and `provider` and `failure_reason` in `notification_status` (`V1__init_beacon_schema.sql`).
