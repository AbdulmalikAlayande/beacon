package io.github.abdulmalikalayande.beacon.api.port;

import io.github.abdulmalikalayande.beacon.api.dto.NotificationPreference;
import io.github.abdulmalikalayande.beacon.api.dto.NotificationTemplate;
import io.github.abdulmalikalayande.beacon.api.dto.RenderedNotification;

import java.util.Map;

/**
 * Renders a template into a ready-to-send payload by substituting context
 * variables.
 *
 * <p><b>Library provides the default implementation</b> (supports
 * {@code ${variable}} substitution). Rendering happens at the worker, just before
 * the provider call, so heavy rendered bodies never sit in the queue.
 */
public interface TemplateEngine {

    /**
     * Render a template for a specific recipient.
     *
     * @param template   the template to render
     * @param preference the recipient's resolved preferences (supplies the
     *                   destination address and any per-user values)
     * @param context    the dynamic values to substitute into the template
     * @return the rendered, ready-to-send notification
     */
    RenderedNotification render(
            NotificationTemplate template,
            NotificationPreference preference,
            Map<String, String> context
    );
}