# 0021. Webhooks are verified by HMAC; reconciliation runs daily

- Status: Accepted
- Date: 2026-11-02
- Register entries: D-107, D-109
- Document status: Approved, last checked against the code on 2026-11-02

## Context

A provider's success response only means it accepted the message. Whether the message was delivered, bounced or marked as spam arrives later, when the provider calls back. Those callbacks reach the host application, whose Spring Security configuration almost certainly rejects requests that carry no login. Anyone could also call the endpoint pretending to be a provider. And some callbacks never arrive, so Beacon's records and the provider's can drift apart.

## Decision

- **Webhooks:**
  - Endpoints live under `/notifications/webhooks/**`.
  - Each callback is verified with the provider's HMAC signature. This is the real security control.
  - The starter's `NotificationWebhookSecurityConfigurer` lets those paths through the host's Spring Security, so Beacon's own verification can run.
  - Bounces and spam complaints add the contact to the suppression list automatically.
- **Reconciliation:** a daily job compares Beacon's records with the providers' reports and corrects mismatches.
- Build order (proposed, not final): webhooks with delivery status and automatic suppression are item 11; reconciliation is item 14.

## Consequences

- `DELIVERED`, bounced and complaint statuses come from verified callbacks only.
- A request without a valid signature is rejected even though Spring Security let it through.
- The suppression list grows by itself as providers report bounces and complaints (ADR 0020).
- A missing callback is caught by the next reconciliation run.
- Webhooks need a web stack, which is why `spring-boot-starter-webmvc` belongs to the starter (ADR 0003).

## Alternatives rejected

- **Relying on Spring Security alone for webhook paths.** A provider cannot present the host's login, so the paths must be let through; the HMAC signature replaces Spring Security as the control.

The register records no other rejected alternatives.

## In the code

Not built yet. What exists in `beacon-api/src/main/java/io/github/abdulmalikalayande/beacon/api/`:

- `port/WebhookHandler.java`, `port/ReconciliationJob.java`
- `port/NotificationProvider.java` (`verifyWebhookSignature`, `parseWebhookEvent`)
- `dto/ProviderWebhookPayload.java` (no interface uses it yet), `enums/WebhookEventType.java`
- `exception/InvalidWebhookSignatureException.java`
