package io.github.abdulmalikalayande.beacon.api.request;

import io.github.abdulmalikalayande.beacon.api.enums.NotificationChannel;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationPriority;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The request a host submits to trigger a notification to many users at once
 * (e.g. an admin broadcasting a promo).
 *
 * <p>The {@code batchId} provides idempotency at the batch level so a
 * re-submitted batch is not processed twice. The {@code context} is shared
 * across every recipient in the batch; per-user substitution still draws from
 * each user's resolved preferences at render time.
 *
 * @param batchId           stable unique identifier for this batch (required)
 * @param type              the notification type (required)
 * @param priority          explicit priority, or {@code null} to derive from type
 * @param preferredChannels channels to use, or {@code null}/empty to resolve automatically
 * @param context           shared key/value data substituted into the template
 * @param audience          who receives this batch (required, cascaded validation)
 * @param scheduledAt       when to deliver, or {@code null} to deliver now
 */
public record BatchNotificationRequest(

        @NotBlank(message = "batchId must not be blank")
        String batchId,

        @NotNull(message = "notification type must not be null")
        NotificationType type,

        NotificationPriority priority,

        List<@NotNull NotificationChannel> preferredChannels,

        Map<String, String> context,

        @NotNull(message = "audience must not be null")
        @Valid
        BatchAudience audience,

        Instant scheduledAt
) {
    public BatchNotificationRequest {
        if (preferredChannels != null) {
            preferredChannels = List.copyOf(preferredChannels);
        }
        if (context != null) {
            context = Map.copyOf(context);
        }
    }

    public boolean hasExplicitChannels() {
        return preferredChannels != null && !preferredChannels.isEmpty();
    }

    public boolean isScheduled() {
        return scheduledAt != null;
    }
}