package io.github.abdulmalikalayande.beacon.api.dto;

import io.github.abdulmalikalayande.beacon.api.enums.NotificationChannel;

import java.util.Map;

/**
 * A notification after its template has been rendered, ready to hand to a
 * provider. Produced by the {@code TemplateEngine} at the worker level, just
 * before the provider call.
 *
 * <p>Field relevance depends on the channel: {@code subject} applies to email,
 * {@code title} applies to push, and {@code body} applies to all three.
 *
 * @param channel  the channel this rendered payload targets
 * @param to       the destination — email address, phone number, or push token
 * @param subject  the subject line (email only), otherwise {@code null}
 * @param title    the title (push only), otherwise {@code null}
 * @param body     the rendered body — SMS text, email HTML, or push body
 * @param metadata optional provider-specific metadata (e.g. headers, tags)
 */
public record RenderedNotification(
        NotificationChannel channel,
        String to,
        String subject,
        String title,
        String body,
        Map<String, String> metadata
) {}