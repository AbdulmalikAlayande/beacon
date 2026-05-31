package io.github.abdulmalikalayande.beacon.api.dto;

import io.github.abdulmalikalayande.beacon.api.enums.NotificationChannel;
import io.github.abdulmalikalayande.beacon.api.enums.ProviderName;
import io.github.abdulmalikalayande.beacon.api.enums.WebhookEventType;

import java.time.Instant;
import java.util.Map;

/**
 * A normalized representation of a provider's webhook callback, produced after a
 * provider adapter parses and validates the raw incoming request.
 *
 * @param providerMessageId the provider's own message identifier
 * @param notificationId    Beacon's internal id, echoed back by the provider
 * @param eventType         the normalized event type
 * @param contact           the affected contact (email/phone), for suppression updates
 * @param channel           the channel this webhook concerns
 * @param provider          the provider that sent this webhook
 * @param rawPayload        the original provider payload, retained for audit
 * @param receivedAt        when Beacon received the webhook
 */
public record ProviderWebhookPayload(
        String providerMessageId,
        String notificationId,
        WebhookEventType eventType,
        String contact,
        NotificationChannel channel,
        ProviderName provider,
        Map<String, String> rawPayload,
        Instant receivedAt
) {}