package io.github.abdulmalikalayande.beacon.api.dto;

import io.github.abdulmalikalayande.beacon.api.enums.NotificationChannel;
import io.github.abdulmalikalayande.beacon.api.enums.SuppressionReason;

import java.time.Instant;

/**
 * An entry on the suppression list. A contact on this list is blocked from
 * receiving notifications on the given channel.
 *
 * @param contact the suppressed email address or phone number
 * @param channel the channel the suppression applies to
 * @param reason  why the contact was suppressed
 * @param addedAt when the suppression was recorded
 */
public record SuppressionEntry(
        String contact,
        NotificationChannel channel,
        SuppressionReason reason,
        Instant addedAt
) {}