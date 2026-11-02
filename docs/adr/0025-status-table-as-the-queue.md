# 0025. `notification_status` used as the queue

- Status: Superseded by 0008
- Date: 2026-11-02
- Register entries: register section 12 (superseded decisions), D-032, D-033
- Document status: Approved, last checked against the code on 2026-11-02

## Context

The original specification used one table for everything: a task was written to `notification_status` as `QUEUED`, and polling workers picked it up from there. The same table had `UNIQUE (idempotency_key)`.

## Decision

`notification_status` was both the work queue and the permanent history, with one row per idempotency key.

## Consequences

- The table workers poll would grow with every notification ever sent, so polling would slow down over time.
- `UNIQUE (idempotency_key)` fails the moment one request fans out to two channels, because the channel rows share the key.

## Alternatives rejected

Superseded by two tables: `notification_queue`, a small work table whose rows are deleted when finished, and `notification_status`, the permanent history with `UNIQUE (idempotency_key, channel)` (ADR 0008).

## In the code

Not used. `beacon-core/src/main/resources/db/migration/beacon/V1__init_beacon_schema.sql` creates the two separate tables.
