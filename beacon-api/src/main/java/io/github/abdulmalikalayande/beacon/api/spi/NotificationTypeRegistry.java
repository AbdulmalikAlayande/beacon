package io.github.abdulmalikalayande.beacon.api.spi;

import io.github.abdulmalikalayande.beacon.api.enums.NotificationChannel;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationPriority;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationType;

import java.util.List;

/**
 * Supplies the priority and supported channels for notification types.
 *
 * <p><b>Host implements this (optional).</b> Beacon ships sensible defaults for
 * its built-in {@link NotificationType} values. A host only needs to implement
 * this when it uses {@link NotificationType#CUSTOM} or wants to override the
 * defaults (e.g. make {@code PROMO} support push as well as email).
 */
public interface NotificationTypeRegistry {

    /**
     * @param type the notification type
     * @return the priority Beacon should treat this type as having
     */
    NotificationPriority getPriority(NotificationType type);

    /**
     * @param type the notification type
     * @return the channels this type is allowed to be delivered on
     */
    List<NotificationChannel> getSupportedChannels(NotificationType type);
}