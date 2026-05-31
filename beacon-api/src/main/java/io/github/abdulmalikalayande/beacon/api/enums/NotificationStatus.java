package io.github.abdulmalikalayande.beacon.api.enums;

/**
 * Every state a notification can occupy across its lifecycle.
 *
 * <p>The typical happy path is:
 * {@code CREATED → QUEUED → PROCESSING → SENT → DELIVERED}.
 *
 * <p>A critical distinction exists between {@link #SENT} and {@link #DELIVERED}:
 * {@code SENT} means the provider accepted the message (HTTP 200), while
 * {@code DELIVERED} means the provider later confirmed actual delivery to the
 * recipient via an asynchronous webhook. These are not the same event and may
 * be separated by seconds, hours, or never (in the case of a bounce).
 */
public enum NotificationStatus {
    
    /** The notification has been accepted by Beacon but not yet queued. */
    CREATED,
    
    /** The notification is sitting in a channel queue awaiting a worker. */
    QUEUED,
    
    /** A worker has picked up the task and is actively processing it. */
    PROCESSING,
    
    /**
     * The provider accepted the message for delivery (e.g. HTTP 200).
     * This does NOT mean the recipient received it — see {@link #DELIVERED}.
     */
    SENT,
    
    /**
     * The provider confirmed, via an asynchronous webhook callback, that the
     * message actually reached the recipient. This is the only true success state.
     */
    DELIVERED,
    
    /** Delivery failed permanently after all retries and fallbacks were exhausted. */
    FAILED,
    
    /**
     * The provider reported a hard bounce. The associated contact is
     * automatically added to the suppression list.
     */
    BOUNCED,
    
    /**
     * The notification was blocked before sending because the recipient's
     * contact is on the suppression list.
     */
    SUPPRESSED,
    
    /**
     * The notification was deferred because it is low priority and the current
     * time falls within the recipient's quiet hours. It will be re-queued once
     * quiet hours end.
     */
    DELAYED
}
