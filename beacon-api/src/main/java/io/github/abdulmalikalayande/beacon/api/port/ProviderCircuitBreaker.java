package io.github.abdulmalikalayande.beacon.api.port;

import io.github.abdulmalikalayande.beacon.api.enums.ProviderName;

/**
 * Tracks provider health and short-circuits calls to a provider that is failing,
 * so the pipeline can fail over to a fallback instead of hammering a dead
 * provider.
 *
 * <p><b>Library provides the default implementation.</b> Note that rate-limit
 * responses ({@code HTTP 429}) are intentionally handled by the rate limiter, not
 * counted as circuit-breaker failures, so throttling never masquerades as an
 * outage.
 */
public interface ProviderCircuitBreaker {

    /**
     * @param provider the provider to check
     * @return {@code true} if the circuit is open (provider considered down)
     */
    boolean isOpen(ProviderName provider);

    /**
     * Record a successful call, contributing to closing the circuit.
     *
     * @param provider the provider that succeeded
     */
    void recordSuccess(ProviderName provider);

    /**
     * Record a failed call, contributing to opening the circuit.
     *
     * @param provider the provider that failed
     */
    void recordFailure(ProviderName provider);
}