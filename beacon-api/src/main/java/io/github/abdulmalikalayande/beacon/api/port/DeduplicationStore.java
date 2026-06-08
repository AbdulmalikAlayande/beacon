package io.github.abdulmalikalayande.beacon.api.port;

/**
 * Backs Beacon's duplicate-prevention guarantees.
 *
 * <p><b>Library provides implementations; pluggable.</b> Two concerns live here:
 * upstream idempotency (has this trigger been seen before?) and the delivery lock
 * (is a worker already handling this exact notification right now?).
 *
 * <p>The default implementation uses the database — a unique constraint for
 * idempotency and {@code SELECT ... FOR UPDATE SKIP LOCKED} for the delivery
 * lock. A Redis-backed implementation using {@code SETNX} is available for
 * higher-concurrency deployments. Both guarantee the lock acquisition is
 * atomic.
 */
public interface DeduplicationStore {
    
    /**
     * @param idempotencyKey the host-supplied key
     * @return {@code true} if this key has already been seen
     */
    boolean isSeen(String idempotencyKey);

    /**
     * Record a key as seen so future submissions are recognized as duplicates.
     *
     * @param idempotencyKey the key to remember
     */
    void markSeen(String idempotencyKey);

    /**
     * Atomically attempt to acquire the delivery lock for a notification.
     *
     * @param notificationId the notification to lock
     * @return {@code true} if the lock was acquired, {@code false} if another
     *         worker already holds it
     */
    boolean acquireDeliveryLock(String notificationId);

    /**
     * Release a previously acquired delivery lock.
     *
     * @param notificationId the notification to unlock
     */
    void releaseDeliveryLock(String notificationId);
}