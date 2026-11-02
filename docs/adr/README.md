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

ADRs 0010 to 0028 are being written and will be added to this table as they are approved.
