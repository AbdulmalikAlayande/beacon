package io.github.abdulmalikalayande.beacon.api.spi;

import io.github.abdulmalikalayande.beacon.api.dto.NotificationTemplate;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationChannel;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationType;

/**
 * Resolves the template to use for a given notification type and channel.
 *
 * <p><b>Host implements this (optional).</b> By default Beacon resolves templates
 * from configuration/classpath. A host implements this when it wants templates to
 * come from elsewhere — its own database, a CMS, or a remote template service.
 */
public interface TemplateResolver {

    /**
     * @param type    the notification type
     * @param channel the delivery channel
     * @return the template for this pairing
     * @throws io.github.abdulmalikalayande.beacon.api.exception.TemplateNotFoundException
     *         if no template exists for the pairing
     */
    NotificationTemplate resolve(NotificationType type, NotificationChannel channel);
}