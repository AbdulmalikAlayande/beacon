# 0002. The host owns user data; Beacon reaches it only through the SPI

- Status: Accepted
- Date: 2026-11-02
- Register entries: D-003
- Document status: Approved, last checked against the code on 2026-11-02

## Context

To send a notification, Beacon needs to know things about the recipient: their email address, phone number and push token, which channels they have enabled, their timezone and their quiet hours. That data lives in the host application, in tables Beacon knows nothing about. A library cannot assume anything about the host's schema.

## Decision

All user data reaches Beacon through the `UserPreferenceResolver` interface, which the host implements. `resolve(userId)` returns a `NotificationPreference` (contact details, enabled channels, timezone, quiet hours), or `null` when the user is unknown. Beacon never queries the host's user tables, and that interface is the only integration boundary for user data.

## Consequences

- The host must implement one interface before Beacon can do anything. It is the only interface a host is required to implement.
- Beacon's own tables hold no contact details. `notification_queue` and `notification_status` store only `user_id`.
- The host decides what "unknown user" means. Beacon treats a `null` result as "skip quietly" (see ADR 0012).
- The speed and correctness of Beacon's lookups depend on the host's implementation.

## Alternatives rejected

The register records no rejected alternative for this decision. The old specification described a `user_preferences` table owned by Beacon; that design contradicts this decision and is not used.

## In the code

- `beacon-api/src/main/java/io/github/abdulmalikalayande/beacon/api/spi/UserPreferenceResolver.java`
- `beacon-api/src/main/java/io/github/abdulmalikalayande/beacon/api/dto/NotificationPreference.java`
- `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/listener/NotificationRequestedEventListener.java` (calls `resolve` before building tasks)
- `beacon-core/src/main/resources/db/migration/beacon/V1__init_beacon_schema.sql` (no contact columns)
