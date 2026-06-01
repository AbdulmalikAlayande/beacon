package io.github.abdulmalikalayande.beacon.api.port;

import io.github.abdulmalikalayande.beacon.api.enums.ProviderName;

import java.util.Map;

/**
 * Processes inbound delivery webhooks from providers.
 *
 * <p><b>Library provides the default implementation.</b> Validates the provider
 * signature, normalizes the payload, and emits the resulting status change into
 * the pipeline (updating delivery status and, on bounces/complaints, the
 * suppression list). Implementations must validate the signature before acting on
 * the payload.
 */
public interface WebhookHandler {

    /**
     * Handle one inbound webhook.
     *
     * @param provider  the provider the webhook claims to be from
     * @param payload   the raw webhook fields
     * @param signature the signature header for authenticity verification
     * @throws io.github.abdulmalikalayande.beacon.api.exception.InvalidWebhookSignatureException
     *         if the signature cannot be verified
     */
    void handle(ProviderName provider, Map<String, String> payload, String signature);
}