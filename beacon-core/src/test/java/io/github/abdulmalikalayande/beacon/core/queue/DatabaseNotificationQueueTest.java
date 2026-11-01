package io.github.abdulmalikalayande.beacon.core.queue;

import io.github.abdulmalikalayande.beacon.api.dto.DeliveryTask;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationChannel;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationPriority;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationType;
import io.github.abdulmalikalayande.beacon.api.port.NotificationQueue;
import io.github.abdulmalikalayande.beacon.core.config.BeaconCoreTestConfig;
import io.github.abdulmalikalayande.beacon.core.config.BeaconQueueConfig;
import io.github.abdulmalikalayande.beacon.core.repository.DeliveryTaskRepository;
import io.github.abdulmalikalayande.beacon.core.util.TestEncryptionUtil;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({ValidationAutoConfiguration.class, BeaconCoreTestConfig.class, BeaconQueueConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(locations = "classpath:application-test.properties")
class DatabaseNotificationQueueTest {
	
	@Autowired
	Clock clock;
	@Autowired
	NotificationQueue queue;
	@Autowired
	private DeliveryTaskRepository deliveryTaskRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	
	@BeforeEach
	void setUp() {
	}
	
	@AfterEach
	void tearDown() {
	
	}
	
	/**
	 * Creates a test delivery task with encrypted context.
	 *
	 * @return a populated delivery task ready for database testing
	 */
	private DeliveryTask deliveryTask(String context, NotificationChannel channel){
		String encryptedContext = TestEncryptionUtil.encrypt(context);
		return new DeliveryTask(
			UUID.randomUUID(),
			UUID.randomUUID(),
			"test-idempotency-key",
			UUID.randomUUID().toString(),
			channel,
			NotificationType.OTP,
			NotificationPriority.HIGH,
			encryptedContext,
			3,
			clock.instant(),
			clock.instant().plus(10, ChronoUnit.MINUTES)
		);
	}
	
	private void assertRowMatchesTask(DeliveryTask task) {
		Map<String, Object> row = jdbcTemplate.queryForMap(
			"SELECT * FROM notification_queue WHERE id = ?",
			task.taskId()
		);
		
		assertAll("row for task " + task.taskId(),
			() -> assertEquals(task.taskId().toString(), row.get("id").toString()),
			() -> assertEquals(task.notificationId().toString(), row.get("notification_id").toString()),
			() -> assertEquals(task.idempotencyKey(), row.get("idempotency_key")),
			() -> assertEquals(task.userId(), row.get("user_id")),
			() -> assertEquals(task.channel().name(), row.get("channel")),
			() -> assertEquals(task.type().name(), row.get("type")),
			() -> assertEquals(task.priority().name(), row.get("priority")),
			() -> assertEquals(task.encryptedContext(), row.get("encrypted_context")),
			() -> assertEquals("QUEUED", row.get("status")),
			() -> assertEquals(task.retryCount(), row.get("retry_count")),
			() -> assertEquals(task.availableAt(), ((Timestamp) row.get("available_at")).toInstant()),
			() -> assertEquals(task.createdAt(), ((Timestamp) row.get("created_at")).toInstant()),
			() -> assertEquals(clock.instant(), ((Timestamp) row.get("updated_at")).toInstant())
		);
	}
	
	
	@Test
	@DisplayName("putting in one task saves one row with every field intact")
	void putIn_oneTask_savesOneRowWithAllFields() {
		String contextJson = "{\"message\":\"Hello, World!\", \"otp\":\"547320\"}";
		DeliveryTask deliveryTask = deliveryTask(contextJson, NotificationChannel.EMAIL);
		queue.enqueue(deliveryTask);
		
		assertRowMatchesTask(deliveryTask);
	}
	
	@Test
	@DisplayName("putting in several tasks saves all of them")
	void putIn_severalTasks_savesAll() {
		List<DeliveryTask> deliveryTasks = List.of(
			deliveryTask("{\"message\":\"Hello, World!\", \"otp\":\"547320\"}", NotificationChannel.SMS),
			deliveryTask("{\"message\":\"Hello, World!\", \"otp\":\"547320\"}", NotificationChannel.EMAIL),
			deliveryTask("{\"message\":\"Hello, World!\", \"otp\":\"547320\"}", NotificationChannel.PUSH)
		);
		deliveryTasks.forEach(queue::enqueue);
		Integer rowCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM notification_queue", Integer.class);
		assertEquals(deliveryTasks.size(), rowCount, "exactly one row per task, no more, no less");
		deliveryTasks.forEach(this::assertRowMatchesTask);
	}
	
	@Test
	@DisplayName("putting in null is rejected with a clear error")
	void putIn_null_rejectedClearly() {
		assertThrows(ConstraintViolationException.class, () -> queue.enqueue(null), "must not be null");
	}
	
	// ================================================================
	// 2. CLAIMING: PICKING THE RIGHT ROWS
	// ================================================================
	
	@Test
	@DisplayName("claims a QUEUED row whose available_at is in the past")
	void claim_queuedRowInThePast_isClaimed() {
		// WHAT: one ready row, claim, get it back.
		// WHY: the most basic promise. Ready rows must come out.
	}
	
	@Test
	@DisplayName("claims a row whose available_at is exactly now")
	void claim_rowAvailableExactlyNow_isClaimed() {
		// WHAT: available_at == clock.instant(), it must be claimed.
		// WHY: our listener sets available_at to exactly "now". If the query
		//      uses < instead of <=, every fresh message waits an extra tick.
	}
	
	@Test
	@DisplayName("does not claim a row whose available_at is in the future")
	void claim_rowInTheFuture_isNotClaimed() {
		// WHAT: available_at = clock + 1 second, claim, get nothing.
		// WHY: future rows are quiet hours and retry backoff. Claiming them
		//      early breaks both features.
		// BONUS: this also proves the query uses the injected Clock, not the
		//        database's now(). The fixed test clock is in the past compared
		//        to the real date, so a query using now() would wrongly claim it.
	}
	
	@Test
	@DisplayName("does not claim rows that are already PROCESSING")
	void claim_processingRow_isNotClaimed() {
		// WHAT: a PROCESSING row is skipped.
		// WHY: PROCESSING means someone is already delivering it. Claiming it
		//      again means the user gets the message twice.
	}
	
	@Test
	@DisplayName("only QUEUED rows are claimed, whatever other status a row has")
	void claim_onlyQueuedStatusIsClaimed() {
		// WHAT: rows with other statuses (FAILED, PROCESSING) are all skipped.
		// WHY: later, failed rows will sit in this table (dead letters). They
		//      must never be picked up by accident.
	}
	
	@Test
	@DisplayName("claiming an empty queue returns an empty result, not an error")
	void claim_emptyQueue_returnsEmpty() {
		// WHAT: no rows, claim, get an empty list.
		// WHY: the poller runs every few seconds, and most of the time there
		//      is nothing to do. That must be boring, not an exception.
	}
	
	@Test
	@DisplayName("never returns more rows than the batch size")
	void claim_respectsBatchSize() {
		// WHAT: 10 ready rows, batch size 3, get exactly 3.
		// WHY: without a limit, one poller could grab 50,000 promo rows at once,
		//      run out of memory, and starve the other servers.
	}
	
	@Test
	@DisplayName("returns everything ready when fewer rows exist than the batch size")
	void claim_fewerRowsThanBatch_returnsAllReady() {
		// WHAT: 2 ready rows, batch size 10, get 2.
		// WHY: proves the batch size is a ceiling, not a requirement.
	}
	
	@Test
	@DisplayName("a batch size of zero or less is rejected")
	void claim_invalidBatchSize_rejected() {
		// WHAT: batch 0 or -1 throws a clear exception.
		// WHY: a bad config value should fail loudly at the first tick,
		//      not quietly claim nothing forever.
	}
	
	@Test
	@DisplayName("the oldest ready rows are claimed first")
	void claim_oldestAvailableFirst() {
		// WHAT: rows with different available_at, batch size 1, oldest wins.
		// WHY: fairness. Without ordering, an old message can wait forever
		//      while newer ones keep jumping ahead.
	}
	
	// ================================================================
	// 3. CLAIMING: WHAT HAPPENS TO CLAIMED ROWS
	// ================================================================
	
	@Test
	@DisplayName("claimed rows are PROCESSING in the database after the claim")
	void claim_marksRowsProcessing() {
		// WHAT: claim, then read the row fresh from the db, status is PROCESSING.
		// WHY: the lock disappears when the claim commits. PROCESSING is the
		//      only thing that stops other pollers from grabbing the same row.
	}
	
	@Test
	@DisplayName("claimed rows have updated_at set to the Clock's time")
	void claim_setsUpdatedAtFromClock() {
		// WHAT: after claim, updated_at == clock.instant().
		// WHY: stuck-row recovery uses updated_at to decide "this has been
		//      PROCESSING too long". If it isn't set, recovery can't work.
	}
	
	@Test
	@DisplayName("a row claimed once is not returned by the next claim")
	void claim_sameRowNeverReturnedTwice() {
		// WHAT: claim, claim again, second claim does not contain that row.
		// WHY: the simplest proof of "no double sending", even with one server.
	}
	
	@Test
	@DisplayName("the claimed task carries every field the worker will need")
	void claim_returnedTaskHasAllFields() {
		// WHAT: check id, notificationId, idempotencyKey, userId, channel, type,
		//       priority, context, retryCount on what claim returns.
		// WHY: the worker gets ONLY this object. If channel or userId is lost
		//      here, the message goes nowhere.
	}
	
	@Test
	@DisplayName("claiming does not change retry_count, available_at, or created_at")
	void claim_doesNotChangeOtherFields() {
		// WHAT: those three columns are the same before and after the claim.
		// WHY: claiming is "I'm taking this", not a retry. Bumping retry_count
		//      here would make healthy messages hit the retry limit early.
	}
	
	// ================================================================
	// 4. CONCURRENCY: SEVERAL SERVERS AT ONCE
	// ================================================================
	
	@Test
	@DisplayName("many threads claiming at once: every row is claimed exactly once")
	void concurrentClaims_everyRowClaimedExactlyOnce() {
		// WHAT: insert 100 rows, 10 threads claim in a loop until empty.
		//       Collect every claimed id. Expect 100 ids, no duplicates.
		// WHY: this IS the reason for "FOR UPDATE SKIP LOCKED". Duplicates mean
		//      double SMS. Missing ids mean lost messages. Both must be zero.
	}
	
	@Test
	@DisplayName("a claimer does not wait for rows another claimer has locked")
	void concurrentClaims_lockedRowsAreSkippedNotWaitedFor() {
		// WHAT: thread A opens a transaction, locks some rows, and HOLDS
		//       (use a latch). Thread B claims and must return OTHER rows
		//       quickly (put a short timeout on it).
		// WHY: plain FOR UPDATE would still avoid duplicates, but B would sit
		//      and wait for A. With 5 servers, they'd queue behind each other.
		//      SKIP LOCKED is what lets them all work at the same time, and
		//      only this test proves that part.
	}
	
	// ================================================================
	// 5. STUCK-ROW RECOVERY: A SERVER DIED MID-DELIVERY
	// ================================================================
	
	@Test
	@DisplayName("a row PROCESSING for longer than the timeout goes back to QUEUED")
	void recover_staleProcessingRow_returnsToQueued() {
		// WHAT: PROCESSING row with an old updated_at, run recovery, it's QUEUED.
		// WHY: if a server crashes after claiming, those rows would be stuck
		//      forever and never sent. This rescues them.
	}
	
	@Test
	@DisplayName("a row PROCESSING for less than the timeout is left alone")
	void recover_freshProcessingRow_untouched() {
		// WHAT: PROCESSING row with a recent updated_at stays PROCESSING.
		// WHY: that row is probably being delivered right now. Putting it back
		//      would let a second server send it again.
	}
	
	@Test
	@DisplayName("a row PROCESSING for exactly the timeout follows the rule you chose")
	void recover_exactlyAtTimeout_followsTheChosenRule() {
		// WHAT: pick "older than" or "older than or equal", and pin it.
		// WHY: boundary bugs hide here. Decide once, test it, never argue again.
	}
	
	@Test
	@DisplayName("a recovered row can be claimed again")
	void recover_thenClaim_rowIsClaimable() {
		// WHAT: recover, then claim, the row comes back out.
		// WHY: recovery is only useful if the rescued row actually gets sent.
	}
	
	@Test
	@DisplayName("recovery adds one to retry_count")
	void recover_incrementsRetryCount() {
		// WHAT: retry_count goes from 0 to 1 after recovery.
		// WHY: imagine a "poison" message that crashes the server every time.
		//      Without counting, it loops forever, crashing servers all day.
		//      Counting lets item 9's retry limit stop it.
	}
	
	@Test
	@DisplayName("recovery sets updated_at to the Clock's time")
	void recover_setsUpdatedAtFromClock() {
		// WHAT: after recovery, updated_at == clock.instant().
		// WHY: keeps the diary honest about when the row last changed.
	}
	
	@Test
	@DisplayName("recovery does not touch QUEUED rows or rows with other statuses")
	void recover_onlyTouchesStaleProcessingRows() {
		// WHAT: QUEUED and FAILED rows are unchanged after recovery.
		// WHY: recovery must be a scalpel, not a broom. Touching FAILED rows
		//      would resurrect dead letters.
	}
	
}
