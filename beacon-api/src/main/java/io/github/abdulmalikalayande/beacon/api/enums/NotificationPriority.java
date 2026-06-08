
package io.github.abdulmalikalayande.beacon.api.enums;

/**
 * The priority of a notification, which controls whether quiet hours apply.
 */
public enum NotificationPriority {
    
    /**
     * Urgent and time-sensitive (OTPs, security alerts, payment confirmations).
     * Bypasses quiet hours entirely — delivered immediately regardless of the
     * recipient's local time.
     */
    HIGH,
    
    /**
     *
     */
    MEDIUM,
    
    /**
     * Non-urgent (promotions, engagement nudges). Respects quiet hours — if the
     * current time falls within the recipient's quiet window, delivery is
     * deferred until the window ends.
     */
    LOW
}