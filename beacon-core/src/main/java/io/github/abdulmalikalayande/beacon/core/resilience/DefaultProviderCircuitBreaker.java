package io.github.abdulmalikalayande.beacon.core.resilience;

import io.github.abdulmalikalayande.beacon.api.enums.ProviderName;
import io.github.abdulmalikalayande.beacon.api.port.ProviderCircuitBreaker;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Default in-memory implementation of {@link ProviderCircuitBreaker}.
 *
 * <p>Each provider has its own circuit with three states:
 * <ul>
 *   <li>{@link State#CLOSED}: the provider is healthy and every call is allowed.</li>
 *   <li>{@link State#OPEN}: {@code failureThreshold} consecutive failures were recorded,
 *       so {@link #isOpen(ProviderName)} returns {@code true} and workers fail over to a
 *       fallback provider without waiting on a provider that is down.</li>
 *   <li>{@link State#HALF_OPEN}: the cooldown has elapsed and exactly one probe call has
 *       been let through. Every other caller still sees the circuit as open until the probe
 *       reports back. A successful probe closes the circuit; a failed probe reopens it and
 *       starts a new cooldown.</li>
 * </ul>
 *
 * <p>The probe is granted by {@link #isOpen(ProviderName)} itself: the first caller to ask
 * after the cooldown receives {@code false} and is expected to make the call and then report
 * the outcome through {@link #recordSuccess(ProviderName)} or {@link #recordFailure(ProviderName)}.
 * If the probe never reports back (for example, the worker crashed), a new probe is granted
 * once another cooldown has passed, so the circuit cannot stay half-open forever.
 *
 * <p>HTTP 429 responses must be handled by the rate limiter and must not be recorded here as
 * failures. The breaker does not inspect status codes; the caller decides what to record.
 *
 * <p>All time is read from the injected {@link Clock}.
 */
public class DefaultProviderCircuitBreaker implements ProviderCircuitBreaker {

    /** The state of a single provider's circuit. */
    public enum State {
        /** Healthy: calls are allowed. */
        CLOSED,
        /** Tripped: calls are short-circuited until the cooldown elapses. */
        OPEN,
        /** Probing: one trial call is in flight; all other calls are short-circuited. */
        HALF_OPEN
    }

    /** Consecutive failures that trip a closed circuit (issue #6). */
    public static final int DEFAULT_FAILURE_THRESHOLD = 5;

    /** How long a circuit stays open before a probe is allowed (issue #6). */
    public static final Duration DEFAULT_COOLDOWN = Duration.ofSeconds(30);

    private final Clock clock;
    private final int failureThreshold;
    private final Duration cooldownDuration;

    private final ConcurrentMap<ProviderName, ProviderCircuit> circuits = new ConcurrentHashMap<>();

    /**
     * Creates a circuit breaker with a failure threshold of {@value #DEFAULT_FAILURE_THRESHOLD}
     * and a cooldown of 30 seconds.
     *
     * @param clock clock used for all time evaluations (must not be null)
     */
    public DefaultProviderCircuitBreaker(Clock clock) {
        this(clock, DEFAULT_FAILURE_THRESHOLD, DEFAULT_COOLDOWN);
    }

    /**
     * Creates a fully configured circuit breaker.
     *
     * @param clock            clock used for all time evaluations (must not be null)
     * @param failureThreshold consecutive failures that trip the circuit (&gt; 0)
     * @param cooldownDuration how long the circuit stays open before a probe is allowed (&gt; 0)
     */
    public DefaultProviderCircuitBreaker(Clock clock, int failureThreshold, Duration cooldownDuration) {
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("Failure threshold must be positive");
        }
        if (cooldownDuration == null || cooldownDuration.isNegative() || cooldownDuration.isZero()) {
            throw new IllegalArgumentException("Cooldown duration must be positive");
        }
        this.failureThreshold = failureThreshold;
        this.cooldownDuration = cooldownDuration;
    }

    /**
     * Returns whether calls to the provider should be short-circuited.
     *
     * <p>When the cooldown of an open circuit has elapsed, the first caller receives
     * {@code false} and becomes the single probe; the circuit moves to
     * {@link State#HALF_OPEN} and every later caller receives {@code true} until the
     * probe's outcome is recorded.
     */
    @Override
    public boolean isOpen(ProviderName provider) {
        return circuitFor(provider).isOpen();
    }

    @Override
    public void recordSuccess(ProviderName provider) {
        circuitFor(provider).recordSuccess();
    }

    @Override
    public void recordFailure(ProviderName provider) {
        circuitFor(provider).recordFailure();
    }

    /**
     * Returns the stored state of the provider's circuit. Unlike {@link #isOpen(ProviderName)}
     * this is a pure read: it never moves the circuit to half-open or grants a probe.
     *
     * @param provider the provider to inspect
     * @return the current circuit state
     */
    public State getState(ProviderName provider) {
        return circuitFor(provider).state();
    }

    /**
     * Returns the number of consecutive failures recorded since the circuit last closed.
     *
     * @param provider the provider to inspect
     * @return the consecutive failure count
     */
    public int getFailureCount(ProviderName provider) {
        return circuitFor(provider).failureCount();
    }

    /**
     * Forces the provider's circuit back to {@link State#CLOSED} and clears its counters.
     *
     * @param provider the provider to reset
     */
    public void reset(ProviderName provider) {
        circuitFor(provider).reset();
    }

    /** Forces every known provider's circuit back to {@link State#CLOSED}. */
    public void resetAll() {
        circuits.values().forEach(ProviderCircuit::reset);
    }

    private ProviderCircuit circuitFor(ProviderName provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Provider must not be null");
        }
        return circuits.computeIfAbsent(provider, p -> new ProviderCircuit());
    }

    /**
     * State for one provider. Every method is synchronized on the instance, so a check and the
     * transition it causes (for example, granting the single probe) happen as one step, and
     * providers never block one another.
     */
    private final class ProviderCircuit {
        private State state = State.CLOSED;
        private int consecutiveFailures;
        /** When the circuit last opened; the cooldown is measured from here. */
        private Instant openedAt;
        /** When the current probe was granted; used to recover from a probe that never reports. */
        private Instant probeGrantedAt;

        synchronized boolean isOpen() {
            Instant now = clock.instant();
            switch (state) {
                case CLOSED:
                    return false;
                case OPEN:
                    if (cooldownElapsed(openedAt, now)) {
                        state = State.HALF_OPEN;
                        probeGrantedAt = now;
                        return false;
                    }
                    return true;
                case HALF_OPEN:
                    // A probe is in flight. Only if it has gone unanswered for a full cooldown
                    // is it treated as lost, and this caller becomes the new probe.
                    if (cooldownElapsed(probeGrantedAt, now)) {
                        probeGrantedAt = now;
                        return false;
                    }
                    return true;
                default:
                    throw new IllegalStateException("Unknown state " + state);
            }
        }

        synchronized void recordSuccess() {
            if (state == State.HALF_OPEN) {
                // One successful probe is enough to close the circuit (issue #6).
                close();
            } else if (state == State.CLOSED) {
                consecutiveFailures = 0;
            }
            // In OPEN, a late success from a call made before the circuit tripped is ignored.
        }

        synchronized void recordFailure() {
            if (state == State.HALF_OPEN) {
                open(clock.instant());
            } else if (state == State.CLOSED) {
                consecutiveFailures++;
                if (consecutiveFailures >= failureThreshold) {
                    open(clock.instant());
                }
            }
            // In OPEN, late failures are ignored so they do not extend the cooldown.
        }

        synchronized State state() {
            return state;
        }

        synchronized int failureCount() {
            return consecutiveFailures;
        }

        synchronized void reset() {
            close();
        }

        private void open(Instant now) {
            state = State.OPEN;
            openedAt = now;
            probeGrantedAt = null;
        }

        private void close() {
            state = State.CLOSED;
            consecutiveFailures = 0;
            openedAt = null;
            probeGrantedAt = null;
        }

        private boolean cooldownElapsed(Instant since, Instant now) {
            return !now.isBefore(since.plus(cooldownDuration));
        }
    }
}
