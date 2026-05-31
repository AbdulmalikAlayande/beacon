package io.github.abdulmalikalayande.beacon.api.dto;

import io.github.abdulmalikalayande.beacon.api.enums.NotificationChannel;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationType;

/**
 * A template for a given notification type and channel. Template strings support
 * {@code ${variable}} placeholders that are substituted with values from the
 * notification's context map at render time.
 *
 * <p>Field relevance depends on the channel: {@code subjectTemplate} applies to
 * email, {@code titleTemplate} applies to push, and {@code bodyTemplate} applies
 * to all channels.
 *
 * @param type            the notification type this template is for
 * @param channel         the channel this template renders
 * @param subjectTemplate the subject template (email only), or {@code null}
 * @param titleTemplate   the title template (push only), or {@code null}
 * @param bodyTemplate    the body template (all channels)
 */
public record NotificationTemplate(
        NotificationType type,
        NotificationChannel channel,
        String subjectTemplate,
        String titleTemplate,
        String bodyTemplate
) {}