package io.github.abdulmalikalayande.beacon.api.port;

import io.github.abdulmalikalayande.beacon.api.enums.ProviderName;

/**
 * Throttles outbound requests to each provider to stay within the provider's
 * documented API rate limits.
 *
 * <p><b>Library provides the default (in-process token bucket).</b> This sits
 * between workers and providers so Beacon never overruns a provider and triggers
 * {@code HTTP 429} responses — which would otherwise be misread by the circuit
 * breaker as an outage.
 */
public interface ProviderRateLimiter {

    /**
     * Acquire permission to make one request to the given provider, blocking if
     * necessary until the rate limit allows it.
     *
     * @param provider the provider about to be called
     */
    void acquire(ProviderName provider);

    /**
     * Configure the allowed request rate for a provider.
     *
     * @param provider          the provider
     * @param requestsPerSecond the maximum sustained requests per second
     */
    void configure(ProviderName provider, int requestsPerSecond);
}