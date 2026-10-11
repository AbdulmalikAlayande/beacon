package io.github.abdulmalikalayande.beacon.core.dedup;


import io.github.abdulmalikalayande.beacon.api.exception.DuplicateNotificationException;
import io.github.abdulmalikalayande.beacon.core.config.BeaconCoreTestConfig;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DataRedisTest
@Testcontainers
@Import(BeaconCoreTestConfig.class)
@DisplayName("RedisDeduplicationStore Integration Tests")
class RedisDeduplicationStoreTest {
	
	@Container
	@ServiceConnection
	static GenericContainer<?> redisContainer = new GenericContainer<>("redis:7.0.11-alpine")
			                                                .withExposedPorts(6379);
//	@DynamicPropertySource
//	static void redisProperties(DynamicPropertyRegistry registry) {
//		registry.add("spring.data.redis.host", redisContainer::getHost);
//		registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
//	}
	
	@Autowired
	Clock clock;
	RedisDeduplicationStore store;
	@Autowired
	StringRedisTemplate redisTemplate;
	static final String BEACON_DEDUP_NAMESPACE = "beacon:dedup:%s";
	
	
	@BeforeEach
	void setup() {
		redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
		store = new RedisDeduplicationStore(redisTemplate, clock);
	}
	
	@Nested
	@DisplayName("")
	class MarkSeenBehaviorTests {
		@Test
		@DisplayName("markSeen inserts a never-before-seen key, it is claimed successfully and does not throw")
		void insertNewKey_MarkSeen_DoesNotThrow () {
			String idempotencyKey = "a-new-key";
			assertDoesNotThrow(() -> store.markSeen(idempotencyKey));
			assertTrue(store.isSeen(idempotencyKey));
		}
		
		@Test
		@DisplayName("key exists in redis after markSeen is called, proving it was really written to redis")
		void keyExistsInRedisAfterMarkSeen () {
			String idempotencyKey = "a-new-key";
			store.markSeen(idempotencyKey);
			assertTrue(redisTemplate.hasKey(String.format(BEACON_DEDUP_NAMESPACE, idempotencyKey)));
			assertNotNull(redisTemplate.opsForValue().get(String.format(BEACON_DEDUP_NAMESPACE, idempotencyKey)));
		}
			
		@Test
		@DisplayName("key has a positive TTL after markSeen is called, proving it will expire")
		void keyStoreWithTTLSet_NotASPermanentKey () {
			String idempotencyKey = "a-new-key";
			store.markSeen(idempotencyKey);
			Long ttl = redisTemplate.getExpire(String.format(BEACON_DEDUP_NAMESPACE, idempotencyKey));
			assertNotNull(ttl);
			assertTrue(ttl > 0, "TTL should be positive, proving the key will expire and not leak forever");
		}
			
		@Test
		@DisplayName("Claiming the same key twice throws DuplicateNotificationException on the second call")
		void sameKeyClaimedMoreThanOnce_DuplicateNotificationExceptionIsThrown () {
			String idempotencyKey = "a-new-key";
			store.markSeen(idempotencyKey);
			assertThrows(DuplicateNotificationException.class, () -> store.markSeen(idempotencyKey));
		}
			
		@Test
		@DisplayName("The reject of an existing key does not reset or extend the TTL of the key")
		void sameKeyClaimedMoreThanOnce_RejectOccurs_TTLOfExistingKeyNotResetOrExtended () {
			String idempotencyKey = "a-new-key";
			store.markSeen(idempotencyKey);
			Long ttlBefore = redisTemplate.getExpire(idempotencyKey);
			assertThrows(DuplicateNotificationException.class, () -> store.markSeen(idempotencyKey));
			Long ttlAfter = redisTemplate.getExpire(idempotencyKey);
			assertTrue(ttlAfter <= ttlBefore, "TTL should not be reset or extended after a duplicate key is rejected");
		}
			
		@Test
		@DisplayName("The thrown exception carries the offending key in its message")
		void __ () {
			String idempotencyKey = "a-new-key";
			store.markSeen(idempotencyKey);
			DuplicateNotificationException exception = assertThrows(DuplicateNotificationException.class, () -> store.markSeen(idempotencyKey));
			assertTrue(exception.getMessage().contains(idempotencyKey));
			assertEquals("Duplicate notification rejected for idempotencyKey: " + idempotencyKey, exception.getMessage());
			
		}
			
