# 0008. Two tables: a small work queue and a permanent status history

- Status: Accepted
- Date: 2026-11-02
- Register entries: D-032, D-033, D-034, D-058
- Document status: Approved, last checked against the code on 2026-11-02

## Context

Beacon needs two different things from its database. Workers need a table of pending work that they can poll quickly, many times a second, even after millions of notifications have been sent. Support teams and hosts need a permanent record of what happened to every notification. One request also fans out to several channels that share one idempotency key, so any uniqueness rule has to allow that.

## Decision

- **`notification_queue`** (entity `DeliveryTaskEntity`) is the work table: one row per channel task. A row is deleted when its task reaches a final state, so the table stays small and fast to poll.
- **`notification_status`** (entity `NotificationStatusEntity`) is the permanent, append-mostly history of every notification per channel.
- `notification_status` has `UNIQUE (idempotency_key, channel)`. The same key on a different channel is a legitimate fan-out; the same key on the same channel is a duplicate.
- Polling uses a partial index: `idx_notification_queue_poll ON notification_queue (available_at) WHERE status = 'QUEUED'`.
- The listener writes the status record, not the queue: for each task it calls `notificationQueue.enqueue(task)` and then saves a `QUEUED` row to `notification_status`.
- The work table is called a "queue", not an "outbox", for clarity, and `DeliveryTaskEntity` mirrors the `DeliveryTask` DTO.

## Consequences

- Polling stays fast because the work table holds only unfinished tasks.
- The status history survives the queue rows being deleted.
- The audit trail does not depend on which queue implementation is used. If a Kafka queue replaced the database queue, the status record would still be written.
- If claiming is ordered by priority (O-02), the poll index may need `priority` as well.

## Alternatives rejected

- **Using `notification_status` itself as the queue** (the old specification). Rejected because the work table must stay small while the history must be permanent (ADR 0025).
- **`UNIQUE (idempotency_key)` on the status table.** Rejected because it fails the moment a user has two channels enabled.
- **Writing the status record inside `enqueue`.** Rejected because the audit trail is not part of the queue; it would vanish if a broker replaced the database queue.

## In the code

- `beacon-core/src/main/resources/db/migration/beacon/V1__init_beacon_schema.sql` (both tables, the unique constraint, the poll index)
- `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/entity/DeliveryTaskEntity.java`
- `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/entity/NotificationStatusEntity.java`
- `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/listener/NotificationRequestedEventListener.java` (enqueue, then save the status row)
- Not built yet: deleting queue rows when a task reaches a final state.
- Not yet true: the V1 comment and two test class names still say "outbox" (register G-34).
