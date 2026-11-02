# 0018. The database queue is claimed in short transactions with `SKIP LOCKED`, and stuck rows are recovered

- Status: Accepted
- Date: 2026-11-02
- Register entries: D-093, D-094, D-095, D-096, D-065 (the expand step)
- Document status: Approved, last checked against the code on 2026-11-02

## Context

Hosts run several instances of their application. Each instance polls Beacon's tables, and no two may take the same work. Delivering a task means calling a provider, which can be slow, so no database transaction may stay open while that happens. A server can also die after taking work, and a task must not stay stuck forever because of it. Brokers work differently: they push messages to consumers rather than letting consumers claim rows.

## Decision

- **Claiming belongs to the database queue, not the port.** `claim(batchSize)` and `recoverStuck(timeout)` are methods of `DatabaseNotificationQueue`, not of `NotificationQueue`. What every queue shares on the way out is a small handler interface ("here is a task to deliver"), which the queue poller calls and the worker implements. This document calls it "the delivery handler"; its name is chosen when it is built.
- **A claim is a short transaction.** In one short transaction:
  1. select up to N ready rows (`status = 'QUEUED' AND available_at <= :now`) with `FOR UPDATE SKIP LOCKED`;
  2. set them to `PROCESSING`, with `updated_at` from the Clock;
  3. commit;
  4. then hand them to the handler.

  `:now` comes from the Clock, never from the database's `now()`.
- **Stuck rows are recovered.** Rows that have been `PROCESSING` for longer than a timeout (judged by `updated_at` and the Clock) go back to `QUEUED` with `retry_count + 1`.
- **Each poller tick runs three steps, in this order, each in its own short transaction:**
  1. recover stuck rows;
  2. expand request rows (ADR 0010): claim request rows with `FOR UPDATE SKIP LOCKED`, then expand each into channel tasks and status rows in one transaction with marking the request expanded; a failed expansion is retried;
  3. claim tasks and hand each one to the delivery handler.
- **The queue poller never dies.** One task failing does not stop the others, and no exception escapes the tick. The poller can be stopped, and claims nothing once stopped.
- In prose the component is called the "queue poller". Its class name is decided when it is built.

## Consequences

- Several application instances can poll at once without waiting for each other or claiming the same row.
- No transaction stays open during a slow provider call; the `PROCESSING` status keeps a row claimed after its lock is released.
- A server that dies after claiming leaves its tasks `PROCESSING` only until the timeout. Counting each recovery in `retry_count` lets the retry limit (item 9) stop a task that crashes the server every time.
- Because expansion runs before claiming, a request expanded in a tick can be claimed in the same tick, so an OTP is not delayed by an extra poll interval. A busy tick spends time expanding before it delivers anything; that is acceptable at Beacon's scale, and the batch sizes in O-02 cover it.
- A broker-based queue needs no claim or recovery code; it only calls the same delivery handler.
- Still open (O-02, to be decided during item 6):
  - whether HIGH is claimed before MEDIUM before LOW, or strictly oldest first (if by priority, the poll index may need `priority`);
  - the stuck-row timeout, and whether a row exactly at the timeout counts as stuck;
  - the default batch size and poll interval;
  - the scheduling mechanism (a `ScheduledExecutorService` owned by the poller, or a Spring `TaskScheduler`).
- Also open: retry and backoff for a failing expansion (O-19).

## Alternatives rejected

- **Claiming on the `NotificationQueue` port.** Rejected because claiming is a database idea; brokers push messages instead.
- **Holding the row lock until delivery finishes.** Rejected because a transaction would stay open during a slow provider call.
- **Letting an exception escape the poller's tick.** Rejected because, in Java, an exception that escapes a scheduled task cancels that schedule silently and permanently.
- **A Redis `SETNX` lock per notification as the worker's guard.** Superseded by this decision, because `SKIP LOCKED` and the `PROCESSING` status already guarantee that only one worker holds a task (ADR 0026).

## In the code

Not built yet. What exists:

- The poll index `idx_notification_queue_poll` in `beacon-core/src/main/resources/db/migration/beacon/V1__init_beacon_schema.sql`.
- Test plans with empty bodies:
  - `beacon-core/src/test/java/io/github/abdulmalikalayande/beacon/core/queue/DatabaseNotificationQueueTest.java`: 24 stubs for claiming, concurrency and recovery;
  - `beacon-core/src/test/java/io/github/abdulmalikalayande/beacon/core/queue/OutboxPollerTest.java`: 8 stubs for the poller.
