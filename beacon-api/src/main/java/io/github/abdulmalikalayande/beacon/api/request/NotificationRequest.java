package io.github.abdulmalikalayande.beacon.api.request;

import io.github.abdulmalikalayande.beacon.api.enums.NotificationChannel;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationPriority;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The request a host submits to trigger a single-user notification.
 *
 * <p>This is the primary input to {@code NotificationService.send(...)}. The
 * {@code idempotencyKey} is critical: the host must supply a stable, unique key
 * per logical event (e.g. {@code "payment-receipt-<txnId>"}) so Beacon can drop
 * duplicate triggers from misbehaving upstream services.
 *
 * <p>If {@code preferredChannels} is {@code null} or empty, Beacon resolves the
 * channels from the user's preferences intersected with what the type supports.
 * If {@code priority} is {@code null}, Beacon derives it from the {@code type}.
 *
 * @param idempotencyKey    stable unique key per logical event (required)
 * @param userId            the recipient's host identifier (required)
 * @param type              the notification type (required)
 * @param priority          explicit priority, or {@code null} to derive from type
 * @param preferredChannels channels to use, or {@code null}/empty to resolve automatically
 * @param context           key/value data substituted into the template
 * @param scheduledAt       when to deliver, or {@code null} to deliver now
 */
public record NotificationRequest(

        @NotBlank(message = "idempotencyKey must not be blank")
        String idempotencyKey,

        @NotBlank(message = "userId must not be blank")
        String userId,

        @NotNull(message = "notification type must not be null")
        NotificationType type,

        NotificationPriority priority,

        List<@NotNull NotificationChannel> preferredChannels,

        Map<String, String> context,

        Instant scheduledAt
) {
    /**
     * Canonical constructor with light normalization: defensively copies the
     * mutable collections so the record stays effectively immutable even if the
     * caller mutates the originals after construction.
     */
    public NotificationRequest {
        if (preferredChannels != null) {
            preferredChannels = List.copyOf(preferredChannels);
        }
        if (context != null) {
            context = Map.copyOf(context);
        }
    }

    /**
     * @return {@code true} if the host explicitly specified channels
     */
    public boolean hasExplicitChannels() {
        return preferredChannels != null && !preferredChannels.isEmpty();
    }

    /**
     * @return {@code true} if this notification is scheduled for the future
     */
    public boolean isScheduled() {
        return scheduledAt != null;
    }
}