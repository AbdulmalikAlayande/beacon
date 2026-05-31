package io.github.abdulmalikalayande.beacon.api.dto;

import io.github.abdulmalikalayande.beacon.api.enums.NotificationChannel;

import java.time.LocalTime;
import java.util.Set;

/**
 * A user's notification preferences and contact details, returned by the host's
 * {@code UserPreferenceResolver}.
 *
 * <p>The host is responsible for decrypting any encrypted fields (email, phone)
 * from their own store before returning this object. Beacon treats the values
 * as plaintext at the point of delivery and re-encrypts context data within its
 * own pipeline as needed.
 *
 * @param userId          the host's identifier for this user
 * @param email           the user's email address, or {@code null} if none
 * @param phoneNumber     the user's phone number in E.164 format, or {@code null}
 * @param pushToken       the user's push token (e.g. FCM token), or {@code null}
 * @param enabledChannels the channels this user has opted into
 * @param timezone        the user's IANA timezone (e.g. {@code "Africa/Lagos"})
 * @param quietHoursStart start of the user's quiet window in local time, or {@code null}
 * @param quietHoursEnd   end of the user's quiet window in local time, or {@code null}
 */
public record NotificationPreference(
        String userId,
        String email,
        String phoneNumber,
        String pushToken,
        Set<NotificationChannel> enabledChannels,
        String timezone,
        LocalTime quietHoursStart,
        LocalTime quietHoursEnd
) {
    /**
     * @return {@code true} if the user has opted into the given channel
     */
    public boolean isChannelEnabled(NotificationChannel channel) {
        return enabledChannels != null && enabledChannels.contains(channel);
    }

    /**
     * @return {@code true} if this user has a configured quiet-hours window
     */
    public boolean hasQuietHours() {
        return quietHoursStart != null && quietHoursEnd != null;
    }
}