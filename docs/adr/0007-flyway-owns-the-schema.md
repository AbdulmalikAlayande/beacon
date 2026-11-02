# 0007. Flyway owns the schema, with Beacon's own history table

- Status: Accepted
- Date: 2026-11-02
- Register entries: D-030, D-031
- Document status: Approved, last checked against the code on 2026-11-02

## Context

Beacon creates its own tables in the host's database. The schema has to be defined in one place and changed in a controlled way. The host very likely uses Flyway too, with its own migrations and its own history table. Earlier test logs showed Beacon's migrations being recorded in the default `public.flyway_schema_history`, which is the table a host's Flyway uses.

## Decision

- Flyway owns the schema. Hibernate only validates it: `spring.jpa.hibernate.ddl-auto=validate`.
- Migrations live in `beacon-core/src/main/resources/db/migration/beacon/`. A schema change is made by writing a migration, never through entity annotations alone.
- A migration is never edited once it is merged. Changes go in a new migration.
- Beacon records its migrations in `beacon_flyway_schema_history`, never in the host's `flyway_schema_history`.
  - Short term: `spring.flyway.table` is set in the test properties.
  - Final: the starter runs its own Flyway instance, so the host's Flyway is never touched.

## Consequences

- An entity and its table can only drift apart if someone forgets a migration, and `validate` then fails at startup.
- A merged migration is permanent. Flyway checksums reject an edited migration that has already run, and hosts' databases will already have run it, so a mistake is corrected by a new migration.
- Beacon's migrations and the host's migrations cannot interfere with each other's history.
- Until the starter's own Flyway instance exists, isolation depends on configuration rather than on Beacon itself.

## Alternatives rejected

- **Letting Hibernate generate or update the schema.** Rejected because it would change a host's database in ways nobody reviewed, and a library must never do that. Migrations are versioned and reviewable.
- **Editing a migration after it is merged.** Rejected because Flyway checksums reject an edited migration that has already run, and hosts' databases have already run it.
- **Sharing the host's default history table.** Rejected because Beacon's history must stay isolated from the host's.

## In the code

- `beacon-core/src/main/resources/db/migration/beacon/V1__init_beacon_schema.sql`
- `beacon-core/src/main/resources/db/migration/beacon/V2__create_deduplication_store.sql`
- `beacon-core/src/main/resources/application.properties` (`spring.jpa.hibernate.ddl-auto=validate`)
- `beacon-core/src/test/resources/application-test.properties` (`spring.flyway.table=beacon_flyway_schema_history`, `spring.flyway.locations=classpath:db/migration/beacon`). A test run on 2026-11-02 confirmed that this table is used.
- Not built yet: the starter's own Flyway instance.
