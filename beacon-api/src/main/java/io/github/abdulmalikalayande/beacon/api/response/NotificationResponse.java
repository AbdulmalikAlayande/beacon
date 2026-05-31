package io.github.abdulmalikalayande.beacon.api.response;

import io.github.abdulmalikalayande.beacon.api.enums.NotificationStatus;

import java.time.Instant;

/**
 * The acknowledgement Beacon returns immediately after accepting a
 * {@code NotificationRequest}.
 *
 * <p>Because delivery is asynchronous (and, when called inside a transaction,
 * deferred until after commit), this response confirms acceptance — not
 * delivery. The {@code status} here will be an early-lifecycle state such as
 * {@code CREATED} or {@code QUEUED}. Use {@code NotificationService.getStatus(...)}
 * to track progress beyond acceptance.
 *
 * @param notificationId Beacon's identifier for the accepted notification
 * @param idempotencyKey the key supplied by the host, echoed back
 * @param status         the early-lifecycle status at acceptance time
 * @param acceptedAt     when Beacon accepted the request
 */
public record NotificationResponse(
        String notificationId,
        String idempotencyKey,
        NotificationStatus status,
        Instant acceptedAt
) {}