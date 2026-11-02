# 0027. Rendering templates before queuing

- Status: Superseded by 0016
- Date: 2026-11-02
- Register entries: register section 12 (superseded decisions), D-081
- Document status: Approved, last checked against the code on 2026-11-02

## Context

An earlier approach rendered each notification's template when the task was created, and queued the finished subject, title and body.

## Decision

Tasks carried rendered content instead of the context needed to render it.

## Consequences

Rendered bodies, especially HTML email, multiplied across a large batch would bloat the queue.

## Alternatives rejected

Superseded by rendering at the worker, just before sending; only the encrypted context is queued (ADR 0016, ADR 0014).

## In the code

Not used. `notification_queue` stores only `encrypted_context` (`beacon-core/src/main/resources/db/migration/beacon/V1__init_beacon_schema.sql`).
