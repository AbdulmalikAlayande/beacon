# 0028. `NotificationWebhookSecurityConfigurer` in `beacon-api`

- Status: Superseded by 0003
- Date: 2026-11-02
- Register entries: register section 12 (superseded decisions), D-015
- Document status: Approved, last checked against the code on 2026-11-02

## Context

The helper that lets provider webhooks through the host's Spring Security, `NotificationWebhookSecurityConfigurer`, was first placed in `beacon-api` next to the other public contracts.

## Decision

`beacon-api` contained the webhook security configurer.

## Consequences

The configurer depends on Spring Security, so `beacon-api` would have depended on it too, breaking the rule that `beacon-api` is plain Java (ADR 0004).

## Alternatives rejected

Superseded by placing the configurer in `beacon-spring-boot-starter` (ADR 0003).

## In the code

Not in `beacon-api`. Not built anywhere yet (ADR 0021).
