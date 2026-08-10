package io.github.abdulmalikalayande.beacon.core.dedup;

import io.github.abdulmalikalayande.beacon.api.exception.DuplicateNotificationException;
import io.github.abdulmalikalayande.beacon.api.port.DeduplicationStore;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@DisplayName("RedisDeduplicationStore — shared, SETNX-based idempotency with built-in TTL")
class RedisDeduplicationStoreTest {

	@Container
	private static final GenericContainer<?> REDIS_CONTAINER =
			new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

	private static StringRedisTemplate redisTemplate;

	private Clock clock;
	private DeduplicationStore store;

	@BeforeAll
	static void setUpRedisTemplate() {
		RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
				REDIS_CONTAINER.getHost(), REDIS_CONTAINER.getMappedPort(6379));
		LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(configuration);
		connectionFactory.afterPropertiesSet();
		redisTemplate = new StringRedisTemplate(connectionFactory);
		redisTemplate.afterPropertiesSet();
	}

	@BeforeEach
	void setUp() {
		clock = Clock.fixed(Instant.parse("2026-07-25T15:00:00Z"), Clock.systemUTC().getZone());
		store = new RedisDeduplicationStore(redisTemplate, clock);
	}

	@Nested
	@DisplayName("markSeen — claiming a key for the first time via SETNX")
	class MarkSeenFirstClaimTests {

		@Test
		@DisplayName("markSeen accepts a never-before-seen key without throwing")
		void markSeen_withNewKey_doesNotThrow() {
			String newKey = UUID.randomUUID().toString();
			assertDoesNotThrow(() -> store.markSeen(newKey));
		}

		@Test
		@DisplayName("markSeen writes the key into Redis, verifiable with a direct GET")
		void markSeen_withNewKey_isVisibleViaDirectRedisGet() {
			String newKey = UUID.randomUUID().toString();
			store.markSeen(newKey);

			String storedValue = redisTemplate.opsForValue().get("beacon:dedup:" + newKey);
			assertNotNull(storedValue, "Expected the key to exist in Redis after markSeen");
		}

		@Test
		@DisplayName("markSeen sets a positive TTL on the key so it is never kept forever")
		void markSeen_withNewKey_setsAPositiveTtl() {
			String newKey = UUID.randomUUID().toString();
			store.markSeen(newKey);

			Long ttlSeconds = redisTemplate.getExpire("beacon:dedup:" + newKey, TimeUnit.SECONDS);
			assertNotNull(ttlSeconds);
			assertTrue(ttlSeconds > 0, "Expected a positive TTL on the claimed key");
		}
	}

	@Nested
	@DisplayName("markSeen — rejecting a duplicate claim")
	class MarkSeenDuplicateRejectionTests {

		@Test
		@DisplayName("markSeen throws DuplicateNotificationException when the same key is claimed a second time")
		void markSeen_withAlreadyClaimedKey_throwsDuplicateNotificationException() {
			String newKey = UUID.randomUUID().toString();
			store.markSeen(newKey);
			assertThrows(DuplicateNotificationException.class, () -> store.markSeen(newKey));
		}

		@Test
		@DisplayName("markSeen surfaces the offending idempotency key on the thrown DuplicateNotificationException")
		void markSeen_withAlreadyClaimedKey_exceptionCarriesTheDuplicatedKey() {
			String newKey = UUID.randomUUID().toString();

			store.markSeen(newKey);
			DuplicateNotificationException exception = assertThrows(DuplicateNotificationException.class, () -> store.markSeen(newKey));
			assertTrue(exception.getMessage().contains(newKey));
		}
	}

	@Nested
	@DisplayName("markSeen / isSeen — input validation (rejecting malformed keys before touching Redis)")
	class InputValidationTests {

		@Test
		@DisplayName("markSeen rejects a null idempotency key with IllegalArgumentException")
		void markSeen_withNullKey_throwsIllegalArgumentException() {
			assertThrows(IllegalArgumentException.class, () -> store.markSeen(null));
		}

		@Test
		@DisplayName("markSeen rejects a blank idempotency key with IllegalArgumentException")
		void markSeen_withBlankKey_throwsIllegalArgumentException() {
			assertThrows(IllegalArgumentException.class, () -> store.markSeen("   "));
		}

		@Test
		@DisplayName("isSeen rejects a null idempotency key with IllegalArgumentException")
		void isSeen_withNullKey_throwsIllegalArgumentException() {
			assertThrows(IllegalArgumentException.class, () -> store.isSeen(null));
		}

		@Test
		@DisplayName("isSeen rejects a blank idempotency key with IllegalArgumentException")
		void isSeen_withBlankKey_throwsIllegalArgumentException() {
			assertThrows(IllegalArgumentException.class, () -> store.isSeen("   "));
		}
	}

	@Nested
	@DisplayName("isSeen — non-destructive read of whether a key has already been claimed")
	class IsSeenReadTests {

		@Test
		@DisplayName("isSeen returns false for a key that has never been claimed")
		void isSeen_withUnclaimedKey_returnsFalse() {
			String unclaimedKey = UUID.randomUUID().toString();
			assertFalse(store.isSeen(unclaimedKey));
		}

		@Test
		@DisplayName("isSeen returns true for a key that has already been claimed via markSeen")
		void isSeen_withAlreadyClaimedKey_returnsTrue() {
			String claimedKey = UUID.randomUUID().toString();
			store.markSeen(claimedKey);
			assertTrue(store.isSeen(claimedKey));
		}

		@Test
		@DisplayName("isSeen is a pure read and does not itself claim the key, so a later markSeen still succeeds")
		void isSeen_doesNotClaimTheKey_soASubsequentMarkSeenStillSucceeds() {
			String newKey = UUID.randomUUID().toString();

			assertFalse(store.isSeen(newKey), "Expected isSeen to return false for an unclaimed key");
			assertDoesNotThrow(() -> store.markSeen(newKey),
					"Expected markSeen to succeed after isSeen — proves isSeen is a pure read with no side effects");
			assertTrue(store.isSeen(newKey));
		}
	}

	@Nested
	@DisplayName("independent keys — different triggers must never interfere with one another")
	class IndependentKeyTests {

		@Test
		@DisplayName("claiming one key does not mark a different, unrelated key as seen")
		void markSeen_withOneKey_doesNotAffectADifferentKey() {
			String claimedKey = UUID.randomUUID().toString();
			String unrelatedKey = UUID.randomUUID().toString();

			store.markSeen(claimedKey);

			assertTrue(store.isSeen(claimedKey));
			assertFalse(store.isSeen(unrelatedKey));
		}
	}

	@Nested
	@DisplayName("concurrent access — proving SETNX is atomic under real thread contention")
	class ConcurrentAccessTests {

		@Test
		@DisplayName("when many threads race to claim the same key simultaneously, exactly one wins and all others get DuplicateNotificationException")
		void concurrentMarkSeen_forTheSameKey_allowsExactlyOneWinnerAndTheRestAreRejected() throws InterruptedException {
			String sharedKey = UUID.randomUUID().toString();
			int threadCount = 20;

			try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
				CountDownLatch readyLatch = new CountDownLatch(threadCount);
				CountDownLatch startLatch = new CountDownLatch(1);
				CountDownLatch doneLatch = new CountDownLatch(threadCount);

				AtomicInteger successCount = new AtomicInteger(0);
				AtomicInteger duplicateCount = new AtomicInteger(0);
				AtomicInteger otherFailureCount = new AtomicInteger(0);

				for (int i = 0; i < threadCount; i++) {
					executor.submit(() -> {
						try {
							readyLatch.countDown();
							startLatch.await();

							try {
								store.markSeen(sharedKey);
								successCount.incrementAndGet();
							} catch (DuplicateNotificationException e) {
								duplicateCount.incrementAndGet();
							} catch (Exception e) {
								otherFailureCount.incrementAndGet();
							}
						} catch (Throwable e) {
							otherFailureCount.incrementAndGet();
						} finally {
							doneLatch.countDown();
						}
					});
				}

				assertTrue(readyLatch.await(10, TimeUnit.SECONDS), "Workers were not ready in time");
				startLatch.countDown();
				assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "Workers did not finish in time");
				executor.shutdown();

				assertEquals(1, successCount.get(), "Expected exactly one thread to successfully mark the key");
				assertEquals(threadCount - 1, duplicateCount.get(), "Expected all other threads to get DuplicateNotificationException");
				assertEquals(0, otherFailureCount.get(), "Expected no unexpected failures");
			}
		}
	}

	@Nested
	@DisplayName("TTL expiry — a claimed key must eventually free itself up")
	class TtlExpiryTests {

		@Test
		@DisplayName("once a short-TTL key expires, isSeen returns false again and markSeen succeeds again")
		void afterTtlExpires_keyIsForgotten_andCanBeClaimedAgain() throws InterruptedException {
			DeduplicationStore shortTtlStore = new RedisDeduplicationStore(
					redisTemplate, clock, Duration.ofSeconds(2), RedisDeduplicationStore.DEFAULT_KEY_PREFIX);
			String key = UUID.randomUUID().toString();

			shortTtlStore.markSeen(key);
			assertTrue(shortTtlStore.isSeen(key));

			TimeUnit.SECONDS.sleep(3);

			assertFalse(shortTtlStore.isSeen(key), "Expected the key to have expired from Redis");
			assertDoesNotThrow(() -> shortTtlStore.markSeen(key), "Expected markSeen to succeed again after TTL expiry");
		}
	}
}
