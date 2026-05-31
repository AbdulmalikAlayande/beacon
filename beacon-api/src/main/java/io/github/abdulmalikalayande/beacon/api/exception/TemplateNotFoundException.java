package io.github.abdulmalikalayande.beacon.api.exception;

import io.github.abdulmalikalayande.beacon.api.enums.NotificationChannel;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationType;

/**
 * Thrown when no template can be resolved for a given notification type and
 * channel combination. Usually indicates a configuration gap — a template was
 * never defined for this pairing.
 */
public class TemplateNotFoundException extends NotificationException {

    private final NotificationType type;
    private final NotificationChannel channel;

    public TemplateNotFoundException(NotificationType type, NotificationChannel channel) {
        super("No template found for type=" + type + " channel=" + channel);
        this.type = type;
        this.channel = channel;
    }

    public NotificationType getType() {
        return type;
    }

    public NotificationChannel getChannel() {
        return channel;
    }
}