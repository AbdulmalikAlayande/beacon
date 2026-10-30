package io.github.abdulmalikalayande.beacon.core.circuitbreaker;

import io.github.abdulmalikalayande.beacon.api.enums.ProviderName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DefaultProviderCircuitBreaker")
class DefaultProviderCircuitBreakerTest {

    private MutableClock clock;
    private DefaultProviderCircuitBreaker circuitBreaker;

    private static final int DEFAULT_FAILURE_THRESHOLD = 5;
    private static final Duration DEFAULT_WAIT_DURATION = Duration.ofSeconds(60);
    private static final int DEFAULT_HALF_OPEN_TRIALS = 3;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-30T10:00:00Z"), ZoneId.of("UTC"));
        circuitBreaker = new DefaultProviderCircuitBreaker(
                clock,
                DEFAULT_FAILURE_THRESHOLD,
                DEFAULT_WAIT_DURATION,
                DEFAULT_HALF_OPEN_TRIALS
        );
    }

    @Nested
    @DisplayName("Initial State & Normal (Closed) Operation")
    class ClosedStateTests {

        @Test
        @DisplayName("Newly checked provider starts in CLOSED state and isOpen returns false")
        void initialState_isClosed() {
            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isFalse();
            assertThat(circuitBreaker.getState(ProviderName.TWILIO))
                    .isEqualTo(DefaultProviderCircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("Successes in CLOSED state keep the circuit CLOSED")
        void recordSuccess_remainsClosed() {
            circuitBreaker.recordSuccess(ProviderName.TWILIO);
            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isFalse();
            assertThat(circuitBreaker.getState(ProviderName.TWILIO))
                    .isEqualTo(DefaultProviderCircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("Failures below threshold keep the circuit CLOSED")
        void failuresBelowThreshold_remainsClosed() {
            for (int i = 0; i < DEFAULT_FAILURE_THRESHOLD - 1; i++) {
                circuitBreaker.recordFailure(ProviderName.TWILIO);
            }

            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isFalse();
            assertThat(circuitBreaker.getState(ProviderName.TWILIO))
                    .isEqualTo(DefaultProviderCircuitBreaker.State.CLOSED);
            assertThat(circuitBreaker.getFailureCount(ProviderName.TWILIO))
                    .isEqualTo(DEFAULT_FAILURE_THRESHOLD - 1);
        }

        @Test
        @DisplayName("A success resets the consecutive failure count in CLOSED state")
        void successResetsFailureCount() {
            circuitBreaker.recordFailure(ProviderName.TWILIO);
            circuitBreaker.recordFailure(ProviderName.TWILIO);
            assertThat(circuitBreaker.getFailureCount(ProviderName.TWILIO)).isEqualTo(2);

            circuitBreaker.recordSuccess(ProviderName.TWILIO);
            assertThat(circuitBreaker.getFailureCount(ProviderName.TWILIO)).isEqualTo(0);
            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isFalse();
        }
    }

    @Nested
    @DisplayName("Tripping to OPEN State")
    class TrippingTests {

        @Test
        @DisplayName("Reaching failure threshold trips circuit to OPEN and isOpen returns true")
        void reachingThreshold_tripsToOpen() {
            for (int i = 0; i < DEFAULT_FAILURE_THRESHOLD; i++) {
                circuitBreaker.recordFailure(ProviderName.TWILIO);
            }

            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isTrue();
            assertThat(circuitBreaker.getState(ProviderName.TWILIO))
                    .isEqualTo(DefaultProviderCircuitBreaker.State.OPEN);
        }

        @Test
        @DisplayName("Circuit remains OPEN before waitDuration has elapsed")
        void remainsOpenBeforeWaitDurationExpires() {
            tripCircuit(ProviderName.TWILIO);

            // Advance clock by 30 seconds (less than 60s wait duration)
            clock.advance(Duration.ofSeconds(30));

            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isTrue();
            assertThat(circuitBreaker.getState(ProviderName.TWILIO))
                    .isEqualTo(DefaultProviderCircuitBreaker.State.OPEN);
        }
    }

    @Nested
    @DisplayName("HALF_OPEN Transition & Probing")
    class HalfOpenTests {

        @Test
        @DisplayName("When waitDuration expires, isOpen transitions to HALF_OPEN and returns false to allow trial")
        void waitDurationExpires_transitionsToHalfOpen() {
            tripCircuit(ProviderName.TWILIO);

            // Advance clock past the 60s wait duration
            clock.advance(Duration.ofSeconds(61));

            // isOpen should evaluate the timeout, transition to HALF_OPEN, and return false for the probe
            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isFalse();
            assertThat(circuitBreaker.getState(ProviderName.TWILIO))
                    .isEqualTo(DefaultProviderCircuitBreaker.State.HALF_OPEN);
        }

        @Test
        @DisplayName("Any failure in HALF_OPEN immediately trips back to OPEN for a new wait cycle")
        void failureInHalfOpen_tripsBackToOpen() {
            tripCircuit(ProviderName.TWILIO);
            clock.advance(Duration.ofSeconds(61));

            // Trigger transition to HALF_OPEN
            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isFalse();
            assertThat(circuitBreaker.getState(ProviderName.TWILIO))
                    .isEqualTo(DefaultProviderCircuitBreaker.State.HALF_OPEN);

            // Trial probe fails
            circuitBreaker.recordFailure(ProviderName.TWILIO);

            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isTrue();
            assertThat(circuitBreaker.getState(ProviderName.TWILIO))
                    .isEqualTo(DefaultProviderCircuitBreaker.State.OPEN);

            // Verify a new wait duration is required
            clock.advance(Duration.ofSeconds(30));
            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isTrue();

            clock.advance(Duration.ofSeconds(31));
            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isFalse();
            assertThat(circuitBreaker.getState(ProviderName.TWILIO))
                    .isEqualTo(DefaultProviderCircuitBreaker.State.HALF_OPEN);
        }

        @Test
        @DisplayName("Successful trial calls in HALF_OPEN close the circuit once permitted threshold is reached")
        void successfulTrials_closesCircuit() {
            tripCircuit(ProviderName.TWILIO);
            clock.advance(Duration.ofSeconds(61));

            // Transition to HALF_OPEN
            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isFalse();

            // Record required consecutive trial successes (threshold = 3)
            circuitBreaker.recordSuccess(ProviderName.TWILIO);
            assertThat(circuitBreaker.getState(ProviderName.TWILIO))
                    .isEqualTo(DefaultProviderCircuitBreaker.State.HALF_OPEN);

            circuitBreaker.recordSuccess(ProviderName.TWILIO);
            assertThat(circuitBreaker.getState(ProviderName.TWILIO))
                    .isEqualTo(DefaultProviderCircuitBreaker.State.HALF_OPEN);

            circuitBreaker.recordSuccess(ProviderName.TWILIO);
            // Reached 3 successes -> should be CLOSED
            assertThat(circuitBreaker.getState(ProviderName.TWILIO))
                    .isEqualTo(DefaultProviderCircuitBreaker.State.CLOSED);
            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isFalse();
            assertThat(circuitBreaker.getFailureCount(ProviderName.TWILIO)).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Provider Isolation")
    class ProviderIsolationTests {

        @Test
        @DisplayName("Tripping one provider does not affect any other provider")
        void trippingOneProvider_doesNotAffectOthers() {
            tripCircuit(ProviderName.TWILIO);

            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isTrue();
            assertThat(circuitBreaker.isOpen(ProviderName.TERMII)).isFalse();
            assertThat(circuitBreaker.isOpen(ProviderName.SENDGRID)).isFalse();
            assertThat(circuitBreaker.isOpen(ProviderName.FIREBASE)).isFalse();
            assertThat(circuitBreaker.isOpen(ProviderName.MAILGUN)).isFalse();
            assertThat(circuitBreaker.isOpen(ProviderName.CUSTOM)).isFalse();
        }

        @Test
        @DisplayName("Each provider maintains its own independent failure counts and state")
        void independentFailureCounts() {
            circuitBreaker.recordFailure(ProviderName.TWILIO);
            circuitBreaker.recordFailure(ProviderName.TWILIO);
            circuitBreaker.recordFailure(ProviderName.SENDGRID);

            assertThat(circuitBreaker.getFailureCount(ProviderName.TWILIO)).isEqualTo(2);
            assertThat(circuitBreaker.getFailureCount(ProviderName.SENDGRID)).isEqualTo(1);
            assertThat(circuitBreaker.getFailureCount(ProviderName.TERMII)).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Validation & Defensive Guards")
    class ValidationTests {

        @Test
        @DisplayName("Null provider passed to isOpen throws IllegalArgumentException")
        void nullProvider_isOpenThrows() {
            assertThatThrownBy(() -> circuitBreaker.isOpen(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Provider must not be null");
        }

        @Test
        @DisplayName("Null provider passed to recordSuccess throws IllegalArgumentException")
        void nullProvider_recordSuccessThrows() {
            assertThatThrownBy(() -> circuitBreaker.recordSuccess(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Provider must not be null");
        }

        @Test
        @DisplayName("Null provider passed to recordFailure throws IllegalArgumentException")
        void nullProvider_recordFailureThrows() {
            assertThatThrownBy(() -> circuitBreaker.recordFailure(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Provider must not be null");
        }

        @Test
        @DisplayName("Null clock in constructor throws NullPointerException")
        void nullClock_throwsNPE() {
            assertThatThrownBy(() -> new DefaultProviderCircuitBreaker(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Clock must not be null");
        }

        @Test
        @DisplayName("Invalid configuration values in constructor throw IllegalArgumentException")
        void invalidConfig_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> new DefaultProviderCircuitBreaker(clock, 0, DEFAULT_WAIT_DURATION, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Failure threshold must be positive");

            assertThatThrownBy(() -> new DefaultProviderCircuitBreaker(clock, 5, Duration.ZERO, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Wait duration must be positive");

            assertThatThrownBy(() -> new DefaultProviderCircuitBreaker(clock, 5, Duration.ofSeconds(-10), 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Wait duration must be positive");

            assertThatThrownBy(() -> new DefaultProviderCircuitBreaker(clock, 5, DEFAULT_WAIT_DURATION, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Permitted half-open trials must be positive");
        }
    }

    @Nested
    @DisplayName("Manual Reset Operations")
    class ResetTests {

        @Test
        @DisplayName("reset() resets a specific provider back to CLOSED with 0 failures")
        void resetSpecificProvider() {
            tripCircuit(ProviderName.TWILIO);
            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isTrue();

            circuitBreaker.reset(ProviderName.TWILIO);

            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isFalse();
            assertThat(circuitBreaker.getState(ProviderName.TWILIO))
                    .isEqualTo(DefaultProviderCircuitBreaker.State.CLOSED);
            assertThat(circuitBreaker.getFailureCount(ProviderName.TWILIO)).isEqualTo(0);
        }

        @Test
        @DisplayName("resetAll() resets all registered providers")
        void resetAllProviders() {
            tripCircuit(ProviderName.TWILIO);
            tripCircuit(ProviderName.SENDGRID);

            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isTrue();
            assertThat(circuitBreaker.isOpen(ProviderName.SENDGRID)).isTrue();

            circuitBreaker.resetAll();

            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isFalse();
            assertThat(circuitBreaker.isOpen(ProviderName.SENDGRID)).isFalse();
        }
    }

    @Nested
    @DisplayName("Concurrency & Thread Safety")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent failure recordings from multiple threads trip the breaker correctly without corruption")
        void concurrentFailures_tripsCorrectly() throws InterruptedException {
            int threadCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            try {
                CountDownLatch readyLatch = new CountDownLatch(threadCount);
                CountDownLatch startLatch = new CountDownLatch(1);
                CountDownLatch doneLatch = new CountDownLatch(threadCount);

                for (int i = 0; i < threadCount; i++) {
                    executor.submit(() -> {
                        readyLatch.countDown();
                        try {
                            startLatch.await();
                            circuitBreaker.recordFailure(ProviderName.TWILIO);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            doneLatch.countDown();
                        }
                    });
                }

                readyLatch.await();
                startLatch.countDown();
                doneLatch.await();

                assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isTrue();
                assertThat(circuitBreaker.getState(ProviderName.TWILIO))
                        .isEqualTo(DefaultProviderCircuitBreaker.State.OPEN);
            } finally {
                executor.shutdown();
            }
        }
    }

    private void tripCircuit(ProviderName provider) {
        for (int i = 0; i < DEFAULT_FAILURE_THRESHOLD; i++) {
            circuitBreaker.recordFailure(provider);
        }
    }

    /**
     * Mutable Clock fixture for deterministic time manipulation in tests.
     */
    static class MutableClock extends Clock {
        private Instant currentInstant;
        private final ZoneId zone;

        MutableClock(Instant initialInstant, ZoneId zone) {
            this.currentInstant = initialInstant;
            this.zone = zone;
        }

        public void advance(Duration duration) {
            this.currentInstant = this.currentInstant.plus(duration);
        }

        public void setInstant(Instant instant) {
            this.currentInstant = instant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(currentInstant, zone);
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }
    }
}
