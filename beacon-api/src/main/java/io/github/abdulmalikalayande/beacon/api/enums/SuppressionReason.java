package io.github.abdulmalikalayande.beacon.api.enums;

/**
 * Why a contact (email address or phone number) is on the suppression list.
 *
 * <p>Suppressed contacts are blocked at the routing layer before any delivery
 * attempt, protecting sender reputation and maintaining regulatory compliance.
 */
public enum SuppressionReason {

    /** The provider reported a permanent (hard) bounce for this contact. */
    HARD_BOUNCE,

    /** The recipient marked a previous message as spam. */
    SPAM_COMPLAINT,

    /** The recipient explicitly unsubscribed. */
    USER_UNSUBSCRIBED,

    /** An administrator manually suppressed this contact. */
    ADMIN_SUPPRESSED
}