		@Test
		@DisplayName("markSeen rejects a null idempotency key with IllegalArgumentException and never touches redis")
		void markSeen_withNullKey_throwsIllegalArgumentException () {
			int countBefore = getRedisKeyCount();
			assertThrows(IllegalArgumentException.class, () -> store.markSeen(null));
			int countAfter = getRedisKeyCount();
			assertAll(
					() -> assertEquals(countBefore, countAfter, "No key should be inserted for a blank key"),
					() -> assertEquals(0, countAfter, "Redis should remain empty after a blank key is rejected")
			);
			assertEquals(countBefore, countAfter, "No Redis key should be created for a null key");
		}
			
		@Test
		@DisplayName("markSeen rejects a blank idempotency key with IllegalArgumentException and never hits the database")
		void markSeen_withBlankKey_throwsIllegalArgumentException () {
			int countBefore = getRedisKeyCount();
			assertThrows(IllegalArgumentException.class, () -> store.markSeen("   "));
			int countAfter = getRedisKeyCount();
			assertAll(
					() -> assertEquals(countBefore, countAfter, "No key should be inserted for a blank key"),
					() -> assertEquals(0, countAfter, "Redis should remain empty after a blank key is rejected")
			);
			assertEquals(countBefore, countAfter, "No Redis key should be created for a blank key");
		}
			
		private int getRedisKeyCount () {
			Set<String> keys = redisTemplate.keys("*");
			return keys != null ? keys.size() : 0;
		}
		
		@Test
		@DisplayName("After the retention window passes, the key is gone and can be claimed again without duplicate error")
		void keyExpiredAfterTTL_CanBeClaimedAgain() {
			
			RedisDeduplicationStore dedupStore = new RedisDeduplicationStore(redisTemplate, clock, 3);
			
			String idempotencyKey = "expiring-key";

			dedupStore.markSeen(idempotencyKey);
			assertTrue(dedupStore.isSeen(idempotencyKey), "Key should be seen after first markSeen");
			assertTrue(redisTemplate.hasKey(String.format(BEACON_DEDUP_NAMESPACE, idempotencyKey)), "Key should exist in Redis");
			
			// Wait for TTL to expire,
			Awaitility.await()
					.atMost(7, TimeUnit.SECONDS)
					.pollDelay(100, TimeUnit.MILLISECONDS)
					.pollInterval(100, TimeUnit.MILLISECONDS)
					.until(() -> !redisTemplate.hasKey(String.format(BEACON_DEDUP_NAMESPACE, idempotencyKey)));
			
			// After expiry, the same key should be claimable again without duplicate error
			assertFalse(dedupStore.isSeen(idempotencyKey), "Key should not be seen after TTL expiry");
			assertDoesNotThrow(() -> dedupStore.markSeen(idempotencyKey), "markSeen should succeed again after TTL expires");
		}
		
		@Test
		@DisplayName("Claiming one key does not mark a different key as seen")
		void claimingOneKey_DoesNotAffectOtherKey() {
			String keyA = "independent-key-a";
			String keyB = "independent-key-b";
			
			store.markSeen(keyA);
			
			assertTrue(store.isSeen(keyA), "keyA should be seen");
			assertFalse(store.isSeen(keyB), "keyB should not be seen after claiming only keyA");
		}
		
		@Test
		@DisplayName("Two distinct keys are both claimed successfully without interference")
		void twoDistinctKeys_ClaimedSuccessfullyWithoutInterference() {
			String keyA = "distinct-key-a";
			String keyB = "distinct-key-b";
			
			assertDoesNotThrow(() -> store.markSeen(keyA), "First key should be claimed successfully");
			assertDoesNotThrow(() -> store.markSeen(keyB), "Second key should be claimed successfully");
			
			assertTrue(store.isSeen(keyA), "keyA should be seen");
			assertTrue(store.isSeen(keyB), "keyB should be seen");
			
			// Verify keys are independent — trying to claim keyA again should fail
			assertThrows(DuplicateNotificationException.class, () -> store.markSeen(keyA), "keyA should be rejected as duplicate");
			// But keyB should still be independent
			assertTrue(store.isSeen(keyB), "keyB should still be seen");
		}
	}
	
	@Nested
	@DisplayName("isSeen — pure read")
	class IsSeenBehaviourTest {
		
		@Test
		@DisplayName("isSeen returns false for a key never claimed")
		void keyNeverClaimed_IsSeenReturnsFalse() {
			String idempotencyKey = "never-seen-key";
			assertFalse(store.isSeen(idempotencyKey), "isSeen should return false for a key that was never claimed");
		}
		
