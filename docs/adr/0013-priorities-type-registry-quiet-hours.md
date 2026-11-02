# 0013. Priorities, the default type registry, and quiet hours

- Status: Accepted
- Date: 2026-11-02
- Register entries: D-059, D-060, D-061, D-062, D-063
- Document status: Approved, last checked against the code on 2026-11-02

## Context

Not every notification is equally urgent. An OTP has to arrive now; a promotion can wait until morning, and sending one at 2 AM is a good way to lose a user. Hosts should not have to set a priority on every request, and the `priority` column is `NOT NULL`, so a request without one must still get one. Before the fix in D-059, a request without priority failed after the host had committed, and the notification was lost.

## Decision

- **Default priority:** if the request has no priority, it comes from the type registry: `priority = request.priority() ?? registry.getPriority(type)`.
- **What each priority means:**
  - HIGH ignores quiet hours.
  - MEDIUM ignores quiet hours, because payment receipts and order confirmations are expected the moment the user acts. It differs from HIGH only in claim order, which is still open (O-02).
  - LOW waits for quiet hours to end.
- **When a task becomes available:** start from `scheduledAt` if the host gave one, otherwise from the time of the request. Then apply quiet hours. The result is the task's `availableAt`. Under ADR 0010, "the time of the request" is the request row's `requestedAt`, never the time of expansion.
- **Quiet hours** are implemented as a separate `QuietHoursPolicy` class:
  - only LOW priority is held;
  - the window is read in the user's timezone, includes its start and excludes its end, and may wrap past midnight;
  - a window whose start equals its end means no quiet hours;
  - a missing or unknown timezone is treated as UTC rather than failing the send;
  - a task inside the window becomes available when the window ends.
- **Default type registry:**

| Type | Priority | Supported channels |
|---|---|---|
| `OTP` | HIGH | SMS, PUSH |
| `SECURITY_ALERT` | HIGH | PUSH, EMAIL, SMS |
| `SYSTEM_ALERT` | HIGH | PUSH, EMAIL, SMS |
| `PAYMENT_FAILED` | HIGH | PUSH, EMAIL, SMS |
| `PAYMENT_RECEIPT` | MEDIUM | EMAIL, PUSH |
| `ORDER_CONFIRMATION` | MEDIUM | EMAIL, PUSH |
| `PROMO` | LOW | PUSH, EMAIL |
| `ENGAGEMENT` | LOW | PUSH, EMAIL |
| any other (for example `CUSTOM`) | MEDIUM | EMAIL |

## Consequences

- A request without priority always gets one, so it cannot fail on the `NOT NULL` column.
- Quiet hours never drop a notification; they only delay it.
- A bad timezone from the host never stops a send.
- SMS is left out of promotional types to avoid telecom costs, and email is included for financial types as their paper trail.
- A host that wants different defaults implements its own `NotificationTypeRegistry`.

## Alternatives rejected

The register records no rejected alternatives. The old README described MEDIUM as respecting quiet hours "during sleeping hours only (configurable)"; that was never a recorded decision and is not what Beacon does.

## In the code

- `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/listener/QuietHoursPolicy.java`
- `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/listener/NotificationRequestedEventListener.java` (`resolvePriority`, `createDeliveryTask`)
- `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/defaults/DefaultNotificationTypeRegistry.java`
- Tests: `QuietHoursPolicyTest` (14 tests); in `NotificationIngestionTransactionalTest`, `missingPriority_isResolvedFromTypeRegistry`, `lowPriorityInsideQuietHours_isHeldUntilWindowEnds`, `highPriorityInsideQuietHours_isAvailableNow`.
- Not yet true: the `NotificationType.PAYMENT_RECEIPT` Javadoc says "High priority" (register G-33).
- Not built yet: `requestedAt` on the request row (ADR 0010). Today the listener reads the Clock instead.
