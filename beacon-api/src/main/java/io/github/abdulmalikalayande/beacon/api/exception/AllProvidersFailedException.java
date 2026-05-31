package io.github.abdulmalikalayande.beacon.api.exception;

import io.github.abdulmalikalayande.beacon.api.enums.NotificationChannel;

/**
 * Thrown when every provider for a channel — primary and all fallbacks — has
 * failed for a delivery. This is the trigger point for cross-channel fallback
 * (if the type and user preferences allow it) or, failing that, routing the task
 * to the dead letter queue.
 */
public class AllProvidersFailedException extends NotificationException {

    private final NotificationChannel channel;

    public AllProvidersFailedException(NotificationChannel channel, String message) {
        super(message);
        this.channel = channel;
    }

    public AllProvidersFailedException(NotificationChannel channel, String message,
                                       Throwable cause) {
        super(message, cause);
        this.channel = channel;
    }

    /** @return the channel for which all providers failed */
    public NotificationChannel getChannel() {
        return channel;
    }
}