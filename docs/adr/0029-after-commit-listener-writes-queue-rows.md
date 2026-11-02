# 0029. `send()` publishes an event and an after-commit listener writes the queue rows

- Status: Superseded by 0010
- Date: 2026-11-02
- Register entries: D-050, D-051, D-052
- Document status: Approved, last checked against the code on 2026-11-02

**This is still what the code does** until ADR 0010 is built (item 6).

## Context

Beacon runs inside the host's transaction. If the host's transaction rolls back (a payment fails), no notification may exist for it. Writing to the queue directly in `send()` would leave a receipt for a payment that never happened.

## Decision

- `send()` publishes `NotificationRequestedEvent` and returns.
- `NotificationRequestedEventListener` handles the event with `@TransactionalEventListener(phase = AFTER_COMMIT)` and `@Transactional(propagation = REQUIRES_NEW)`. It looks up the user, resolves channels, and writes the channel tasks and status rows.
- `REQUIRES_NEW` is needed because, in the after-commit phase, the host's finished transaction is still attached to the thread; a plain `@Transactional` would join it, and its writes would never be saved.
- `fallbackExecution = true` makes `send()` work outside a transaction. Without it, `@TransactionalEventListener` silently drops the event when no transaction is active, and the host gets a success response for a notification that never exists.
- The event is a plain `record` in `beacon-core`, not an `ApplicationEvent`, with its own copy of the request's fields, built by `NotificationRequestedEvent.from(notificationId, request)`. Modern Spring publishes any object, the event is internal so it stays out of `beacon-api`, and its own fields let the internal contract change without touching the public request.

## Consequences

- Nothing is queued when the host rolls back.
- Because the queue rows are written after the host commits, a failure in the listener (the database blips, or the application restarts at that moment) loses the notification while its idempotency key stays claimed, so a retry is rejected (register G-17). Until ADR 0010 is built, "no missed sends" holds only from the moment the queue rows are committed.
- Without a transaction, the listener runs inline inside `send()`, so its exceptions reach the caller.

## Alternatives rejected

At the time:

- **Writing directly in `send()`.** Rejected because it would leave a receipt for a payment that rolled back.
- **Extending `ApplicationEvent`.** Rejected; modern Spring publishes any object.
- **An empty `BeaconEvent` base class.** Rejected because records cannot extend classes, and the base held nothing.
- **Wrapping the raw `NotificationRequest` in the event.** Rejected because it coupled the internal event to the public API.
- **The name `NotificationAcceptedEvent`.** Considered, not adopted.

Superseded by ADR 0010 on 2026-11-02, because this design cannot close G-17.

## In the code

- `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/service/NotificationServiceImplementation.java` (`send` publishes the event)
- `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/event/NotificationRequestedEvent.java`
- `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/listener/NotificationRequestedEventListener.java`
- Tests: `NotificationIngestionTransactionalTest` (`hostTransactionCommits_tasksAreQueued`, `listenerWaitsForCommit_notForPublish`, `hostTransactionThrows_nothingIsQueued`, `hostTransactionMarkedRollbackOnly_nothingIsQueued`, `noTransaction_tasksAreQueuedImmediately`).
