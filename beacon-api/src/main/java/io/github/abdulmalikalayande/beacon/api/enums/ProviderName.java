package io.github.abdulmalikalayande.beacon.api.enums;

/**
 * Identifies which third-party provider handled or attempted a delivery.
 *
 * <p>Used for status tracking, circuit-breaker state, and reconciliation.
 * The set is open-ended via {@link #CUSTOM} so hosts can register their own
 * {@code NotificationProvider} implementations.
 */
public enum ProviderName {

    /** Twilio — default primary SMS provider. */
    TWILIO,

    /** Termii — default fallback SMS provider, strong African coverage. */
    TERMII,

    /** SendGrid — default primary email provider. */
    SENDGRID,

    /** Mailgun — default fallback email provider. */
    MAILGUN,

    /** Firebase Cloud Messaging — default push provider. */
    FIREBASE,

    /** A host-supplied custom provider. */
    CUSTOM
}
