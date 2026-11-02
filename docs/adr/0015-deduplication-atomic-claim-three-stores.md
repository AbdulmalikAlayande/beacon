# 0015. Deduplication claims a key in one atomic step, with three stores

- Status: Accepted
- Date: 2026-11-02
- Register entries: D-039, D-070, D-071, D-072
- Document status: Approved, last checked against the code on 2026-11-02

## Context

The same trigger can reach `send()` twice: a billing service fires the same webhook twice, or two requests race. Deduplication has to happen in `send()`, before the channels are known, so the per-channel unique constraint on `notification_status` cannot do this job. A check that reads first and writes second lets two simultaneous calls both pass. Hosts also differ: some want a durable store on PostgreSQL, some already run Redis, and tests want something with no infrastructure.

## Decision

- **One atomic claim.** `DeduplicationStore.markSeen` checks and claims the key in one atomic operation and throws `DuplicateNotificationException` if the key was already claimed. `isSeen` is a read-only query for status checks and is never used to make the dedup decision.
- **A separate table** for the JDBC store: `deduplication_store`, with `UNIQUE (idempotency_key)`.
- **Three stores:**
  - **In-memory** (`DefaultDeduplicationStore`): `ConcurrentHashMap.putIfAbsent`. Bounded at 100,000 keys with a one-hour retention window; when full, expired keys are removed first, then the oldest. Best effort only, and lost on restart. Its eviction is deliberately not polished further.
  - **JDBC** (`JdbcDeduplicationStore`): inserts into `deduplication_store`, and the unique constraint is the check. `DuplicateKeyException` becomes `DuplicateNotificationException`; other database errors become `NotificationException`.
  - **Redis** (`RedisDeduplicationStore`): `SET key value NX` with a TTL (one hour by default) under the namespace `beacon:dedup:`. Redis expires keys itself.
- **Default:** the JDBC store, with a property to select another.
- **Rollback:** the JDBC store's insert joins the host's transaction, so its claim rolls back with the host and the host can retry the same key. The in-memory and Redis stores are outside the transaction, so after a rollback the key stays claimed and a retry is rejected. This is a documented limitation of those two stores, with no compensation.

## Consequences

- Two simultaneous requests with the same key can never both pass.
- Only the JDBC store gives a correct retry after a host rollback. A host that chooses the in-memory or Redis store accepts that limitation.
- Nothing yet deletes old rows from `deduplication_store`, so it grows forever. Retention is open (O-07); the `created_at` index already exists for a cleanup job.
- `acquireDeliveryLock` and `releaseDeliveryLock` remain on the port as stubs. Whether they stay is open until item 8 (O-08).

## Alternatives rejected

- **Read first, then write.** Rejected because two simultaneous calls can both pass the read.
- **`isDuplicate` on the port.** Removed because it did the same as `isSeen`.
- **Using the per-channel constraint on `notification_status`.** Rejected because deduplication happens before the channels are known.
- **Compensating for a rollback in the in-memory and Redis stores** (releasing the claim). Not adopted; the limitation is documented instead.

## In the code

- `beacon-api/src/main/java/io/github/abdulmalikalayande/beacon/api/port/DeduplicationStore.java`
- `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/dedup/DefaultDeduplicationStore.java`
- `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/dedup/JdbcDeduplicationStore.java`
- `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/dedup/RedisDeduplicationStore.java`
- `beacon-core/src/main/resources/db/migration/beacon/V2__create_deduplication_store.sql`
- Tests: `DefaultDeduplicationStoreTest`, `JdbcDeduplicationStoreTest`, `RedisDeduplicationStoreTest` (each with a 20-thread same-key race); `NotificationIngestionTransactionalTest.rolledBackSend_releasesDedupClaim_retrySucceeds`.
- Not yet true:
  - The in-memory store enforces its retention only when full, and `isSeen` ignores expiry (register G-21).
  - `deduplication_store` gets its ID and `created_at` from the database (G-29).
  - The JDBC store logs a normal duplicate at ERROR with a stack trace (G-10).
  - No test documents the in-memory and Redis rollback limitation (G-50).
- Not built yet: the default-store wiring and its selection property.
