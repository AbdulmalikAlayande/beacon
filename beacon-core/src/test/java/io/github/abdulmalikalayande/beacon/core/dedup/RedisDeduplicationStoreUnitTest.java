package io.github.abdulmalikalayande.beacon.core.dedup;

import io.github.abdulmalikalayande.beacon.api.exception.DuplicateNotificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisDeduplicationStore Unit Tests")
class RedisDeduplicationStoreUnitTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	private Clock fixedClock;
	private RedisDeduplicationStore store;

	private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");

	@BeforeEach
	void setUp() {
		fixedClock = Clock.fixed(NOW, ZoneId.of("UTC"));
		lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		store = new RedisDeduplicationStore(redisTemplate, fixedClock);
	}

	@Nested
	@DisplayName("markSeen — atomic SETNX behavior")
	class MarkSeenTests {

		@Test
		@DisplayName("1. markSeen with new key — succeeds, returns normally")
		void markSeen_withNewKey_succeedsWithoutException() {
			String key = "new-order-123";
			String expectedRedisKey = "beacon:dedup:" + key;
			when(valueOperations.setIfAbsent(eq(expectedRedisKey), eq(NOW.toString()), eq(Duration.ofHours(24))))
					.thenReturn(Boolean.TRUE);

			assertDoesNotThrow(() -> store.markSeen(key));

			verify(valueOperations).setIfAbsent(expectedRedisKey, NOW.toString(), Duration.ofHours(24));
		}

		@Test
		@DisplayName("2. markSeen with duplicate key — throws DuplicateNotificationException")
		void markSeen_withDuplicateKey_throwsDuplicateNotificationException() {
			String key = "duplicate-order-456";
			String expectedRedisKey = "beacon:dedup:" + key;
			when(valueOperations.setIfAbsent(eq(expectedRedisKey), any(), any()))
					.thenReturn(Boolean.FALSE);

			DuplicateNotificationException ex = assertThrows(
					DuplicateNotificationException.class,
					() -> store.markSeen(key)
			);

			assertTrue(ex.getMessage().contains(key));
		}

		@Test
		@DisplayName("3. markSeen stores key with Clock timestamp as value")
		void markSeen_storesTimestampFromClock() {
			String key = "timestamp-check-789";
			String expectedRedisKey = "beacon:dedup:" + key;
			when(valueOperations.setIfAbsent(eq(expectedRedisKey), eq(NOW.toString()), any(Duration.class)))
					.thenReturn(Boolean.TRUE);

			store.markSeen(key);

			verify(valueOperations).setIfAbsent(expectedRedisKey, NOW.toString(), Duration.ofHours(24));
		}

		@Test
		@DisplayName("4. markSeen passes configured TTL to setIfAbsent")
		void markSeen_passesConfiguredTtl() {
			Duration customTtl = Duration.ofMinutes(15);
			RedisDeduplicationStore customStore = new RedisDeduplicationStore(redisTemplate, fixedClock, customTtl);

			String key = "custom-ttl-key";
			String expectedRedisKey = "beacon:dedup:" + key;
			when(valueOperations.setIfAbsent(eq(expectedRedisKey), eq(NOW.toString()), eq(customTtl)))
					.thenReturn(Boolean.TRUE);

			customStore.markSeen(key);

			verify(valueOperations).setIfAbsent(expectedRedisKey, NOW.toString(), customTtl);
		}
	}

	@Nested
	@DisplayName("isSeen — pure read behavior")
	class IsSeenTests {

		@Test
		@DisplayName("5. isSeen returns false for unclaimed key")
		void isSeen_returnsFalseForUnclaimedKey() {
			String key = "unseen-key";
			when(redisTemplate.hasKey("beacon:dedup:" + key)).thenReturn(Boolean.FALSE);

			assertFalse(store.isSeen(key));
			verify(redisTemplate).hasKey("beacon:dedup:" + key);
			verifyNoInteractions(valueOperations);
		}

		@Test
		@DisplayName("6. isSeen returns true for claimed key")
		void isSeen_returnsTrueForClaimedKey() {
			String key = "seen-key";
			when(redisTemplate.hasKey("beacon:dedup:" + key)).thenReturn(Boolean.TRUE);

			assertTrue(store.isSeen(key));
			verify(redisTemplate).hasKey("beacon:dedup:" + key);
			verifyNoInteractions(valueOperations);
		}

		@Test
		@DisplayName("7. isSeen is a pure read and does not mutate Redis")
		void isSeen_isPureRead_neverSetsKey() {
			String key = "pure-read-key";
			when(redisTemplate.hasKey("beacon:dedup:" + key)).thenReturn(Boolean.FALSE);

			store.isSeen(key);

			verify(redisTemplate, never()).opsForValue();
			verifyNoInteractions(valueOperations);
		}
	}

	@Nested
	@DisplayName("Input validation & edge cases")
	class InputValidationTests {

		@Test
		@DisplayName("8. null key throws IllegalArgumentException for both markSeen and isSeen")
		void nullKey_throwsIllegalArgumentException() {
			assertThrows(IllegalArgumentException.class, () -> store.markSeen(null));
			assertThrows(IllegalArgumentException.class, () -> store.isSeen(null));

			verifyNoInteractions(redisTemplate);
			verifyNoInteractions(valueOperations);
		}

		@Test
		@DisplayName("9. blank key throws IllegalArgumentException for both markSeen and isSeen")
		void blankKey_throwsIllegalArgumentException() {
			assertThrows(IllegalArgumentException.class, () -> store.markSeen("   "));
			assertThrows(IllegalArgumentException.class, () -> store.isSeen("   "));

			verifyNoInteractions(redisTemplate);
			verifyNoInteractions(valueOperations);
		}

		@Test
		@DisplayName("10. custom key prefix formatting")
		void customKeyPrefix_formatsCorrectly() {
			String customPrefix = "custom:notification:";
			RedisDeduplicationStore customStore = new RedisDeduplicationStore(
					redisTemplate, fixedClock, Duration.ofHours(1), customPrefix
			);

			String key = "test-123";
			when(valueOperations.setIfAbsent(eq(customPrefix + key), any(), any()))
					.thenReturn(Boolean.TRUE);
			when(redisTemplate.hasKey(customPrefix + key)).thenReturn(Boolean.TRUE);

			customStore.markSeen(key);
			assertTrue(customStore.isSeen(key));

			verify(valueOperations).setIfAbsent(eq(customPrefix + key), any(), any());
			verify(redisTemplate).hasKey(customPrefix + key);
		}

		@Test
		@DisplayName("11. constructor null checks enforce valid dependencies")
		void constructor_nullChecks() {
			assertThrows(NullPointerException.class, () -> new RedisDeduplicationStore(null, fixedClock));
			assertThrows(NullPointerException.class, () -> new RedisDeduplicationStore(redisTemplate, null));
			assertThrows(NullPointerException.class, () -> new RedisDeduplicationStore(redisTemplate, fixedClock, (Duration) null));
		}

		@Test
		@DisplayName("12. constructor rejects non-positive or sub-millisecond TTL")
		void constructor_rejectsNonPositiveTtl() {
			assertThrows(IllegalArgumentException.class, () -> new RedisDeduplicationStore(redisTemplate, fixedClock, Duration.ZERO));
			assertThrows(IllegalArgumentException.class, () -> new RedisDeduplicationStore(redisTemplate, fixedClock, Duration.ofSeconds(-1)));
			assertThrows(IllegalArgumentException.class, () -> new RedisDeduplicationStore(redisTemplate, fixedClock, Duration.ofNanos(500)));
			assertThrows(IllegalArgumentException.class, () -> new RedisDeduplicationStore(redisTemplate, fixedClock, 0));
			assertThrows(IllegalArgumentException.class, () -> new RedisDeduplicationStore(redisTemplate, fixedClock, -5));
		}

		@Test
		@DisplayName("13. legacy int seconds constructor delegates correctly")
		void legacyConstructor_delegatesCorrectly() {
			RedisDeduplicationStore legacyStore = new RedisDeduplicationStore(redisTemplate, fixedClock, 3600);
			String key = "legacy-key";
			when(valueOperations.setIfAbsent(eq("beacon:dedup:" + key), any(), eq(Duration.ofSeconds(3600))))
					.thenReturn(Boolean.TRUE);

			legacyStore.markSeen(key);

			verify(valueOperations).setIfAbsent("beacon:dedup:" + key, NOW.toString(), Duration.ofSeconds(3600));
		}
	}

	@Nested
	@DisplayName("Delivery lock stubs")
	class DeliveryLockStubsTests {

		@Test
		@DisplayName("Delivery lock methods are stubs returning false and no-op")
		void deliveryLocks_areStubs() {
			assertFalse(store.acquireDeliveryLock("notif-1"));
			assertDoesNotThrow(() -> store.releaseDeliveryLock("notif-1"));
		}
	}

	@Nested
	@DisplayName("Concurrency simulation")
	class ConcurrencyTests {

		@Test
		@DisplayName("Concurrent markSeen for same key — exactly 1 wins, rest get DuplicateNotificationException")
		void concurrentMarkSeen_exactlyOneWins() throws InterruptedException {
			int threads = 10;
			String key = "race-key";
			String expectedRedisKey = "beacon:dedup:" + key;

			// Atomic simulation: first thread gets true, all subsequent threads get false
			AtomicBoolean claimed = new AtomicBoolean(false);
			when(valueOperations.setIfAbsent(eq(expectedRedisKey), any(), any()))
					.thenAnswer(invocation -> claimed.compareAndSet(false, true));

			ExecutorService executor = Executors.newFixedThreadPool(threads);
			try {
				CountDownLatch startLatch = new CountDownLatch(1);
				CountDownLatch doneLatch = new CountDownLatch(threads);

				AtomicInteger successCount = new AtomicInteger();
				AtomicInteger duplicateCount = new AtomicInteger();

				for (int i = 0; i < threads; i++) {
					executor.submit(() -> {
						try {
							startLatch.await();
							store.markSeen(key);
							successCount.incrementAndGet();
						} catch (DuplicateNotificationException e) {
							duplicateCount.incrementAndGet();
						} catch (Exception ignored) {
						} finally {
							doneLatch.countDown();
						}
					});
				}

				startLatch.countDown();
				boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
				assertTrue(completed, "Workers did not finish in time");

				assertEquals(1, successCount.get(), "Exactly one caller must win the claim");
				assertEquals(threads - 1, duplicateCount.get(), "All others must receive DuplicateNotificationException");
			} finally {
				executor.shutdownNow();
			}
		}
	}
}
