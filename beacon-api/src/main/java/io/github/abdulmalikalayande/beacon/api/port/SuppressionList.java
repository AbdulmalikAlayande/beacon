package io.github.abdulmalikalayande.beacon.api.port;

import io.github.abdulmalikalayande.beacon.api.dto.SuppressionEntry;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationChannel;

/**
 * Tracks contacts that must not be sent to on a given channel.
 *
 * <p><b>Library provides the default (database-backed) implementation.</b>
 * Checked at the routing layer before any delivery attempt and updated
 * automatically when providers report hard bounces, spam complaints, or
 * unsubscribes via webhooks.
 */
public interface SuppressionList {

    /**
     * @param contact the email address or phone number
     * @param channel the channel to check
     * @return {@code true} if the contact is suppressed on this channel
     */
    boolean isSuppressed(String contact, NotificationChannel channel);

    /**
     * Add a contact to the suppression list.
     *
     * @param entry the suppression entry
     */
    void suppress(SuppressionEntry entry);

    /**
     * Remove a contact from the suppression list for a channel (e.g. after a
     * re-subscribe).
     *
     * @param contact the email address or phone number
     * @param channel the channel to lift suppression on
     */
    void unsuppress(String contact, NotificationChannel channel);
}