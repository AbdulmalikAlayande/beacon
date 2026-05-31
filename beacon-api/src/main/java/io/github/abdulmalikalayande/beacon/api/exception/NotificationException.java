package io.github.abdulmalikalayande.beacon.api.exception;

/**
 * Base type for all exceptions thrown by Beacon.
 *
 * <p>Unchecked by design: hosts should not be forced to handle delivery
 * failures they cannot meaningfully recover from at the call site. Catch the
 * specific subtypes you care about (e.g. {@link DuplicateNotificationException})
 * and let the rest propagate to your global error handling.
 */
public class NotificationException extends RuntimeException {

    public NotificationException(String message) {
        super(message);
    }

    public NotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}