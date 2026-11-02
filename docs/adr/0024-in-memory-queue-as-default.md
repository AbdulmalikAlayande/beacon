# 0024. In-memory queue as the default

- Status: Superseded by 0001 and 0008
- Date: 2026-11-02
- Register entries: register section 12 (superseded decisions)
- Document status: Approved, last checked against the code on 2026-11-02

## Context

An earlier design made an in-memory queue the default (`QueueType.IN_MEMORY`).

## Decision

The default queue was an in-memory structure inside the host's process.

## Consequences

Anything waiting in memory is lost when the application stops or crashes. That breaks "no missed sends", the promise Beacon exists to keep.

## Alternatives rejected

Superseded by a database-backed queue on the host's own PostgreSQL: still no extra infrastructure, but durable (ADR 0001 and ADR 0008).

## In the code

- No in-memory queue implementation exists.
- `QueueType.IN_MEMORY` is still a value of `beacon-api/src/main/java/io/github/abdulmalikalayande/beacon/api/enums/QueueType.java`.
