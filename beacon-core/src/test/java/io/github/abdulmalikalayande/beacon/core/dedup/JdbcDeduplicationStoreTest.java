package io.github.abdulmalikalayande.beacon.core.dedup;

import io.github.abdulmalikalayande.beacon.api.exception.DuplicateNotificationException;
import io.github.abdulmalikalayande.beacon.api.port.DeduplicationStore;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@JdbcTest
@TestPropertySource(locations = "classpath:application-test.properties")
@DisplayName("JdbcDeduplicationStore — durable, database-backed idempotency via unique-constraint insert")
class JdbcDeduplicationStoreTest {
	
	@Autowired
	private JdbcTemplate jdbcTemplate;
	private DeduplicationStore store;
	@Autowired
	private DataSource dataSource;
//	
	@BeforeEach
	void setUp() {
		jdbcTemplate = new JdbcTemplate(dataSource);
		store = new JdbcDeduplicationStore(jdbcTemplate);
	}
	
	@Nested
	@DisplayName("markSeen — claiming a key for the first time (the happy path of the insert-is-the-check pattern)")
	class MarkSeenFirstClaimTests {
		
		@Test
		@DisplayName("markSeen inserts a never-before-seen key successfully and does not throw")
		void markSeen_withBrandNewKey_insertsSuccessfullyWithoutThrowing() {
			String newKey = UUID.randomUUID().toString();
			assertDoesNotThrow(()-> store.markSeen(newKey));
			assertTrue(store.isSeen(newKey));
		}
		
		@Test
		@DisplayName("markSeen persists the key as an actual row in the dedup table so the claim survives restarts")
		void markSeen_withBrandNewKey_writesARowThatIsVisibleInTheDatabase() {
			String newKey = UUID.randomUUID().toString();
			store.markSeen(newKey);
			
			Integer rowCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM deduplication_store WHERE idempotency_key = ?", Integer.class, newKey);
			Boolean queryResult = jdbcTemplate.queryForObject(
				"SELECT EXISTS(SELECT 1 FROM deduplication_store WHERE idempotency_key = ?)", Boolean.class, newKey);
			
			assertNotNull(rowCount);
			assertNotNull(queryResult);
			
			//asserting the row exists to prove durability, the whole reason JDBC dedup exists over in-memory
			assertEquals(1, rowCount);
			assertTrue(queryResult);
		}
		
		@Test
		@Deprecated(forRemoval = true)
		@DisplayName("markSeen records the claim timestamp using the injected Clock, not wall-clock time")
		void markSeen_withBrandNewKey_storesTheClaimTimestampFromTheInjectedClock() {
			// use a fixed Clock; mark a key; read seen_at column; assert it equals the fixed instant
			// proves the Clock discipline holds in the JDBC store too
		}
	}
	
	@Nested
	@DisplayName("markSeen — rejecting a duplicate claim (the unique constraint translating into a domain exception)")
	class MarkSeenDuplicateRejectionTests {
		
		@Test
		@DisplayName("markSeen throws DuplicateNotificationException when the same key is claimed a second time")
		void markSeen_withAlreadyClaimedKey_throwsDuplicateNotificationException() {
			String newKey = UUID.randomUUID().toString();
			store.markSeen(newKey);
			assertThrows(DuplicateNotificationException.class, () -> store.markSeen(newKey));
		}
		
		@Test
		// With the shared transaction gone, the row we insert actually saves in the container's database after the test
		// But since we use a UUID in each new test, it does not matter at all; we will never run into a duplicate key error
		// We just need to be aware that the auto-cleanup is off for any method we annotate this way
		@Transactional(propagation = Propagation.NOT_SUPPORTED)
		@DisplayName("markSeen does not insert a second row when a duplicate is rejected, leaving exactly one row for the key")
		void markSeen_withAlreadyClaimedKey_doesNotCreateASecondRow() {
			String newKey = UUID.randomUUID().toString();
			
			store.markSeen(newKey);
			// Although this call triggers an error as expected which is handled, postgres does something funny.
			// In Postgres, once any statement fails inside a transaction, Postgres aborts the entire transaction.
			// Every subsequent command in that same transaction is refused with exactly this message:
			// "current transaction is aborted, commands ignored until end of transaction block".
			// The transaction is poisoned. It won't accept anything else until we roll back.
			
			// "Postgres aborts a transaction on any error" and "@JdbcTest runs each test in one transaction."
			assertThrows(DuplicateNotificationException.class, () -> store.markSeen(newKey));
			
			Integer rowCount = jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM deduplication_store WHERE idempotency_key = ?", Integer.class, newKey);

			assertNotNull(rowCount);
			// to prove that the rejection is clean, i.e., there is no partial or double insert
			assertEquals(1, rowCount);
			doManualCleanup(newKey);
		}

		private void doManualCleanup(String key) {
			jdbcTemplate.update("DELETE FROM deduplication_store WHERE idempotency_key = ?", key);
			Integer rowCountAfterCleanup = jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM deduplication_store WHERE idempotency_key = ?", Integer.class, key);
			assertNotNull(rowCountAfterCleanup);
			assertEquals(0, rowCountAfterCleanup);
		}
		
		@Test
		@DisplayName("markSeen surfaces the offending idempotency key on the thrown DuplicateNotificationException")
		void markSeen_withAlreadyClaimedKey_exceptionCarriesTheDuplicatedKey() {
			String newKey = UUID.randomUUID().toString();
			
			store.markSeen(newKey);
			DuplicateNotificationException exception = assertThrows(DuplicateNotificationException.class, () -> store.markSeen(newKey));
			assertTrue(exception.getMessage().contains(newKey));
			assertEquals("Duplicate notification rejected for idempotencyKey: " + newKey, exception.getMessage());
		}
	}
	
