# 0012. Channel resolution and unknown users

- Status: Accepted
- Date: 2026-11-02
- Register entries: D-056, D-057
- Document status: Approved, last checked against the code on 2026-11-02

## Context

One request can reach a user on several channels. Which channels depends on three things: what the host asked for, what the notification type supports, and what the user has enabled. A request can also name a user the host's `UserPreferenceResolver` does not know.

## Decision

- **Channel resolution:**
  - If the host gave `preferredChannels`, start from those. Otherwise, start from the type's supported channels in `NotificationTypeRegistry`.
  - Keep only the channels the user has enabled.
  - If nothing is left, log a warning and queue nothing, without throwing.
  - Explicit channels take precedence over the registry (for example, SMS for `ORDER_CONFIRMATION` when the host asks for it).
- **Unknown user:** if `UserPreferenceResolver.resolve()` returns `null`, log a warning and queue nothing.
- Under ADR 0010 this logic moves from the listener into the expand step, unchanged.

## Consequences

- A user never receives a notification on a channel they have not enabled.
- A host can deliberately send on a channel the type does not list. Explicit channels are not checked against the type's supported channels at all, so an OTP can go out by EMAIL if the host asks for it. Whether to limit this is open (O-17).
- A request with no channel overlap, or for an unknown user, produces nothing and raises no error. The host still gets a `CREATED` response.
- Under ADR 0010, what happens to a request whose user is unknown at expansion time is open again (O-20).

## Alternatives rejected

The register records no rejected alternatives for these decisions.

## In the code

- `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/listener/NotificationRequestedEventListener.java` (`onNotificationRequested`, `resolveEffectiveChannels`)
- `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/defaults/DefaultNotificationTypeRegistry.java`
- Tests in `NotificationIngestionTransactionalTest`: `noExplicitChannels_queuesIntersectionOfTypeAndUser`, `explicitChannels_areIntersectedWithUserChannels`, `explicitChannels_takePrecedenceOverTypeRegistry`, `noChannelOverlap_nothingQueued_noException`, `userWithNoEnabledChannels_nothingQueued`, `unknownUser_nothingQueued_noException`.
- Not yet true: a `null` set of enabled channels throws a `NullPointerException` in `resolveEffectiveChannels` (register G-22).
- Not built yet: the expand step (ADR 0010).
