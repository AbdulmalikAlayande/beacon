package io.github.abdulmalikalayande.beacon.api.enums;

/**
 * The queue backend Beacon uses to hold delivery tasks between routing and
 * delivery.
 *
 * <p>{@link #DATABASE} is the zero-infrastructure default and requires nothing
 * beyond the PostgreSQL database Beacon already uses. The remaining options are
 * opt-in for higher throughput and require the corresponding broker to be
 * available and configured by the host.
 */
public enum QueueType {
    
    /**
     * Default. The {@code notification_status} table doubles as the queue, with
     * workers polling via {@code SELECT ... FOR UPDATE SKIP LOCKED}. Durable,
     * survives restarts, and works across horizontally scaled host instances
     * with no extra infrastructure.
     */
    DATABASE,
    
    /** RabbitMQ broker. Opt-in for higher throughput. */
    RABBITMQ,
    
    /** Apache Kafka. Opt-in for very high throughput and event streaming. */
    KAFKA,
    
    /** Apache Pulsar. Opt-in alternative broker. */
    PULSAR,
    
    /**
     * In-process queue (non-durable). Tasks are lost on restart.
     * Intended for tests and local development ONLY — never for production,
     * as it violates the no-missed-sends guarantee.
     */
    IN_MEMORY
}