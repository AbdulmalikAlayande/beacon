package io.github.abdulmalikalayande.beacon.api.service;

import io.github.abdulmalikalayande.beacon.api.dto.NotificationStatusRecord;
import io.github.abdulmalikalayande.beacon.api.request.BatchNotificationRequest;
import io.github.abdulmalikalayande.beacon.api.request.NotificationRequest;
import io.github.abdulmalikalayande.beacon.api.response.NotificationResponse;

/**
 * The primary entry point hosts use to send notifications. This is the one
 * interface most host applications will interact with directly.
 *
 * <p><b>Transactional behavior:</b> when {@code send} is called inside an active
 * transaction, Beacon does not dispatch immediately. It defers queuing until the
 * host's transaction commits (via an after-commit hook), so a notification is
 * never sent for work that ultimately rolls back. Called outside a transaction,
 * it dispatches right away.
 *
 * <p><b>Idempotency:</b> each request carries a host-supplied key. Submitting the
 * same key twice results in the duplicate being rejected, not delivered twice.
 *
 * <p>Implemented by beacon-core. Hosts never implement this.
 */
public interface NotificationService {

    /**
     * Accept a single-user notification for delivery.
     *
     * @param request the notification to send
     * @return an acknowledgement carrying the assigned notification id and
     *         early-lifecycle status
     * @throws io.github.abdulmalikalayande.beacon.api.exception.DuplicateNotificationException
     *         if the idempotency key has already been seen
     * @throws jakarta.validation.ConstraintViolationException
     *         if the request fails validation
     */
    NotificationResponse send(NotificationRequest request);

    /**
     * Accept a batch notification targeting many users.
     *
     * <p>Recipients are resolved according to the request's audience. For large
     * audiences, Beacon pages through users in chunks rather than loading them
     * all at once.
     *
     * @param request the batch to send
     * @return an acknowledgement for the accepted batch
     * @throws jakarta.validation.ConstraintViolationException
     *         if the request fails validation
     */
    NotificationResponse sendBatch(BatchNotificationRequest request);

    /**
     * Look up the current tracking record for a notification.
     *
     * @param notificationId the id returned at acceptance time
     * @return the current status record, or {@code null} if unknown
     */
    NotificationStatusRecord getStatus(String notificationId);

    /**
     * Cancel a notification that was scheduled for future delivery and has not
     * yet been dispatched. No-op if it has already been sent or does not exist.
     *
     * @param notificationId the id of the scheduled notification
     */
    void cancelScheduled(String notificationId);
}