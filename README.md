# Beacon

**A reliable, transactionally-safe multi-channel notification library for Spring Boot.**

Push, SMS, and email — with idempotency, automatic retries, provider fallback, and zero required infrastructure. Add the starter, implement one interface, and send.

[![Build](https://github.com/AbdulmalikAlayande/beacon/actions/workflows/ci.yml/badge.svg)](https://github.com/AbdulmalikAlayande/beacon/actions)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Status: Active Development](https://img.shields.io/badge/status-active%20development-yellow)](https://github.com/AbdulmalikAlayande/beacon)

> **Status:** Beacon is in active development. The core pipeline (ingestion, deduplication, database-backed queue, template engine) is built and tested. Provider adapters and the pluggable queue backends (Kafka, RabbitMQ) are in progress. The API contracts in `beacon-api` are stable. Internal implementation details may still change.

---

## The problem Beacon solves

Every non-trivial application eventually needs to send notifications. Most teams bolt together a quick solution — call Twilio in a service method, fire an email after saving to the database — and it works until it doesn't.

The problems that show up in production:

- A payment transaction rolls back, but the receipt email already went out.
- The app restarts mid-queue and pending notifications are lost.
- Twilio returns a 503 and the message is silently dropped.
- The same event triggers twice under load and the user gets two OTPs.
- Someone's asleep at 2 AM and gets a promotional email.

Beacon is built specifically to handle all of these correctly, out of the box, without requiring you to stand up Kafka or Redis to do it.

---

## How it works

When your application calls `notificationService.send(request)`, Beacon does not immediately write to a queue or call a provider. It publishes a Spring `ApplicationEvent` and returns an acknowledgement.

A `@TransactionalEventListener(AFTER_COMMIT)` listener picks up the event after your transaction successfully commits. If your transaction rolls back — a failed payment, a constraint violation, anything — the notification is discarded automatically. No ghost notifications.

The listener then:
1. Resolves the user's contact details and channel preferences via your `UserPreferenceResolver`.
2. Fans out to one delivery task per enabled channel.
3. Checks quiet hours — low-priority notifications during a user's quiet window get deferred, not dropped.
4. Encrypts the template context before writing to the queue.
5. Persists each task to the `notification_queue` table and writes an initial status record.

A scheduled poller sweeps the queue using `SELECT FOR UPDATE SKIP LOCKED`, so multiple instances of your application can run without ever processing the same task twice. Workers render the template, check the suppression list, pick the right provider, and send. Failures retry with backoff. Exhausted retries go to a dead letter queue. Actual delivery is confirmed via provider webhooks.

---

## Quick start

**1. Add the dependency**

```xml
<dependency>
    <groupId>io.github.abdulmalikalayande</groupId>
    <artifactId>beacon-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

**2. Implement one interface**

Beacon never queries your user tables. You tell it where to find contact details:

```java
@Component
public class MyUserPreferenceResolver implements UserPreferenceResolver {

    private final UserRepository userRepository;

    @Override
    public NotificationPreference resolve(String userId) {
        User user = userRepository.findById(userId).orElseThrow();
        return new NotificationPreference(
            user.getId(),
            user.getEmail(),
            user.getPhoneNumber(),
            user.getPushToken(),
            EnumSet.of(NotificationChannel.EMAIL, NotificationChannel.SMS),
            user.getTimezone(),       // e.g. "Africa/Lagos"
            user.getQuietHoursStart(),
            user.getQuietHoursEnd()
        );
    }

    @Override
    public List<NotificationPreference> resolveAll(List<String> userIds) {
        return userRepository.findAllById(userIds).stream()
            .map(this::toPreference)
            .toList();
    }
}
```

**3. Configure your providers and database**

```yaml
beacon:
  datasource:
    url: jdbc:postgresql://localhost:5432/my_notifications_db
    username: postgres
    password: secret

  providers:
    sms:
      primary:
        name: twilio
        account-sid: ${TWILIO_ACCOUNT_SID}
        auth-token: ${TWILIO_AUTH_TOKEN}
        from-number: +1234567890
      fallback:
        name: termii
        api-key: ${TERMII_API_KEY}
        from: MyApp

    email:
      primary:
        name: sendgrid
        api-key: ${SENDGRID_API_KEY}
        from-email: noreply@myapp.com
        from-name: MyApp

    push:
      primary:
        name: firebase
        service-account-json: classpath:firebase-service-account.json
```

**4. Send a notification**

```java
@Service
public class PaymentService {

    private final NotificationService notificationService;

    @Transactional
    public void processPayment(String userId, BigDecimal amount) {
        // ... your payment logic ...

        notificationService.send(new NotificationRequest(
            "payment-receipt-" + transactionId,   // idempotency key
            userId,
            NotificationType.PAYMENT_RECEIPT,
            NotificationPriority.HIGH,
            null,                                  // resolve channels from user preferences
            Map.of("amount", amount.toString(), "currency", "NGN"),
            null                                   // send now
        ));
        // Beacon waits for this transaction to commit before queuing anything.
        // If the transaction rolls back, nothing gets sent.
    }
}
```

That's it. Beacon handles the rest.

---

## Modules

Beacon is a multi-module Maven project. You only need `beacon-spring-boot-starter` as a dependency — the rest are internal.

| Module | Purpose |
|---|---|
| `beacon-api` | Pure Java contracts — enums, DTOs, interfaces, exceptions. No Spring dependency. This is what your IDE shows when you import Beacon. |
| `beacon-core` | All the implementation — the queue, the workers, the dedup store, the template engine, the listener. |
| `beacon-spring-boot-starter` | Autoconfiguration, `@EnableBeacon`, property binding. Wires everything together. |
| `beacon-test` | Test fixtures for host applications — mock providers, in-memory queue, assertion utilities. |

---

## Integration boundary

Beacon owns:
- The delivery pipeline from accepted request to provider call.
- The `notification_queue` and `notification_status` tables (in their own Flyway history, isolated from your schema).
- Deduplication, suppression, quiet hours, retries, and fallback.
- Webhook endpoints for provider delivery callbacks.

You own:
- Your user data. Beacon never touches your tables.
- Provider credentials (supplied via configuration).
- One interface implementation: `UserPreferenceResolver`.

Optionally, you can implement:
- `TemplateResolver` — serve templates from your own database instead of classpath files.
- `NotificationTypeRegistry` — define custom notification types and their priorities/channels.
- `UserBatchSource` — power broadcast/batch notifications with your own user source.
- Any `port/` interface — plug in your own queue backend, dedup store, or provider.

---

## Webhook security

Beacon exposes webhook endpoints that providers (Twilio, SendGrid, Firebase) call to confirm delivery. Your Spring Security configuration likely blocks these by default.

Add the Beacon security configurer to your `SecurityFilterChain`:

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    NotificationWebhookSecurityConfigurer.configure(http);
    // ... rest of your security config ...
    return http.build();
}
```

This whitelists Beacon's webhook paths (`/notifications/webhooks/**`) while keeping everything else protected. Beacon still validates each incoming webhook against the provider's HMAC signature — the whitelist just lets the request past Spring Security so Beacon's own validation can run.

---

## Default behaviour and pluggable alternatives

Beacon is designed to require zero infrastructure beyond your PostgreSQL database.

| Concern | Default | Pluggable alternative |
|---|---|---|
| Queue | Database-backed (`notification_queue` table, `FOR UPDATE SKIP LOCKED`) | Kafka, RabbitMQ, Pulsar |
| Deduplication | Database unique constraint | Redis (`SETNX`) |
| Template engine | `${variable}` substitution | Implement `TemplateResolver` for any engine |
| SMS primary | Twilio | Any `NotificationProvider` implementation |
| SMS fallback | Termii | Any `NotificationProvider` implementation |
| Email primary | SendGrid | Any `NotificationProvider` implementation |
| Email fallback | Mailgun | Any `NotificationProvider` implementation |
| Push | Firebase Cloud Messaging | Any `NotificationProvider` implementation |

To switch to a pluggable backend, register a bean that implements the relevant port interface from `beacon-api`. Beacon's autoconfiguration backs off via `@ConditionalOnMissingBean`.

---

## Notification lifecycle

```
Host calls send()
    → Validation + dedup check
    → Publishes NotificationRequestedEvent
    → Returns NotificationResponse (CREATED) immediately

After host transaction commits:
    → Resolves user preferences
    → Fan-out per channel
    → Quiet hours check (defers low-priority if in window)
    → Encrypts context
    → Writes DeliveryTaskEntity (QUEUED) + NotificationStatusEntity

Poller (scheduled):
    → SELECT FOR UPDATE SKIP LOCKED
    → Hands batch to workers

Worker (per task):
    → Decrypts context
    → Suppression check (blocks if bounced/unsubscribed)
    → Renders template
    → Rate limiter acquire
    → Circuit breaker check (fails over to fallback if open)
    → Provider.send()

On success:
    → Deletes from notification_queue
    → Updates notification_status to SENT

On transient failure:
    → Increments retry_count
    → Sets available_at = now + backoff
    → Status: QUEUED (re-polled later)

On permanent failure or max retries:
    → Status: FAILED, moves to DLQ

On webhook callback from provider:
    → Status: DELIVERED (or BOUNCED → suppression list updated)
```

---

## Notification types and priorities

| Type | Default Priority | Default Channels |
|---|---|---|
| `OTP` | HIGH | SMS, PUSH |
| `SECURITY_ALERT` | HIGH | PUSH, EMAIL, SMS |
| `PAYMENT_FAILED` | HIGH | PUSH, EMAIL, SMS |
| `SYSTEM_ALERT` | HIGH | PUSH, EMAIL, SMS |
| `PAYMENT_RECEIPT` | MEDIUM | EMAIL, PUSH |
| `ORDER_CONFIRMATION` | MEDIUM | EMAIL, PUSH |
| `PROMO` | LOW | PUSH, EMAIL |
| `ENGAGEMENT` | LOW | PUSH, EMAIL |

**HIGH** priority bypasses quiet hours. **LOW** priority respects them. **MEDIUM** respects quiet hours during sleeping hours only (configurable).

Custom types are supported — implement `NotificationTypeRegistry` and return whatever priority and channels make sense for your domain.

---

## Template syntax

Templates use `${variable}` placeholders substituted from the context map you pass with each request:

```
Subject: Order ${orderId} confirmed for ${customerName}
Body: Hi ${customerName}, your order of ${itemCount} item(s) has been confirmed. 
      Track it at ${trackingUrl}.
```

If a placeholder key is missing from the context, Beacon logs a warning and leaves `${variable}` literally in the output — it does not crash or silently drop the field. This is deliberate: a visible `${orderId}` in a delivered message is loud and easy to catch in testing.

Templates are loaded from classpath by default. To load from a database, CMS, or any other source, implement `TemplateResolver`.

---

## Requirements

- Java 21+
- Spring Boot 3.2+
- PostgreSQL 14+ (required — the default queue uses `FOR UPDATE SKIP LOCKED`, which behaves differently on other databases)
- Maven 3.8+

---

## What is not yet available

Beacon is in active development. The following are planned but not yet shipped:

- [ ] Kafka queue backend
- [ ] RabbitMQ queue backend
- [ ] Redis deduplication store
- [ ] Scheduled/delayed send (the infrastructure is built, configuration is not yet wired)
- [ ] Admin dashboard / DLQ inspection
- [ ] Maven Central publication (available as SNAPSHOT only)

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

---

## License

Apache License 2.0. See [LICENSE](LICENSE).