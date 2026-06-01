package io.github.abdulmalikalayande.beacon.api.port;

import io.github.abdulmalikalayande.beacon.api.dto.RenderedNotification;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationChannel;
import io.github.abdulmalikalayande.beacon.api.enums.ProviderName;
import io.github.abdulmalikalayande.beacon.api.enums.WebhookEventType;

import java.util.Map;

/**
 * Adapter to a single third-party delivery provider (Twilio, SendGrid, Firebase,
 * etc.).
 *
 * <p><b>Library provides default adapters; hosts may add their own.</b> This is
 * the extension point for plugging in any provider — implement it, register it as
 * a bean, and Beacon will route the matching channel through it.
 */
public interface NotificationProvider {

    /** @return which provider this adapter represents */
    ProviderName getName();

    /** @return the channel this provider delivers on */
    NotificationChannel getChannel();

    /**
     * Send a rendered notification to the provider.
     *
     * @param notification the fully rendered, ready-to-send payload
     * @return the provider's message identifier for the accepted message
     * @throws io.github.abdulmalikalayande.beacon.api.exception.ProviderException
     *         if the provider rejects or fails the send
     */
    String send(RenderedNotification notification);

    /**
     * Verify the authenticity of an incoming webhook from this provider.
     *
     * @param payload   the raw webhook body
     * @param signature the signature header supplied by the provider
     * @param secret    the configured signing secret for this provider
     * @return {@code true} if the signature is valid
     */
    boolean verifyWebhookSignature(String payload, String signature, String secret);

    /**
     * Translate a raw provider webhook payload into a normalized event type.
     *
     * @param rawPayload the provider's webhook fields
     * @return the normalized event type
     */
    WebhookEventType parseWebhookEvent(Map<String, String> rawPayload);
}