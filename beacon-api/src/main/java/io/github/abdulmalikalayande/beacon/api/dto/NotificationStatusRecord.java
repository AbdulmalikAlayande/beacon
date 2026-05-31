package io.github.abdulmalikalayande.beacon.api.dto;

import io.github.abdulmalikalayande.beacon.api.enums.NotificationChannel;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationStatus;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationType;
import io.github.abdulmalikalayande.beacon.api.enums.ProviderName;

import java.time.Instant;

/**
 * A point-in-time snapshot of a notification's tracking record, returned when a
 * host queries the status of a notification.
 *
 * @param notificationId the notification's unique identifier
 * @param userId         the recipient
 * @param channel        the delivery channel
 * @param type           the notification type
 * @param provider       the provider that handled or attempted delivery, or {@code null}
 * @param status         the current status
 * @param retryCount     number of delivery attempts made
 * @param failureReason  a masked, PII-free failure description, or {@code null}
 * @param createdAt      when the notification was first created
 * @param updatedAt      when the status was last changed
 */
public record NotificationStatusRecord(
        String notificationId,
        String userId,
        NotificationChannel channel,
        NotificationType type,
        ProviderName provider,
        NotificationStatus status,
        int retryCount,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {}