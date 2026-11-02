# 0026. Redis `SETNX` lock per notification as the worker's guard

- Status: Superseded by 0018
- Date: 2026-11-02
- Register entries: register section 12 (superseded decisions), D-073
- Document status: Approved, last checked against the code on 2026-11-02

## Context

The original specification had each worker take a Redis `SETNX` lock, with a TTL, on the notification ID before delivering. If the lock was already held, the worker dropped the task. This was meant to stop two workers that picked up the same message at the same moment from both sending it. The same specification described `FOR UPDATE SKIP LOCKED` as giving the same guarantee when a host does not use Redis.

## Decision

The per-notification Redis lock was the worker's main guard against two workers delivering the same task.

## Consequences

Delivery would have relied on a second locking mechanism for a guarantee the database claim already provides.

## Alternatives rejected

Superseded by claiming rows with `FOR UPDATE SKIP LOCKED` and marking them `PROCESSING` (ADR 0018). Those two already guarantee that only one worker holds a task, with no extra infrastructure.

## In the code

- `DeduplicationStore` still declares `acquireDeliveryLock` and `releaseDeliveryLock`. All three stores implement them as stubs (`acquireDeliveryLock` returns `false`).
- Whether to remove these methods, or keep them for broker-based queues, is open until item 8 (O-08).
