package io.github.abdulmalikalayande.beacon.core.circuitbreaker;

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
 * <p>Tracks delivery provider availability using a 3-state finite state machine
 * ({@link State#CLOSED}, {@link State#OPEN}, {@link State#HALF_OPEN}) per provider.
 * When a provider suffers consecutive failures reaching {@code failureThreshold},
 * its circuit trips to {@code OPEN}, short-circuiting calls so workers can immediately
 * failover to a fallback provider without wasting connection/request timeouts.
 *
 * <p>After {@code waitDurationInOpenState} elapses (measured via the injected {@link Clock}),
 * the breaker enters {@code HALF_OPEN} to trial a controlled number of requests. If all
 * {@code permittedHalfOpenTrials} succeed, the circuit resets to {@code CLOSED}. Any failure
 * in {@code HALF_OPEN} immediately returns the circuit to {@code OPEN}.
 *
 * <p><b>Important architectural invariant:</b> HTTP 429 (Too Many Requests) responses
 * must be handled by the rate limiter and NOT recorded as circuit-breaker failures.
 */
public class DefaultProviderCircuitBreaker implements ProviderCircuitBreaker {

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    public static final int DEFAULT_FAILURE_THRESHOLD = 5;
    public static final Duration DEFAULT_WAIT_DURATION = Duration.ofSeconds(60);
    public static final int DEFAULT_HALF_OPEN_TRIALS = 3;

    private final Clock clock;
    private final int failureThreshold;
    private final Duration waitDurationInOpenState;
    private final int permittedHalfOpenTrials;

    private final ConcurrentMap<ProviderName, ProviderCircuitState> providerStates = new ConcurrentHashMap<>();

    /**
     * Creates a circuit breaker with default settings:
     * <ul>
     *   <li>failureThreshold: 5 consecutive failures</li>
     *   <li>waitDurationInOpenState: 60 seconds</li>
     *   <li>permittedHalfOpenTrials: 3 consecutive trial successes</li>
     * </ul>
     *
     * @param clock clock for time evaluations (must not be null)
     */
    public DefaultProviderCircuitBreaker(Clock clock) {
        this(clock, DEFAULT_FAILURE_THRESHOLD, DEFAULT_WAIT_DURATION, DEFAULT_HALF_OPEN_TRIALS);
    }

    /**
     * Creates a fully configured circuit breaker.
     *
     * @param clock                   clock for time evaluations (must not be null)
     * @param failureThreshold        number of consecutive failures to trip open (&gt; 0)
     * @param waitDurationInOpenState duration to wait before probing recovery (&gt; 0)
     * @param permittedHalfOpenTrials consecutive trial successes required to close (&gt; 0)
     */
    public DefaultProviderCircuitBreaker(Clock clock, int failureThreshold,
                                         Duration waitDurationInOpenState, int permittedHalfOpenTrials) {
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("Failure threshold must be positive");
        }
        if (waitDurationInOpenState == null || waitDurationInOpenState.isNegative() || waitDurationInOpenState.isZero()) {
            throw new IllegalArgumentException("Wait duration must be positive");
        }
        if (permittedHalfOpenTrials <= 0) {
            throw new IllegalArgumentException("Permitted half-open trials must be positive");
        }

        this.failureThreshold = failureThreshold;
        this.waitDurationInOpenState = waitDurationInOpenState;
        this.permittedHalfOpenTrials = permittedHalfOpenTrials;
    }

    @Override
    public boolean isOpen(ProviderName provider) {
        return getStateFor(provider).isOpen();
    }

    @Override
    public void recordSuccess(ProviderName provider) {
        getStateFor(provider).recordSuccess();
    }

    @Override
    public void recordFailure(ProviderName provider) {
        getStateFor(provider).recordFailure();
    }

    /**
     * Returns the current state of the circuit for the given provider.
     *
     * @param provider the provider to check
     * @return the circuit state
     */
    public State getState(ProviderName provider) {
        return getStateFor(provider).getState();
    }

    /**
     * Returns the consecutive failure count for the given provider in CLOSED state.
     *
     * @param provider the provider to check
     * @return current failure count
     */
    public int getFailureCount(ProviderName provider) {
        return getStateFor(provider).getFailureCount();
    }

    /**
     * Resets the breaker for the given provider back to CLOSED.
     *
     * @param provider the provider to reset
     */
    public void reset(ProviderName provider) {
        getStateFor(provider).reset();
    }

    /**
     * Resets all provider circuits back to CLOSED.
     */
    public void resetAll() {
        providerStates.values().forEach(ProviderCircuitState::reset);
    }

    private ProviderCircuitState getStateFor(ProviderName provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Provider must not be null");
        }
        return providerStates.computeIfAbsent(provider, p -> new ProviderCircuitState(clock.instant()));
    }

    /**
     * Thread-safe state tracker for an individual provider.
     */
    private class ProviderCircuitState {
        private State state = State.CLOSED;
        private int consecutiveFailures = 0;
        private int consecutiveSuccesses = 0;
        private Instant lastStateChange;

        ProviderCircuitState(Instant initialTime) {
            this.lastStateChange = initialTime;
        }

        synchronized boolean isOpen() {
            if (state == State.OPEN) {
                Instant now = clock.instant();
                if (!now.isBefore(lastStateChange.plus(waitDurationInOpenState))) {
                    state = State.HALF_OPEN;
                    lastStateChange = now;
                    consecutiveSuccesses = 0;
                    return false;
                }
                return true;
            }
            return false;
        }

        synchronized void recordSuccess() {
            if (state == State.HALF_OPEN) {
                consecutiveSuccesses++;
                if (consecutiveSuccesses >= permittedHalfOpenTrials) {
                    state = State.CLOSED;
                    consecutiveFailures = 0;
                    consecutiveSuccesses = 0;
                    lastStateChange = clock.instant();
                }
            } else if (state == State.CLOSED) {
                consecutiveFailures = 0;
            }
        }

        synchronized void recordFailure() {
            Instant now = clock.instant();
            if (state == State.HALF_OPEN) {
                state = State.OPEN;
                lastStateChange = now;
                consecutiveSuccesses = 0;
            } else if (state == State.CLOSED) {
                consecutiveFailures++;
                if (consecutiveFailures >= failureThreshold) {
                    state = State.OPEN;
                    lastStateChange = now;
                }
            }
        }

        synchronized State getState() {
            if (state == State.OPEN) {
                Instant now = clock.instant();
                if (!now.isBefore(lastStateChange.plus(waitDurationInOpenState))) {
                    state = State.HALF_OPEN;
                    lastStateChange = now;
                    consecutiveSuccesses = 0;
                }
            }
            return state;
        }

        synchronized int getFailureCount() {
            return consecutiveFailures;
        }

        synchronized void reset() {
            state = State.CLOSED;
            consecutiveFailures = 0;
            consecutiveSuccesses = 0;
            lastStateChange = clock.instant();
        }
    }
}
