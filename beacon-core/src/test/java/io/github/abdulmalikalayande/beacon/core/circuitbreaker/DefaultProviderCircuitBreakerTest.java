package io.github.abdulmalikalayande.beacon.core.circuitbreaker;

import io.github.abdulmalikalayande.beacon.api.enums.ProviderName;
import io.github.abdulmalikalayande.beacon.core.circuitbreaker.DefaultProviderCircuitBreaker.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DefaultProviderCircuitBreaker")
class DefaultProviderCircuitBreakerTest {

    private static final int FAILURE_THRESHOLD = DefaultProviderCircuitBreaker.DEFAULT_FAILURE_THRESHOLD;
    private static final Duration COOLDOWN = DefaultProviderCircuitBreaker.DEFAULT_COOLDOWN;

    private MutableClock clock;
    private DefaultProviderCircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-30T10:00:00Z"));
        circuitBreaker = new DefaultProviderCircuitBreaker(clock);
    }

    @Nested
    @DisplayName("Defaults")
    class DefaultsTests {

        @Test
        @DisplayName("Defaults follow issue #6: 5 consecutive failures, 30-second cooldown")
        void defaultsMatchIssue() {
            assertThat(DefaultProviderCircuitBreaker.DEFAULT_FAILURE_THRESHOLD).isEqualTo(5);
            assertThat(DefaultProviderCircuitBreaker.DEFAULT_COOLDOWN).isEqualTo(Duration.ofSeconds(30));
        }
    }

    @Nested
    @DisplayName("CLOSED state")
    class ClosedStateTests {

        @Test
        @DisplayName("A provider that has never been seen is CLOSED and isOpen returns false")
        void initialState_isClosed() {
            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isFalse();
            assertThat(circuitBreaker.getState(ProviderName.TWILIO)).isEqualTo(State.CLOSED);
        }

        @Test
        @DisplayName("Failures below the threshold keep the circuit CLOSED")
        void failuresBelowThreshold_remainClosed() {
            recordFailures(ProviderName.TWILIO, FAILURE_THRESHOLD - 1);

            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isFalse();
            assertThat(circuitBreaker.getState(ProviderName.TWILIO)).isEqualTo(State.CLOSED);
            assertThat(circuitBreaker.getFailureCount(ProviderName.TWILIO)).isEqualTo(FAILURE_THRESHOLD - 1);
        }

        @Test
        @DisplayName("A success while CLOSED resets the consecutive failure count")
        void successResetsFailureCount() {
            recordFailures(ProviderName.TWILIO, FAILURE_THRESHOLD - 1);

            circuitBreaker.recordSuccess(ProviderName.TWILIO);
            recordFailures(ProviderName.TWILIO, FAILURE_THRESHOLD - 1);

            assertThat(circuitBreaker.getFailureCount(ProviderName.TWILIO)).isEqualTo(FAILURE_THRESHOLD - 1);
            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isFalse();
        }
    }

    @Nested
    @DisplayName("OPEN state")
    class OpenStateTests {

        @Test
        @DisplayName("Reaching the failure threshold opens the circuit")
        void reachingThreshold_opens() {
            trip(ProviderName.TWILIO);

            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isTrue();
            assertThat(circuitBreaker.getState(ProviderName.TWILIO)).isEqualTo(State.OPEN);
        }

        @Test
        @DisplayName("The circuit stays OPEN until the full cooldown has elapsed")
        void staysOpenDuringCooldown() {
            trip(ProviderName.TWILIO);

            clock.advance(COOLDOWN.minusMillis(1));

            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isTrue();
            assertThat(circuitBreaker.getState(ProviderName.TWILIO)).isEqualTo(State.OPEN);
        }

        @Test
        @DisplayName("Failures recorded while OPEN do not extend the cooldown")
        void lateFailuresDoNotExtendCooldown() {
            trip(ProviderName.TWILIO);
            clock.advance(COOLDOWN.minusSeconds(1));
            circuitBreaker.recordFailure(ProviderName.TWILIO);

            clock.advance(Duration.ofSeconds(1));

            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isFalse();
        }

        @Test
        @DisplayName("A success recorded while OPEN does not close the circuit")
        void lateSuccessDoesNotClose() {
            trip(ProviderName.TWILIO);

            circuitBreaker.recordSuccess(ProviderName.TWILIO);

            assertThat(circuitBreaker.getState(ProviderName.TWILIO)).isEqualTo(State.OPEN);
            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isTrue();
        }
    }

    @Nested
    @DisplayName("HALF_OPEN state: a single probe")
    class HalfOpenTests {

        @Test
        @DisplayName("After the cooldown, the first caller gets the probe and the circuit becomes HALF_OPEN")
        void firstCallerAfterCooldown_getsProbe() {
            trip(ProviderName.TWILIO);
            clock.advance(COOLDOWN);

            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isFalse();
            assertThat(circuitBreaker.getState(ProviderName.TWILIO)).isEqualTo(State.HALF_OPEN);
        }

        @Test
        @DisplayName("While the probe is in flight, every other caller still sees the circuit as open")
        void otherCallersDuringProbe_seeOpen() {
            trip(ProviderName.TWILIO);
            clock.advance(COOLDOWN);

            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isFalse();
            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isTrue();
            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isTrue();
            clock.advance(COOLDOWN.minusMillis(1));
            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isTrue();
        }

        @Test
        @DisplayName("One successful probe closes the circuit and clears the failure count")
        void successfulProbe_closes() {
            trip(ProviderName.TWILIO);
            clock.advance(COOLDOWN);
            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isFalse();

            circuitBreaker.recordSuccess(ProviderName.TWILIO);

            assertThat(circuitBreaker.getState(ProviderName.TWILIO)).isEqualTo(State.CLOSED);
            assertThat(circuitBreaker.getFailureCount(ProviderName.TWILIO)).isZero();
            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isFalse();
            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isFalse();
        }

        @Test
        @DisplayName("A failed probe reopens the circuit and starts a new full cooldown")
        void failedProbe_reopensWithNewCooldown() {
            trip(ProviderName.TWILIO);
            clock.advance(COOLDOWN);
            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isFalse();

            circuitBreaker.recordFailure(ProviderName.TWILIO);

            assertThat(circuitBreaker.getState(ProviderName.TWILIO)).isEqualTo(State.OPEN);
            clock.advance(COOLDOWN.minusMillis(1));
            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isTrue();
            clock.advance(Duration.ofMillis(1));
            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isFalse();
            assertThat(circuitBreaker.getState(ProviderName.TWILIO)).isEqualTo(State.HALF_OPEN);
        }

        @Test
        @DisplayName("If the probe never reports back, a new probe is granted after another cooldown")
        void lostProbe_isReplacedAfterCooldown() {
            trip(ProviderName.TWILIO);
            clock.advance(COOLDOWN);
            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isFalse();

            clock.advance(COOLDOWN);

            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isFalse();
            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isTrue();
        }

        @Test
        @DisplayName("getState is a pure read: it does not move the circuit to HALF_OPEN or use up the probe")
        void getState_doesNotGrantProbe() {
            trip(ProviderName.TWILIO);
            clock.advance(COOLDOWN);

            assertThat(circuitBreaker.getState(ProviderName.TWILIO)).isEqualTo(State.OPEN);
            assertThat(circuitBreaker.getState(ProviderName.TWILIO)).isEqualTo(State.OPEN);

            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isFalse();
            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isTrue();
        }
    }

    @Nested
    @DisplayName("Provider isolation")
    class ProviderIsolationTests {

        @Test
        @DisplayName("Opening one provider's circuit does not affect any other provider")
        void trippingOneProvider_doesNotAffectOthers() {
            trip(ProviderName.TWILIO);

            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isTrue();
            for (ProviderName other : ProviderName.values()) {
                if (other != ProviderName.TWILIO) {
                    assertThat(circuitBreaker.isOpen(other)).as(other.name()).isFalse();
                }
            }
        }

        @Test
        @DisplayName("Each provider keeps its own failure count")
        void independentFailureCounts() {
            recordFailures(ProviderName.TWILIO, 2);
            recordFailures(ProviderName.SENDGRID, 1);

            assertThat(circuitBreaker.getFailureCount(ProviderName.TWILIO)).isEqualTo(2);
            assertThat(circuitBreaker.getFailureCount(ProviderName.SENDGRID)).isEqualTo(1);
            assertThat(circuitBreaker.getFailureCount(ProviderName.TERMII)).isZero();
        }
    }

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("A null provider is rejected by every public method")
        void nullProvider_isRejected() {
            assertThatThrownBy(() -> circuitBreaker.isOpen(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Provider must not be null");
            assertThatThrownBy(() -> circuitBreaker.recordSuccess(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Provider must not be null");
            assertThatThrownBy(() -> circuitBreaker.recordFailure(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Provider must not be null");
        }

        @Test
        @DisplayName("A null clock is rejected")
        void nullClock_isRejected() {
            assertThatThrownBy(() -> new DefaultProviderCircuitBreaker(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Clock must not be null");
        }

        @Test
        @DisplayName("A non-positive failure threshold or cooldown is rejected")
        void invalidConfiguration_isRejected() {
            assertThatThrownBy(() -> new DefaultProviderCircuitBreaker(clock, 0, COOLDOWN))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Failure threshold must be positive");
            assertThatThrownBy(() -> new DefaultProviderCircuitBreaker(clock, 5, Duration.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cooldown duration must be positive");
            assertThatThrownBy(() -> new DefaultProviderCircuitBreaker(clock, 5, Duration.ofSeconds(-10)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cooldown duration must be positive");
            assertThatThrownBy(() -> new DefaultProviderCircuitBreaker(clock, 5, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cooldown duration must be positive");
        }
    }

    @Nested
    @DisplayName("Manual reset")
    class ResetTests {

        @Test
        @DisplayName("reset() closes one provider's circuit and clears its failure count")
        void resetSpecificProvider() {
            trip(ProviderName.TWILIO);
            trip(ProviderName.SENDGRID);

            circuitBreaker.reset(ProviderName.TWILIO);

            assertThat(circuitBreaker.getState(ProviderName.TWILIO)).isEqualTo(State.CLOSED);
            assertThat(circuitBreaker.getFailureCount(ProviderName.TWILIO)).isZero();
            assertThat(circuitBreaker.isOpen(ProviderName.SENDGRID)).isTrue();
        }

        @Test
        @DisplayName("resetAll() closes every provider's circuit")
        void resetAllProviders() {
            trip(ProviderName.TWILIO);
            trip(ProviderName.SENDGRID);

            circuitBreaker.resetAll();

            assertThat(circuitBreaker.isOpen(ProviderName.TWILIO)).isFalse();
            assertThat(circuitBreaker.isOpen(ProviderName.SENDGRID)).isFalse();
        }
    }

    @Nested
    @DisplayName("Concurrency")
    class ConcurrencyTests {

        private static final int THREADS = 20;
        private static final int CALLS_PER_THREAD = 50;

        @Test
        @DisplayName("Concurrent failures below the threshold are all counted: no update is lost and the circuit stays CLOSED")
        void concurrentFailuresBelowThreshold_areCountedExactly() throws Exception {
            int total = THREADS * CALLS_PER_THREAD;
            var breaker = new DefaultProviderCircuitBreaker(clock, total + 1, COOLDOWN);

            runConcurrently(THREADS, () -> {
                for (int i = 0; i < CALLS_PER_THREAD; i++) {
                    breaker.recordFailure(ProviderName.TWILIO);
                }
                return null;
            });

            assertThat(breaker.getFailureCount(ProviderName.TWILIO)).isEqualTo(total);
            assertThat(breaker.getState(ProviderName.TWILIO)).isEqualTo(State.CLOSED);
        }

        @Test
        @DisplayName("Concurrent failures that reach the threshold exactly open the circuit with the exact count")
        void concurrentFailuresReachingThreshold_openWithExactCount() throws Exception {
            int total = THREADS * CALLS_PER_THREAD;
            var breaker = new DefaultProviderCircuitBreaker(clock, total, COOLDOWN);

            runConcurrently(THREADS, () -> {
                for (int i = 0; i < CALLS_PER_THREAD; i++) {
                    breaker.recordFailure(ProviderName.TWILIO);
                }
                return null;
            });

            assertThat(breaker.getFailureCount(ProviderName.TWILIO)).isEqualTo(total);
            assertThat(breaker.getState(ProviderName.TWILIO)).isEqualTo(State.OPEN);
        }

        @Test
        @DisplayName("Concurrent failures on different providers are counted per provider without interference")
        void concurrentFailuresOnDifferentProviders_areIsolated() throws Exception {
            var breaker = new DefaultProviderCircuitBreaker(clock, Integer.MAX_VALUE, COOLDOWN);
            ProviderName[] providers = {ProviderName.TWILIO, ProviderName.SENDGRID};

            AtomicInteger next = new AtomicInteger();
            runConcurrently(THREADS, () -> {
                ProviderName provider = providers[next.getAndIncrement() % providers.length];
                for (int i = 0; i < CALLS_PER_THREAD; i++) {
                    breaker.recordFailure(provider);
                }
                return null;
            });

            int perProvider = (THREADS / providers.length) * CALLS_PER_THREAD;
            assertThat(breaker.getFailureCount(ProviderName.TWILIO)).isEqualTo(perProvider);
            assertThat(breaker.getFailureCount(ProviderName.SENDGRID)).isEqualTo(perProvider);
            assertThat(breaker.getFailureCount(ProviderName.TERMII)).isZero();
        }

        @Test
        @DisplayName("When many callers race after the cooldown, exactly one is granted the probe")
        void concurrentCallersAfterCooldown_exactlyOneProbe() throws Exception {
            trip(ProviderName.TWILIO);
            clock.advance(COOLDOWN);

            List<Boolean> results = runConcurrently(THREADS, () -> circuitBreaker.isOpen(ProviderName.TWILIO));

            assertThat(results).filteredOn(open -> !open).hasSize(1);
            assertThat(results).filteredOn(open -> open).hasSize(THREADS - 1);
            assertThat(circuitBreaker.getState(ProviderName.TWILIO)).isEqualTo(State.HALF_OPEN);
        }

        /**
         * Starts {@code threads} tasks at the same moment and returns each task's result.
         */
        private <T> List<T> runConcurrently(int threads, Callable<T> task) throws Exception {
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            try {
                CountDownLatch ready = new CountDownLatch(threads);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<T>> futures = new ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return task.call();
                    }));
                }
                assertThat(ready.await(10, TimeUnit.SECONDS)).as("workers ready").isTrue();
                start.countDown();
                List<T> results = new ArrayList<>();
                for (Future<T> future : futures) {
                    results.add(future.get(10, TimeUnit.SECONDS));
                }
                return results;
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private void trip(ProviderName provider) {
        recordFailures(provider, FAILURE_THRESHOLD);
    }

    private void recordFailures(ProviderName provider, int count) {
        for (int i = 0; i < count; i++) {
            circuitBreaker.recordFailure(provider);
        }
    }

    /**
     * A clock whose time only moves when a test advances it. The instant is volatile so
     * worker threads in the concurrency tests see the value the test thread set.
     */
    static final class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException("Not needed by these tests");
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
