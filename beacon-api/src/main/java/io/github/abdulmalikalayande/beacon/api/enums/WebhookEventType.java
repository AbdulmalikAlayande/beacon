package io.github.abdulmalikalayande.beacon.api.enums;

/**
 * The normalized event types Beacon understands from provider webhook callbacks.
 *
 * <p>Each provider has its own webhook vocabulary; the provider adapter is
 * responsible for translating raw provider payloads into one of these
 * normalized types.
 */
public enum WebhookEventType {
    
    /** The provider confirmed the message reached the recipient. */
    DELIVERED,
    
    /** The provider reported a hard bounce. Triggers suppression. */
    BOUNCED,
    
    /** The provider reported a permanent delivery failure. */
    FAILED,
    
    /** The recipient marked the message as spam. Triggers suppression. */
    SPAM_COMPLAINT,
    
    /** The recipient unsubscribed. Triggers suppression. */
    UNSUBSCRIBED
}