package io.github.abdulmalikalayande.beacon.core.dedup;

import io.github.abdulmalikalayande.beacon.api.exception.DuplicateNotificationException;
import org.junit.jupiter.api.*;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultDeduplicationStore")
class DefaultDeduplicationStoreTest {

	private Clock clock;
	private DefaultDeduplicationStore store;

	@BeforeEach
	void setUp() {
		clock = Clock.fixed(Instant.parse("2026-07-25T15:00:00Z"), Clock.systemUTC().getZone());
		store = new DefaultDeduplicationStore(clock);
	}

	@Nested
	@DisplayName("markSeen behavior")
	class MarkSeenBehaviorTests {

		@Test
		@DisplayName("markSeen accepts a new idempotency key without throwing")
		void markSeen_acceptsNewIdempotencyKey_withoutThrowing() {
			String newKey = "new-key-001";
			
			assertDoesNotThrow(() -> store.markSeen(newKey),
				"Expected markSeen to accept a new idempotency key without throwing");
		}

		@Test
		@DisplayName("markSeen rejects a duplicate idempotency key by throwing DuplicateNotificationException")
		void markSeen_rejectsDuplicateIdempotencyKey_withDuplicateNotificationException() {
			String duplicateKey = "duplicate-key-001";
			
			// First call succeeds
			assertDoesNotThrow(() -> store.markSeen(duplicateKey));
			
			// Second call with same key should throw DuplicateNotificationException
			assertThrowsExactly(DuplicateNotificationException.class,
				() -> store.markSeen(duplicateKey),
				"Expected DuplicateNotificationException when marking a duplicate key");
		}
	}

	@Nested
	@DisplayName("seen-state tracking")
	class SeenStateTests {

		@Test
		@DisplayName("isSeen returns false before an idempotency key has been marked")
		void isSeen_returnsFalseBeforeKeyIsMarked() {
			String keyNotYetMarked = "key-not-yet-marked-001";
			
			assertFalse(store.isSeen(keyNotYetMarked),
				"Expected isSeen to return false for a key that has not been marked");
		}

		@Test
		@DisplayName("isSeen returns true after an idempotency key has been marked")
		void isSeen_returnsTrueAfterKeyIsMarked() {
			String keyToMark = "key-to-mark-001";
			
			store.markSeen(keyToMark);
			
			assertTrue(store.isSeen(keyToMark),
				"Expected isSeen to return true after markSeen has been called with the key");
		}
	}

	@Nested
	@DisplayName("duplicate detection")
	class DuplicateDetectionTests {

		@Test
		@DisplayName("isDuplicate returns false before an idempotency key has been marked")
		void isDuplicate_returnsFalseBeforeKeyIsMarked() {
			String keyNotYetMarked = "key-not-yet-marked-002";
			
			assertFalse(store.isDuplicate(keyNotYetMarked),
				"Expected isDuplicate to return false for a key that has not been marked");
		}

		@Test
		@DisplayName("isDuplicate returns true after an idempotency key has been marked")
		void isDuplicate_returnsTrueAfterKeyIsMarked() {
			String keyToMark = "key-to-mark-002";
			
			store.markSeen(keyToMark);
			
			assertTrue(store.isDuplicate(keyToMark),
				"Expected isDuplicate to return true after markSeen has been called with the key");
		}
	}

	@Nested
	@DisplayName("input validation")
	class InputValidationTests {

		@Test
		@DisplayName("markSeen rejects a null idempotency key with a clear validation error")
		void markSeen_rejectsNullIdempotencyKey_withClearValidationError() {
			assertThrowsExactly(IllegalArgumentException.class,
				() -> store.markSeen(null),
				"Expected markSeen to reject null key with IllegalArgumentException");
		}

		@Test
		@DisplayName("markSeen rejects a blank idempotency key with a clear validation error")
		void markSeen_rejectsBlankIdempotencyKey_withClearValidationError() {
			assertThrowsExactly(IllegalArgumentException.class,
				() -> store.markSeen("   "),
				"Expected markSeen to reject blank key with IllegalArgumentException");
		}

		@Test
		@DisplayName("isSeen rejects a null idempotency key with a clear validation error")
		void isSeen_rejectsNullIdempotencyKey_withClearValidationError() {
			assertThrowsExactly(IllegalArgumentException.class,
				() -> store.isSeen(null),
				"Expected isSeen to reject null key with IllegalArgumentException");
		}

		@Test
		@DisplayName("isDuplicate rejects a blank idempotency key with a clear validation error")
		void isDuplicate_rejectsBlankIdempotencyKey_withClearValidationError() {
			assertThrowsExactly(IllegalArgumentException.class,
				() -> store.isDuplicate(""),
				"Expected isDuplicate to reject blank key with IllegalArgumentException");
		}
	}

	@Nested
	@DisplayName("concurrent access")
	class ConcurrentAccessTests {

		@Test
		@DisplayName("concurrent markSeen calls for the same idempotency key allow exactly one winner")
		void concurrentMarkSeenCalls_sameIdempotencyKey_allowExactlyOneWinner() throws Exception {
			String sharedKey = "concurrent-key-001";
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
				
				readyLatch.await();
				startLatch.countDown();
				doneLatch.await();
				executor.shutdown();
				
				assertEquals(1, successCount.get(),
					"Expected exactly one thread to successfully mark the key");
				assertEquals(threadCount - 1, duplicateCount.get(),
					"Expected all other threads to get DuplicateNotificationException");
				assertEquals(0, otherFailureCount.get(),
					"Expected no unexpected failures");
			}
		}
	}

	@Nested
	@DisplayName("independent key handling")
	class IndependentKeyHandlingTests {

		@Test
		@DisplayName("different idempotency keys do not interfere with each other")
		void differentIdempotencyKeys_doNotInterfereWithEachOther() {
			String keyA = "independent-key-a";
			String keyB = "independent-key-b";
			
			// Mark key A
			store.markSeen(keyA);
			
			// Key B should not be seen
			assertFalse(store.isSeen(keyB),
				"Expected key B to not be seen when only key A has been marked");
			
			// Marking key B should succeed (no interference)
			assertDoesNotThrow(() -> store.markSeen(keyB),
				"Expected marking key B to succeed, independent from key A");
			
			// Both keys should now be seen
			assertTrue(store.isSeen(keyA));
			assertTrue(store.isSeen(keyB));
			
			// Attempting to mark key A again should fail with duplicate
			assertThrowsExactly(DuplicateNotificationException.class,
				() -> store.markSeen(keyA),
				"Expected marking key A again to fail with DuplicateNotificationException");
			
			// But key B marking should still fail independently
			assertThrowsExactly(DuplicateNotificationException.class,
				() -> store.markSeen(keyB),
				"Expected marking key B again to fail with DuplicateNotificationException");
		}
	}

}