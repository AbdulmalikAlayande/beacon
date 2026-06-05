package io.github.abdulmalikalayande.beacon.api.enums;

/**
 * The category of a notification.
 *
 * <p>The type determines the notification's default {@link NotificationPriority},
 * which channels it supports, and which template is used to render it. Hosts that
 * need categories beyond the built-in set can use {@link #CUSTOM} in combination
 * with a {@code NotificationTypeRegistry} implementation.
 */
public enum NotificationType {
    
    /** One-time passwords. High priority, time-sensitive, bypasses quiet hours. */
    OTP,
    
    /** Confirmation that a payment succeeded. High priority. */
    PAYMENT_RECEIPT,
    
    /** Notice that a payment failed. High priority. */
    PAYMENT_FAILED,
    
    /** Security-related alerts such as new device logins. High priority. */
    SECURITY_ALERT,
    
    /** Promotional/marketing content. Low priority, respects quiet hours. */
    PROMO,
    
    /** Engagement nudges and re-activation messages. Low priority. */
    ENGAGEMENT,
    
    /**
     * Transactional messages related to order processing, such as shipping updates or delivery confirmations.
     */
    ORDER_CONFIRMATION,
    
    /** Operational/system notices. Priority determined by the host. */
    SYSTEM_ALERT,
    
    /**
     * A host-defined type. When used, the host should supply a
     * {@code NotificationTypeRegistry} so Beacon can resolve the priority and
     * supported channels for it.
     */
    CUSTOM
}