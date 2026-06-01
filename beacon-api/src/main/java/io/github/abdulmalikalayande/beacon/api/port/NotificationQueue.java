package io.github.abdulmalikalayande.beacon.api.port;

import io.github.abdulmalikalayande.beacon.api.dto.DeliveryTask;

import java.time.Instant;

/**
 * The queue that holds delivery tasks between routing and delivery.
 *
 * <p><b>Library provides implementations; pluggable.</b> The default is a durable
 * database-backed queue requiring no extra infrastructure. Hosts can swap in
 * Kafka, RabbitMQ, or Pulsar implementations for higher throughput without
 * changing any other code.
 */
public interface NotificationQueue {

    /**
     * Enqueue a task for delivery as soon as a worker is available.
     *
     * @param task the delivery task
     */
    void enqueue(DeliveryTask task);

    /**
     * Enqueue a task to become eligible for delivery only at or after the given
     * time. Used for scheduled sends and quiet-hours deferral.
     *
     * @param task      the delivery task
     * @param deliverAt the earliest time the task may be delivered
     */
    void enqueueDelayed(DeliveryTask task, Instant deliverAt);

    /**
     * Move a task to the dead letter queue after all retries and fallbacks are
     * exhausted. The task's context remains encrypted at rest.
     *
     * @param task   the failed delivery task
     * @param reason a masked, PII-free description of why it failed
     */
    void enqueueDead(DeliveryTask task, String reason);
}