	@Nested
	@DisplayName("markSeen — input validation (rejecting malformed keys before touching the database)")
	class MarkSeenInputValidationTests {
		
		@Test
		@DisplayName("markSeen rejects a null idempotency key with IllegalArgumentException and never hits the database")
		void markSeen_withNullKey_throwsIllegalArgumentException() {
			Integer countBefore = jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM deduplication_store", Integer.class);
			assertThrows(IllegalArgumentException.class, () -> store.markSeen(null));
			Integer countAfter = jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM deduplication_store", Integer.class);

			assertNotNull(countBefore);
			assertNotNull(countAfter);
			assertEquals(countBefore, countAfter, "No row should be inserted for a null key");
		}
		
		@Test
		@DisplayName("markSeen rejects a blank idempotency key with IllegalArgumentException and never hits the database")
		void markSeen_withBlankKey_throwsIllegalArgumentException() {
			Integer countBefore = jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM deduplication_store", Integer.class);
			assertThrows(IllegalArgumentException.class, () -> store.markSeen("   "));
			Integer countAfter = jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM deduplication_store", Integer.class);

			assertNotNull(countBefore);
			assertNotNull(countAfter);
			assertEquals(countBefore, countAfter, "No row should be inserted for a blank key");
		}
	}
	
	@Nested
	@DisplayName("isSeen — non-destructive read of whether a key has already been claimed")
	class IsSeenReadTests {
		
		@Test
		@DisplayName("isSeen returns false for a key that has never been claimed")
		void isSeen_withUnclaimedKey_returnsFalse() {
			String newKey = UUID.randomUUID().toString();
			String anotherKey = UUID.randomUUID().toString();
			String unclaimedKey = UUID.randomUUID().toString();
			
			store.markSeen(newKey);
			store.markSeen(anotherKey);
			
			assertTrue(store.isSeen(newKey));
			assertTrue(store.isSeen(anotherKey));
			
			assertFalse(store.isSeen(unclaimedKey));
		}
		
		@Test
		@DisplayName("isSeen returns true for a key that has already been claimed via markSeen")
		void isSeen_withAlreadyClaimedKey_returnsTrue() {
			String newKey = UUID.randomUUID().toString();
			String anotherKey = UUID.randomUUID().toString();
			String claimedKey = UUID.randomUUID().toString();
			
			store.markSeen(newKey);
			store.markSeen(anotherKey);
			store.markSeen(claimedKey);
			
			assertTrue(store.isSeen(newKey));
			assertTrue(store.isSeen(anotherKey));
			
			assertTrue(store.isSeen(claimedKey));
		}
		
		@Test
		@DisplayName("isSeen is a pure read and does not itself claim the key, so a later markSeen still succeeds")
		void isSeen_doesNotClaimTheKey_soASubsequentMarkSeenStillSucceeds() {
			String newKey = UUID.randomUUID().toString();

			// isSeen must be a pure read — it must not claim the key
			assertFalse(store.isSeen(newKey), "Expected isSeen to return false for an unclaimed key");

			// If isSeen had side effects (accidentally inserted the key), this would throw DuplicateNotificationException
			assertDoesNotThrow(() -> store.markSeen(newKey),
					"Expected markSeen to succeed after isSeen — proves isSeen is a pure read with no side effects");

			// Now the key should be seen
			assertTrue(store.isSeen(newKey));
		}
		
		@Test
		@DisplayName("isSeen rejects a null idempotency key with IllegalArgumentException")
		void isSeen_withNullKey_throwsIllegalArgumentException() {
			assertThrows(IllegalArgumentException.class, () -> store.isSeen(null),
					"Expected isSeen to reject null key with IllegalArgumentException");
		}

		@Test
		@DisplayName("isSeen rejects a blank idempotency key with IllegalArgumentException")
		void isSeen_withBlankKey_throwsIllegalArgumentException() {
			assertThrows(IllegalArgumentException.class, () -> store.isSeen("   "),
					"Expected isSeen to reject blank key with IllegalArgumentException");
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

			assertTrue(store.isSeen(claimedKey), "The claimed key should be seen");
			assertFalse(store.isSeen(unrelatedKey),
					"An unrelated key must not be affected by claiming a different key");
		}

		@Test
		@DisplayName("two different keys can both be claimed successfully without either being treated as a duplicate")
		void markSeen_withTwoDistinctKeys_bothSucceedIndependently() {
			String keyA = UUID.randomUUID().toString();
			String keyB = UUID.randomUUID().toString();

			assertDoesNotThrow(() -> store.markSeen(keyA), "First key should be claimed successfully");
			assertDoesNotThrow(() -> store.markSeen(keyB), "Second key should be claimed successfully — no interference");

			assertTrue(store.isSeen(keyA));
			assertTrue(store.isSeen(keyB));
		}
	}
	
	@Nested
	@DisplayName("concurrent access — proving the unique-constraint insert is atomic under real thread contention")
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

				assertEquals(1, successCount.get(),
						"Expected exactly one thread to successfully mark the key");
				assertEquals(threadCount - 1, duplicateCount.get(),
						"Expected all other threads to get DuplicateNotificationException");
				assertEquals(0, otherFailureCount.get(),
						"Expected no unexpected failures");
			}
		}
		
		@Test
		@DisplayName("when many threads each claim their own distinct key concurrently, every claim succeeds with no false duplicates")
		void concurrentMarkSeen_forDistinctKeys_allSucceedWithNoFalseRejections() throws InterruptedException {
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