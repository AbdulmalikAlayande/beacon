package io.github.abdulmalikalayande.beacon.api.port;

/**
 * Periodic sweep that catches notifications which slipped through the cracks —
 * stuck in {@code PROCESSING} too long, or {@code SENT} but never confirmed
 * {@code DELIVERED} by a provider webhook.
 *
 * <p><b>Library provides the default implementation.</b> Typically invoked on a
 * schedule. It reconciles Beacon's records against provider status and applies
 * corrections or escalations.
 */
public interface ReconciliationJob {

    /**
     * Run one reconciliation pass.
     */
    void run();
}