		@Test
		@DisplayName("isSeen returns true for a key already claimed via markSeen")
		void keyClaimedViaMark_IsSeenReturnsTrue() {
			String idempotencyKey = "already-seen-key";
			store.markSeen(idempotencyKey);
			assertTrue(store.isSeen(idempotencyKey), "isSeen should return true for a key already claimed via markSeen");
		}
		
		@Test
		@DisplayName("isSeen does not itself claim the key — read has no side effect")
		void isSeenHasNoSideEffect_ReadDoesNotClaim() {
			String idempotencyKey = "test-key";
			// Call isSeen on a fresh key
			assertFalse(store.isSeen(idempotencyKey), "isSeen should return false before markSeen");
			// Then markSeen the same key must still succeed, proving the read has no side effect
			assertDoesNotThrow(() -> store.markSeen(idempotencyKey), "markSeen should succeed after isSeen, proving isSeen has no side effect");
			assertTrue(store.isSeen(idempotencyKey), "Key should be seen after markSeen succeeds");
		}
		
		@Test
		@DisplayName("Null key to isSeen throws IllegalArgumentException")
		void isSeenWithNullKey_ThrowsIllegalArgumentException() {
			assertThrows(IllegalArgumentException.class, () -> store.isSeen(null), "isSeen should throw IllegalArgumentException for null key");
		}
		
		@Test
		@DisplayName("Blank key to isSeen throws IllegalArgumentException")
		void isSeenWithBlankKey_ThrowsIllegalArgumentException() {
			assertThrows(IllegalArgumentException.class, () -> store.isSeen("   "), "isSeen should throw IllegalArgumentException for blank key");
		}
		
		@Test
		@DisplayName("Claiming one key does not mark a different key as seen")
		void claimingOneKeyDoesNotAffectOtherKey() {
			String keyA = "key-a";
			String keyB = "key-b";
			store.markSeen(keyA);
			assertFalse(store.isSeen(keyB), "keyB should not be seen after claiming keyA");
		}
		
		@Test
		@DisplayName("Two distinct keys are both claimed successfully without interference")
		void twoDistinctKeysClaimedWithoutInterference() {
			String keyA = "key-a";
			String keyB = "key-b";
			assertDoesNotThrow(() -> store.markSeen(keyA));
			assertDoesNotThrow(() -> store.markSeen(keyB));
			assertTrue(store.isSeen(keyA), "keyA should be seen");
			assertTrue(store.isSeen(keyB), "keyB should be seen");
		}
	}
	
	@Nested
	@DisplayName("Concurrency — atomic operations under thread contention")
	class ConcurrencyTests {
		
		@Test
		@DisplayName("When many threads race to claim the same key simultaneously, exactly one wins and all others get DuplicateNotificationException")
		void concurrentMarkSeen_SameKey_ExactlyOneWinsRestGetDuplicate() throws InterruptedException {
			int threadCount = 20;
			String contentedKey = "highly-contended-key";
			
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
								store.markSeen(contentedKey);
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
				
				assertEquals(1, successCount.get(),
						"Expected exactly one thread to successfully mark the key");
				assertEquals(threadCount - 1, duplicateCount.get(),
						"Expected all other threads to get DuplicateNotificationException");
				assertEquals(0, otherFailureCount.get(),
						"Expected no unexpected failures");
			}
		}
		
		@Test
		@DisplayName("When many threads each claim their own distinct key concurrently, every claim succeeds with no false duplicates")
		void concurrentMarkSeen_DistinctKeys_AllSucceedWithNoFalseRejections() throws InterruptedException {
			int threadCount = 20;
			
			try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
				CountDownLatch readyLatch = new CountDownLatch(threadCount);
				CountDownLatch startLatch = new CountDownLatch(1);
				CountDownLatch doneLatch = new CountDownLatch(threadCount);
				
				AtomicInteger successCount = new AtomicInteger(0);
				AtomicInteger duplicateCount = new AtomicInteger(0);
				AtomicInteger otherFailureCount = new AtomicInteger(0);
				
				for (int i = 0; i < threadCount; i++) {
					String distinctKey = UUID.randomUUID().toString();
					executor.submit(() -> {
						try {
							readyLatch.countDown();
							startLatch.await();
							try {
								store.markSeen(distinctKey);
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
				
				assertEquals(threadCount, successCount.get(),
						"Expected every thread with its own key to succeed");
				assertEquals(0, duplicateCount.get(),
						"Expected zero duplicates since every key is distinct");
				assertEquals(0, otherFailureCount.get(),
						"Expected no unexpected failures");
			}
		}
	}
}