package io.github.abdulmalikalayande.beacon.api.port;

import io.github.abdulmalikalayande.beacon.api.dto.NotificationStatusEvent;
import io.github.abdulmalikalayande.beacon.api.dto.NotificationStatusRecord;

import java.util.List;

/**
 * Records and serves the lifecycle status of notifications.
 *
 * <p><b>Library provides the default implementation.</b> Consumes status events
 * emitted across the pipeline and persists them to the status table, decoupling
 * status writes from the delivery path so workers are never blocked on a status
 * write.
 */
public interface NotificationStatusTracker {

    /**
     * Record a status-change event.
     *
     * @param event the status change to persist
     */
    void record(NotificationStatusEvent event);

    /**
     * @param notificationId the notification id
     * @return the current status record, or {@code null} if unknown
     */
    NotificationStatusRecord get(String notificationId);

    /**
     * @param userId the user id
     * @return all status records for the user, most recent first
     */
    List<NotificationStatusRecord> getByUser(String userId);
}