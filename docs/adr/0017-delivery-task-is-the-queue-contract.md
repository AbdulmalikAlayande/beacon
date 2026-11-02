# 0017. `DeliveryTask` is the queue contract; only `DatabaseNotificationQueue` sees the entity

- Status: Accepted
- Date: 2026-11-02
- Register entries: D-090, D-091, D-092
- Document status: Approved, last checked against the code on 2026-11-02

## Context

The default queue is a database table, but a host may replace it with Kafka, RabbitMQ or Pulsar. A Kafka message has no table row, so the contract every queue accepts cannot be a JPA entity. The contract also has to carry exactly what a worker needs, and nothing that only one queue implementation cares about.

## Decision

- `DeliveryTask` (a record in `beacon-api/dto`) is what the library passes around and what every queue implementation accepts.
- `DeliveryTaskEntity` (in `beacon-core/entity`) is a row of `notification_queue`. Only `DatabaseNotificationQueue` converts between the two.
- Whoever builds tasks (the listener today, the expand step under ADR 0010) builds `DeliveryTask`s and never touches the entity or its repository.
- `DeliveryTask` carries: `UUID taskId`, `UUID notificationId`, idempotency key, user ID, channel, type, priority, encrypted context, retry count, `createdAt`, `availableAt`.
- It does not carry `status` or `updated_at`; those are bookkeeping for the database queue only.
- `enqueue` returns nothing and throws on failure.

## Consequences

- Any queue implementation can be swapped in without touching the code that builds tasks.
- The database queue's bookkeeping columns stay private to it.
- A failed `enqueue` throws, which rolls back the transaction that is writing the tasks; a failure cannot be silently ignored.
- `enqueueDelayed` duplicates what `availableAt` already expresses. Its removal is agreed but it stays until removed (O-03).

## Alternatives rejected

- **A JPA entity as the shared contract.** Rejected because a broker message has no table row.
- **The listener saving through the repository directly.** Rejected because it made queue implementations impossible to swap.
- **`scheduledAt` on the task.** Rejected because the host's schedule is a wish set once, while `availableAt` is when the task may be picked up, which quiet hours and retries also move. The queue needs only `availableAt`.
- **`enqueue` returning a boolean.** Rejected because the caller already has the task and its ID, and a boolean can be ignored.

## In the code

- `beacon-api/src/main/java/io/github/abdulmalikalayande/beacon/api/dto/DeliveryTask.java`
- `beacon-api/src/main/java/io/github/abdulmalikalayande/beacon/api/port/NotificationQueue.java`
- `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/queue/DatabaseNotificationQueue.java` (`enqueue` converts the task to an entity and sets `status` to `QUEUED`)
- `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/entity/DeliveryTaskEntity.java`
- Tests: `DatabaseNotificationQueueTest` (`putIn_oneTask_savesOneRowWithAllFields`, `putIn_severalTasks_savesAll`, `putIn_null_rejectedClearly`); `NotificationIngestionTransactionalTest.queuedTask_carriesEveryField`.
- Not yet true: `enqueueDelayed` and `enqueueDead` have empty bodies.
- Not built yet: converting an entity back into a `DeliveryTask` (needed when claiming, ADR 0018).
