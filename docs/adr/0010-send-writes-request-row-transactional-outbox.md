# 0010. `send()` writes a request row in the host's transaction (transactional outbox)

- Status: Accepted
- Date: 2026-11-02
- Register entries: D-065, D-053
- Document status: Approved, last checked against the code on 2026-11-02

## Context

Beacon promises that a notification exists if and only if the host's work committed. The current design (ADR 0029) keeps the first half of that promise: nothing is queued when the host rolls back. It breaks the second half. The queue rows are written by a listener after the host has committed, so if that listener fails (the database blips, or the application restarts at that moment), the host's work is saved but the notification is gone. Its idempotency key is already claimed, so a retry is rejected as a duplicate (register G-17).

Whatever replaces it must not let Beacon break the host's own transaction: a notification problem must never roll back a payment.

## Decision

- `send()` validates the request, claims the idempotency key (unchanged), then inserts **one request row** in the host's transaction and returns. There is no event and no after-commit listener.
- Without a transaction, the insert simply commits on its own.
- The request row commits if and only if the host's work commits.
- The request row stores:
  - notification ID, idempotency key, user ID, type;
  - priority as sent (it may be null);
  - preferred channels;
  - the encrypted context (ADR 0014);
  - `scheduledAt`;
  - `requestedAt`, the Clock's time inside `send()`.
- Quiet hours and `availableAt` are measured from `scheduledAt` if given, otherwise from `requestedAt`, never from the time of expansion.
- A new migration adds the request table. Proposed name: `notification_request`.
- The queue poller expands request rows into channel tasks (ADR 0018). Expansion does what the listener does today: resolve the user, resolve channels and priority, apply quiet hours, and fan out into channel tasks and status rows. It runs in one transaction with marking the request expanded, and a failure is retried, not lost.
- The order of work in `send()` becomes: validate, claim the key, derive the notification ID (ADR 0011), insert the request row, return `NotificationResponse` with status `CREATED`. The response is an acknowledgement, not "queued", because channel tasks only exist after expansion.

## Consequences

- Nothing can be lost between the host's commit and Beacon's write, which closes G-17.
- Beacon cannot break the host's transaction, because the only work inside it is one insert.
- A failing user lookup is retried during expansion instead of losing the notification.
- Beacon's tables must share the host's database and transaction. There is no separate datasource for Beacon.
- The event, the listener and their tests change. Most of `NotificationIngestionTransactionalTest` checks queue and status rows straight after commit; those rows now exist only after expansion, so the tests must run the expand step first or check the request table. `listenerWaitsForCommit_notForPublish` and `noTransaction_tasksAreQueuedImmediately` are tied to the listener and are rewritten.
- The rollback behaviour of the dedup stores does not change (ADR 0015): the JDBC store's claim rolls back with the host; the in-memory and Redis stores keep the key claimed.
- Still open:
  - how a failing expansion is retried and with what backoff (O-19);
  - what happens to a request whose user is unknown (O-20);
  - whether expanded request rows are deleted or kept (O-21).

## Alternatives rejected

- **Keep the after-commit listener** (option A, ADR 0029). A listener failure after the host commits loses the notification while its key stays claimed.
- **Run the listener before commit, inside the host's transaction** (option B1). The queue rows would commit with the host's work, but any failure in Beacon at that point, including a failure in the host's own `UserPreferenceResolver`, would roll back the host's business transaction because of a notification.

## In the code

Not built yet. The code still implements ADR 0029. This work is part of item 6: the request table, the `send()` change and the expand step come first, followed by claiming, stuck-row recovery and the poller.
