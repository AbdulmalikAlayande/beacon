package io.github.abdulmalikalayande.beacon.api.dto;

import io.github.abdulmalikalayande.beacon.api.enums.NotificationStatus;
import io.github.abdulmalikalayande.beacon.api.enums.ProviderName;

import java.time.Instant;

/**
 * An event emitted whenever a notification changes state. Published to Beacon's
 * internal status stream and consumed by the status tracker, which persists it
 * to the {@code notification_status} table.
 *
 * <p>This is distinct from the internal {@code NotificationRequestedEvent} (which
 * lives in beacon-core): this DTO is a public, channel-agnostic status-change
 * record that observers and dashboards can consume.
 *
 * @param notificationId the affected notification
 * @param status         the new status
 * @param provider       the provider involved, or {@code null} if not applicable
 * @param failureReason  a masked, PII-free reason when the status is a failure, else {@code null}
 * @param occurredAt     when the state change occurred
 */
public record NotificationStatusEvent(
        String notificationId,
        NotificationStatus status,
        ProviderName provider,
        String failureReason,
        Instant occurredAt
) {}