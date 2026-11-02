# 0019. Providers behind one interface, with error classification, rate limiting and circuit breakers

- Status: Accepted
- Date: 2026-11-02
- Register entries: D-100, D-101, D-102, D-103
- Document status: Approved, last checked against the code on 2026-11-02

## Context

Beacon sends through third-party providers: Twilio and Termii for SMS, SendGrid and Mailgun for email, Firebase for push, or a provider the host writes. Providers fail in different ways. Some failures are temporary and worth retrying; others never succeed however often they are retried. Providers also limit how fast they accept requests. A rate-limit response (HTTP 429) means Beacon is sending too fast, not that the provider is down.

## Decision

- Each vendor is a `NotificationProvider`. A provider registry returns a channel's providers in fallback order. A fake provider is built first, for tests.
- **Error classification:** HTTP 429 and 503 are temporary (retry); HTTP 400 is permanent (no retry).
- **A 429 must never trip the circuit breaker.** Failing over on a 429 would only burn the secondary provider's limits as well.
- **Outbound rate limiting:** a token bucket per provider, in process by default, distributed through Redis as an option.
- **A circuit breaker per provider**, with fallback to the next provider for the channel.
- Build order: error classification is part of item 7 (providers); the rate limiter and the circuit breaker are part of item 8 (the worker).

## Consequences

- The worker never calls a vendor directly; adding a provider means implementing one interface.
- Retries are spent only on failures that can succeed.
- A provider that is throttling Beacon slows Beacon down instead of triggering a failover.
- The default rate limiter needs no infrastructure; a host running several instances can opt into the distributed one.

## Alternatives rejected

- **Treating a 429 as a provider failure** (tripping the breaker and failing over). Rejected because it means Beacon is sending too fast, not that the provider is down, and failing over would burn the secondary provider's limits too.

The register records no other rejected alternatives.

## In the code

Not built yet. The contracts exist in `beacon-api`:

- `beacon-api/src/main/java/io/github/abdulmalikalayande/beacon/api/port/NotificationProvider.java`
- `beacon-api/src/main/java/io/github/abdulmalikalayande/beacon/api/port/ProviderRateLimiter.java`
- `beacon-api/src/main/java/io/github/abdulmalikalayande/beacon/api/port/ProviderCircuitBreaker.java`
- `beacon-api/src/main/java/io/github/abdulmalikalayande/beacon/api/exception/ProviderException.java` (carries the HTTP status code and whether the failure is retryable)
- `beacon-api/src/main/java/io/github/abdulmalikalayande/beacon/api/exception/AllProvidersFailedException.java`
- `beacon-api/src/main/java/io/github/abdulmalikalayande/beacon/api/enums/ProviderName.java`
