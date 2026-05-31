package io.github.abdulmalikalayande.beacon.api.dto;

import io.github.abdulmalikalayande.beacon.api.enums.NotificationChannel;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationPriority;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationType;

import java.time.Instant;

/**
 * A single unit of delivery work: one user, one channel, one message.
 *
 * <p>A single {@code NotificationRequest} may fan out into multiple delivery
 * tasks — one per channel the user is eligible for. This is the object that
 * travels through the queue. The {@code encryptedContext} stays encrypted while
 * in the queue and is only decrypted by the worker immediately before rendering.
 *
 * @param taskId           unique identifier for this task
 * @param notificationId   identifier of the parent notification
 * @param idempotencyKey   the host-supplied key tracing back to the trigger
 * @param userId           the recipient's host identifier
 * @param channel          the channel this task delivers on
 * @param type             the notification type
 * @param priority         the notification priority
 * @param encryptedContext the template context, encrypted at rest
 * @param retryCount       how many delivery attempts have been made so far
 * @param createdAt        when this task was created
 * @param scheduledAt      when this task should be delivered, or {@code null} for now
 */
public record DeliveryTask(
        String taskId,
        String notificationId,
        String idempotencyKey,
        String userId,
        NotificationChannel channel,
        NotificationType type,
        NotificationPriority priority,
        String encryptedContext,
        int retryCount,
        Instant createdAt,
        Instant scheduledAt
) {
    /**
     * @return a copy of this task with the retry count incremented by one
     */
    public DeliveryTask withIncrementedRetry() {
        return new DeliveryTask(
                taskId, notificationId, idempotencyKey, userId, channel, type,
                priority, encryptedContext, retryCount + 1, createdAt, scheduledAt
        );
    }
}