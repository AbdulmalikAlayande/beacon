package io.github.abdulmalikalayande.beacon.api.exception;

/**
 * Thrown when a notification is rejected because its idempotency key has already
 * been seen. This is the upstream-deduplication guarantee in action — a repeated
 * trigger for the same logical event is refused rather than delivered twice.
 *
 * <p>Hosts can usually treat this as a benign no-op: the original notification
 * was already accepted, so the duplicate trigger simply has nothing to do.
 */
public class DuplicateNotificationException extends NotificationException {

    private final String idempotencyKey;

    public DuplicateNotificationException(String idempotencyKey) {
        super("Duplicate notification rejected for idempotencyKey: " + idempotencyKey);
        this.idempotencyKey = idempotencyKey;
    }

    /**
     * @return the idempotency key that was already seen
     */
    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}