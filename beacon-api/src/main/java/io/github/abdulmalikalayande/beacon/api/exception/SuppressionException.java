
package io.github.abdulmalikalayande.beacon.api.exception;

import io.github.abdulmalikalayande.beacon.api.enums.SuppressionReason;

/**
 * Thrown when delivery is blocked because the recipient's contact is on the
 * suppression list.
 *
 * <p>This is typically not an error the host needs to act on — it is the system
 * correctly protecting sender reputation and honoring opt-outs. The pipeline
 * records the notification as {@code SUPPRESSED} rather than failing it.
 */
public class SuppressionException extends NotificationException {

    private final SuppressionReason reason;

    public SuppressionException(SuppressionReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /** @return why the contact was suppressed */
    public SuppressionReason getReason() {
        return reason;
    }
}