
package io.github.abdulmalikalayande.beacon.api.enums;

/**
 * The priority of a notification, which controls whether quiet hours apply.
 */
public enum NotificationPriority {
    
    /**
     * Urgent and time-sensitive (OTPs, security alerts, payment confirmations, order confirmations).
     * Bypasses quiet hours entirely, and it is delivered immediately regardless of the
     * recipient's local time.
     */
    HIGH,

    /**
     * Transactional but not urgent (payment receipts).
     * Bypasses quiet hours, because recipients expect these right after they act,
     * but is claimed after HIGH when the queue is busy.
     */
    MEDIUM,
    
    /**
     * Non-urgent (promotions, engagement nudges). Respects quiet hours, if the
     * current time falls within the recipient's quiet window, delivery is
     * deferred until the window ends.
     */
    LOW
}