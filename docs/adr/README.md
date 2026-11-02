# Architecture decision records

- Purpose: an index of every architecture decision record (ADR) for Beacon, with its status and the decision register entries it covers.
- Document status: Draft, last checked against the code on 2026-11-02

Each ADR records one decision: its context, what was decided, its consequences, the alternatives that were rejected, and where it is visible in the code. The source for every ADR is the decision register, [`docs/archive/decision-register.md`](../archive/decision-register.md).

| No. | Title | Status | Register entries |
|---|---|---|---|
| [0001](0001-embedded-starter-no-required-infrastructure.md) | Beacon is an embedded starter with no required infrastructure | Accepted | D-001, D-002, D-004, D-005 |
| [0002](0002-host-owns-user-data.md) | The host owns user data; Beacon reaches it only through the SPI | Accepted | D-003 |
| [0003](0003-four-modules-dependency-direction.md) | Four modules with a fixed dependency direction | Accepted | D-010, D-015, D-017, D-018, D-019 |
| [0004](0004-api-plain-java-split-by-implementer.md) | `beacon-api` is plain Java, split by who implements each contract | Accepted | D-011, D-012, D-013, D-014 |
| [0005](0005-beans-registered-with-bean-never-component.md) | Beans are registered with `@Bean` and conditions, never `@Component` | Accepted | D-016, D-097 |
| [0006](0006-time-from-injected-clock.md) | Time comes from an injected `Clock`, stored as `Instant` and `timestamptz` | Accepted | D-020, D-021 |
| [0007](0007-flyway-owns-the-schema.md) | Flyway owns the schema, with Beacon's own history table | Accepted | D-030, D-031 |
| [0008](0008-two-tables-queue-and-status-history.md) | Two tables: a small work queue and a permanent status history | Accepted | D-032, D-033, D-034, D-058 |
| [0009](0009-entity-rules.md) | Entities hold data only, with string enums, ID-only equality and IDs created in code | Accepted | D-035, D-036, D-037, D-038 |
| [0010](0010-send-writes-request-row-transactional-outbox.md) | `send()` writes a request row in the host's transaction (transactional outbox) | Accepted (not built yet) | D-065, D-053 |
| [0011](0011-notification-id-from-idempotency-key.md) | The notification ID is derived from the idempotency key, and a duplicate key throws | Accepted | D-054, D-055 |
| [0012](0012-channel-resolution-and-unknown-users.md) | Channel resolution and unknown users | Accepted | D-056, D-057 |
| [0013](0013-priorities-type-registry-quiet-hours.md) | Priorities, the default type registry, and quiet hours | Accepted | D-059, D-060, D-061, D-062, D-063 |
| [0014](0014-context-encrypted-while-queued.md) | The template context is encrypted while queued | Accepted | D-064 |
| [0015](0015-deduplication-atomic-claim-three-stores.md) | Deduplication claims a key in one atomic step, with three stores | Accepted | D-039, D-070, D-071, D-072 |
| [0016](0016-simple-template-engine-render-at-worker.md) | Beacon's own simple template engine, rendering at the worker | Accepted | D-080, D-081 |
| [0017](0017-delivery-task-is-the-queue-contract.md) | `DeliveryTask` is the queue contract; only `DatabaseNotificationQueue` sees the entity | Accepted | D-090, D-091, D-092 |
| [0018](0018-claiming-the-database-queue.md) | The database queue is claimed in short transactions with `SKIP LOCKED`, and stuck rows are recovered | Accepted (not built yet) | D-093, D-094, D-095, D-096, D-065 |
| [0019](0019-providers-classification-rate-limits-circuit-breakers.md) | Providers behind one interface, with error classification, rate limiting and circuit breakers | Accepted (not built yet) | D-100, D-101, D-102, D-103 |
| [0020](0020-delivery-suppression-outcomes-no-personal-data.md) | Delivery: suppression first, user looked up again, defined outcomes, no personal data in logs | Accepted (not built yet) | D-104, D-105, D-106, D-108 |
| [0021](0021-webhooks-hmac-and-daily-reconciliation.md) | Webhooks are verified by HMAC; reconciliation runs daily | Accepted (not built yet) | D-107, D-109 |
| [0022](0022-batch-sends.md) | Batch sends | Accepted (not built yet) | D-110 |
| [0023](0023-strict-tdd-and-real-infrastructure.md) | Testing uses strict TDD and real infrastructure | Accepted | D-120, D-121, D-122, D-123, D-124, D-125 |
| [0024](0024-in-memory-queue-as-default.md) | In-memory queue as the default | Superseded by 0001 and 0008 | Register section 12 |
| [0025](0025-status-table-as-the-queue.md) | `notification_status` used as the queue | Superseded by 0008 | Register section 12, D-032, D-033 |
| [0026](0026-redis-setnx-worker-lock.md) | Redis `SETNX` lock per notification as the worker's guard | Superseded by 0018 | Register section 12, D-073 |
| [0027](0027-render-before-queuing.md) | Rendering templates before queuing | Superseded by 0016 | Register section 12, D-081 |
| [0028](0028-webhook-security-configurer-in-api.md) | `NotificationWebhookSecurityConfigurer` in `beacon-api` | Superseded by 0003 | Register section 12, D-015 |
| [0029](0029-after-commit-listener-writes-queue-rows.md) | `send()` publishes an event and an after-commit listener writes the queue rows | Superseded by 0010 (still in the code) | D-050, D-051, D-052 |
