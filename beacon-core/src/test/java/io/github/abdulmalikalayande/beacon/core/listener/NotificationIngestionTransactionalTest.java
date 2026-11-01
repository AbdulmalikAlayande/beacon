package io.github.abdulmalikalayande.beacon.core.listener;

import io.github.abdulmalikalayande.beacon.api.dto.NotificationPreference;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationChannel;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationPriority;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationStatus;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationType;
import io.github.abdulmalikalayande.beacon.api.exception.DuplicateNotificationException;
import io.github.abdulmalikalayande.beacon.api.port.NotificationQueue;
import io.github.abdulmalikalayande.beacon.api.request.NotificationRequest;
import io.github.abdulmalikalayande.beacon.api.service.NotificationService;
import io.github.abdulmalikalayande.beacon.api.spi.UserPreferenceResolver;
import io.github.abdulmalikalayande.beacon.core.config.BeaconCoreTestConfig;
import io.github.abdulmalikalayande.beacon.core.crypto.AesGcmContextEncryptor;
import io.github.abdulmalikalayande.beacon.core.crypto.ContextEncryptor;
import io.github.abdulmalikalayande.beacon.core.dedup.JdbcDeduplicationStore;
import io.github.abdulmalikalayande.beacon.core.defaults.DefaultNotificationTypeRegistry;
import io.github.abdulmalikalayande.beacon.core.entity.DeliveryTaskEntity;
import io.github.abdulmalikalayande.beacon.core.entity.NotificationStatusEntity;
import io.github.abdulmalikalayande.beacon.core.queue.DatabaseNotificationQueue;
import io.github.abdulmalikalayande.beacon.core.repository.DeliveryTaskRepository;
import io.github.abdulmalikalayande.beacon.core.repository.NotificationStatusRepository;
import io.github.abdulmalikalayande.beacon.core.service.NotificationServiceImplementation;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.github.abdulmalikalayande.beacon.api.enums.NotificationChannel.EMAIL;
import static io.github.abdulmalikalayande.beacon.api.enums.NotificationChannel.PUSH;
import static io.github.abdulmalikalayande.beacon.api.enums.NotificationChannel.SMS;
import static io.github.abdulmalikalayande.beacon.api.enums.NotificationPriority.*;
import static io.github.abdulmalikalayande.beacon.api.enums.NotificationType.*;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the ingestion path of item 5: {@link NotificationServiceImplementation#send}
 * publishing {@code NotificationRequestedEvent}, and {@link NotificationRequestedEventListener}
 * writing the queue and status rows after the host's transaction commits.
 *
 * <p>The class-level {@code @Transactional(NOT_SUPPORTED)} switches off the slice's automatic
 * per-test transaction. That is required here: the listener only runs after a real commit, so
 * each test opens and commits (or rolls back) its own "host" transaction through
 * {@link TransactionTemplate}, exactly the way a host application would.
 *
 * <p>Because rows are really committed, tables are cleared before and after every test.
 */
@DataJpaTest
@TestPropertySource(locations = "classpath:application-test.properties")
@Import({BeaconCoreTestConfig.class, NotificationIngestionTransactionalTest.IngestionTestBeans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("Notification ingestion: send() + NotificationRequestedEventListener against real Postgres")
class NotificationIngestionTransactionalTest {

	/**
	 * Wires the real service and the real listener as Spring beans, so the listener is
	 * proxied for @Transactional and registered for @TransactionalEventListener.
	 * The host SPI (UserPreferenceResolver) is the only mock.
	 */
	static class IngestionTestBeans {

		@Bean
		UserPreferenceResolver userPreferenceResolver() {
			return mock(UserPreferenceResolver.class);
		}
		
		@Bean
		NotificationQueue notificationQueue(Clock clock, DeliveryTaskRepository deliveryTaskRepository) {
			return new DatabaseNotificationQueue(clock, deliveryTaskRepository);
		}

		@Bean
		ContextEncryptor contextEncryptor(){
			final byte[] KEY = new byte[32];
			for (int i = 0; i < 32; i++) { KEY[i] = (byte) i; }
			return new AesGcmContextEncryptor(KEY);
		}

		@Bean
		NotificationService notificationService(Clock clock,
		                                        ApplicationEventPublisher eventPublisher,
		                                        DataSource dataSource) {
			return new NotificationServiceImplementation(
					clock,
					Validation.buildDefaultValidatorFactory().getValidator(),
					eventPublisher,
					new JdbcDeduplicationStore(new JdbcTemplate(dataSource))
			);
		}

		@Bean
		NotificationRequestedEventListener notificationRequestedEventListener(
				Clock clock,
				NotificationQueue queue,
				ContextEncryptor contextEncryptor,
				UserPreferenceResolver userPreferenceResolver,
				NotificationStatusRepository notificationStatusRepository) {
			return new NotificationRequestedEventListener(
				clock, queue, userPreferenceResolver, new DefaultNotificationTypeRegistry(),
				notificationStatusRepository, contextEncryptor
			);
		}
	}

	@Autowired private Clock clock;
	@Autowired private DataSource dataSource;
	@Autowired private ContextEncryptor contextEncryptor;
	@Autowired private NotificationService notificationService;
	@Autowired private DeliveryTaskRepository deliveryTaskRepository;
	@Autowired private PlatformTransactionManager transactionManager;
	@Autowired private UserPreferenceResolver userPreferenceResolver;
	@Autowired private NotificationStatusRepository notificationStatusRepository;

	private JdbcTemplate jdbcTemplate;
	private TransactionTemplate hostTransaction;

	@BeforeEach
	void setUp() {
		hostTransaction = new TransactionTemplate(transactionManager);
		jdbcTemplate = new JdbcTemplate(dataSource);
		reset(userPreferenceResolver);
		clearTables();
	}

	@AfterEach
	void tearDown() {
		clearTables();
	}

	// ------------------------------------------------------------------
	// Transactional binding: the reason the event mechanism exists
	// ------------------------------------------------------------------

	@Test
	@DisplayName("when the host transaction commits, one queue row per resolved channel is saved")
	void hostTransactionCommits_tasksAreQueued() {
		String userId = userWithChannels(EMAIL, SMS, PUSH);
		NotificationRequest request = requestFor(userId, SECURITY_ALERT, List.of());

		sendInsideCommittedTransaction(request);

		List<DeliveryTaskEntity> tasks = queuedTasksFor(request);
		assertEquals(3, tasks.size(), "SECURITY_ALERT supports all three channels and the user enabled all three");
		assertEquals(Set.of(EMAIL, SMS, PUSH), channelsOf(tasks));
		verify(userPreferenceResolver, times(1)).resolve(userId);
	}

	@Test
	@DisplayName("the listener does not run while the host transaction is still open, only after it commits")
	void listenerWaitsForCommit_notForPublish() {
		String userId = userWithChannels(EMAIL, SMS, PUSH);
		NotificationRequest request = requestFor(userId, SECURITY_ALERT, List.of());

		hostTransaction.executeWithoutResult(status -> {
			notificationService.send(request);

			// Still inside the host transaction: nothing may be queued yet.
			assertTrue(queuedTasksFor(request).isEmpty(),
					"Rows were written before the host committed, so the listener is not bound to AFTER_COMMIT");
			verify(userPreferenceResolver, never()).resolve(any());
		});

		assertEquals(3, queuedTasksFor(request).size(), "Rows must appear once the host transaction commits");
	}

	@Test
	@DisplayName("when the host transaction rolls back because of an exception, nothing is queued and the listener never runs")
	void hostTransactionThrows_nothingIsQueued() {
		String userId = userWithChannels(EMAIL, SMS, PUSH);
		NotificationRequest request = requestFor(userId, SECURITY_ALERT, List.of());

		assertThrows(SimulatedHostFailure.class, () ->
				hostTransaction.executeWithoutResult(status -> {
					notificationService.send(request);
					throw new SimulatedHostFailure();
				}));

		assertTrue(queuedTasksFor(request).isEmpty(), "A rolled-back host transaction must not leave queue rows");
		assertTrue(statusRecordsFor(request).isEmpty(), "A rolled-back host transaction must not leave status rows");
		verify(userPreferenceResolver, never()).resolve(any());
	}

	@Test
	@DisplayName("when the host marks its transaction rollback-only, nothing is queued and the listener never runs")
	void hostTransactionMarkedRollbackOnly_nothingIsQueued() {
		String userId = userWithChannels(EMAIL, SMS, PUSH);
		NotificationRequest request = requestFor(userId, SECURITY_ALERT, List.of());

		hostTransaction.executeWithoutResult(status -> {
			notificationService.send(request);
			status.setRollbackOnly();
		});

		assertTrue(queuedTasksFor(request).isEmpty());
		assertTrue(statusRecordsFor(request).isEmpty());
		verify(userPreferenceResolver, never()).resolve(any());
	}

	@Test
	@DisplayName("a rolled-back send releases its dedup claim, so the host can retry the same key and it gets queued")
	void rolledBackSend_releasesDedupClaim_retrySucceeds() {
		// Only true for JdbcDeduplicationStore: its insert joins the host transaction and rolls back with it.
		// DefaultDeduplicationStore and RedisDeduplicationStore are outside the database transaction,
		// so with them the retry below would be rejected as a duplicate.
		String userId = userWithChannels(EMAIL, PUSH);
		NotificationRequest request = requestFor(userId, ORDER_CONFIRMATION, List.of());

		assertThrows(SimulatedHostFailure.class, () ->
				hostTransaction.executeWithoutResult(status -> {
					notificationService.send(request);
					throw new SimulatedHostFailure();
				}));

		assertEquals(0, dedupRowsFor(request.idempotencyKey()), "The dedup claim must roll back with the host transaction");

		assertDoesNotThrow(() -> sendInsideCommittedTransaction(request),
				"Retrying the same key after a rollback must not be treated as a duplicate");

		assertEquals(Set.of(EMAIL, PUSH), channelsOf(queuedTasksFor(request)));
		assertEquals(1, dedupRowsFor(request.idempotencyKey()));
	}

	@Test
	@DisplayName("when send() is called with no transaction at all, the notification is queued immediately")
	void noTransaction_tasksAreQueuedImmediately() {
		assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
				"Precondition: this test must run with no active transaction");

		String userId = userWithChannels(EMAIL, SMS, PUSH);
		NotificationRequest request = requestFor(userId, SECURITY_ALERT, List.of());

		notificationService.send(request);

		assertEquals(3, queuedTasksFor(request).size(),
				"With no transaction the event must still be handled (needs fallbackExecution = true on the listener)");
		assertEquals(3, statusRecordsFor(request).size());
	}

	// ------------------------------------------------------------------
	// Duplicate requests, end to end
	// ------------------------------------------------------------------

	@Test
	@DisplayName("a second committed send with the same key is rejected and does not fan out a second time")
	void committedDuplicate_isRejected_andNotFannedOutTwice() {
		String userId = userWithChannels(EMAIL, SMS, PUSH);
		NotificationRequest request = requestFor(userId, SECURITY_ALERT, List.of());

		sendInsideCommittedTransaction(request);

		assertThrows(DuplicateNotificationException.class, () -> sendInsideCommittedTransaction(request));

		assertEquals(3, queuedTasksFor(request).size(), "Only the first send may produce queue rows");
		assertEquals(3, statusRecordsFor(request).size(), "Only the first send may produce status rows");
		verify(userPreferenceResolver, times(1)).resolve(userId);
	}

	// ------------------------------------------------------------------
	// What the saved rows contain
	// ------------------------------------------------------------------

	@Test
	@DisplayName("a queued task carries every field from the request, with timestamps from the injected Clock")
	void queuedTask_carriesEveryField() {
		String userId = userWithChannels(SMS);
		NotificationRequest request = requestFor(userId, OTP, List.of());

		sendInsideCommittedTransaction(request);

		List<DeliveryTaskEntity> tasks = queuedTasksFor(request);
		assertEquals(1, tasks.size());
		DeliveryTaskEntity task = tasks.getFirst();

		assertAll(
				() -> assertNotNull(task.getId(), "Row id must be generated on insert"),
				() -> assertEquals(NotificationServiceImplementation.uuidFrom(request.idempotencyKey()), task.getNotificationId()),
				() -> assertEquals(request.idempotencyKey(), task.getIdempotencyKey()),
				() -> assertEquals(userId, task.getUserId()),
				() -> assertEquals(SMS, task.getChannel()),
				() -> assertEquals(OTP, task.getType()),
				() -> assertEquals(HIGH, task.getPriority()),
				() -> assertEquals(NotificationStatus.QUEUED, task.getStatus()),
				() -> assertEquals(0, task.getRetryCount().intValue()),
				() -> assertEquals(clock.instant(), task.getAvailableAt(), "HIGH priority is always available immediately"),
				() -> assertEquals(clock.instant(), task.getCreatedAt()),
				() -> assertEquals(clock.instant(), task.getUpdatedAt())
		);
	}

	@Test
	@DisplayName("every queued task gets a matching status record that starts the audit trail")
	void statusRecords_mirrorTheQueuedTasks() {
		String userId = userWithChannels(EMAIL, SMS, PUSH);
		NotificationRequest request = requestFor(userId, SECURITY_ALERT, List.of());

		sendInsideCommittedTransaction(request);

		List<DeliveryTaskEntity> tasks = queuedTasksFor(request);
		List<NotificationStatusEntity> statusRecords = statusRecordsFor(request);

		assertEquals(tasks.size(), statusRecords.size(), "One status record per queue row");
		assertEquals(channelsOf(tasks),
				statusRecords.stream().map(NotificationStatusEntity::getChannel).collect(Collectors.toSet()));

		for (NotificationStatusEntity statusRecord : statusRecords) {
			assertAll(
					() -> assertEquals(NotificationServiceImplementation.uuidFrom(request.idempotencyKey()), statusRecord.getNotificationId()),
					() -> assertEquals(request.idempotencyKey(), statusRecord.getIdempotencyKey()),
					() -> assertEquals(userId, statusRecord.getUserId()),
					() -> assertEquals(SECURITY_ALERT, statusRecord.getType()),
					() -> assertEquals(NotificationStatus.QUEUED, statusRecord.getStatus()),
					() -> assertNull(statusRecord.getProvider(), "No provider until delivery is attempted"),
					() -> assertNull(statusRecord.getFailureReason(), "No failure reason until something fails"),
					() -> assertEquals(0, statusRecord.getRetryCount().intValue()),
					() -> assertEquals(clock.instant(), statusRecord.getCreatedAt())
			);
		}
	}

	// ------------------------------------------------------------------
	// Fan-out: which channels get a row
	// ------------------------------------------------------------------

	@Test
	@DisplayName("without explicit channels, only channels the type supports AND the user enabled are queued")
	void noExplicitChannels_queuesIntersectionOfTypeAndUser() {
		// ORDER_CONFIRMATION supports EMAIL and PUSH; the user enabled EMAIL and SMS.
		String userId = userWithChannels(EMAIL, SMS);
		NotificationRequest request = requestFor(userId, ORDER_CONFIRMATION, List.of());

		sendInsideCommittedTransaction(request);

		assertEquals(Set.of(EMAIL), channelsOf(queuedTasksFor(request)));
	}

	@Test
	@DisplayName("explicit channels from the host are still limited to what the user enabled")
	void explicitChannels_areIntersectedWithUserChannels() {
		String userId = userWithChannels(EMAIL, PUSH);
		NotificationRequest request = requestFor(userId, SECURITY_ALERT, List.of(EMAIL, SMS));

		sendInsideCommittedTransaction(request);

		assertEquals(Set.of(EMAIL), channelsOf(queuedTasksFor(request)));
	}

	@Test
	@DisplayName("explicit channels from the host take precedence over the type's supported channels")
	void explicitChannels_takePrecedenceOverTypeRegistry() {
		// ORDER_CONFIRMATION does not list SMS in the registry, but the host asked for it explicitly.
		String userId = userWithChannels(SMS);
		NotificationRequest request = requestFor(userId, ORDER_CONFIRMATION, List.of(SMS));

		sendInsideCommittedTransaction(request);

		assertEquals(Set.of(SMS), channelsOf(queuedTasksFor(request)));
	}

	@Test
	@DisplayName("when the type's channels and the user's channels do not overlap, nothing is queued and nothing fails")
	void noChannelOverlap_nothingQueued_noException() {
		// OTP supports SMS and PUSH; the user only enabled EMAIL.
		String userId = userWithChannels(EMAIL);
		NotificationRequest request = requestFor(userId, OTP, List.of());

		assertDoesNotThrow(() -> sendInsideCommittedTransaction(request));

		assertTrue(queuedTasksFor(request).isEmpty());
		assertTrue(statusRecordsFor(request).isEmpty());
		verify(userPreferenceResolver, times(1)).resolve(userId);
	}

	@Test
	@DisplayName("a user with no enabled channels gets nothing queued and nothing fails")
	void userWithNoEnabledChannels_nothingQueued() {
		String userId = userWithChannels();
		NotificationRequest request = requestFor(userId, SECURITY_ALERT, List.of());

		assertDoesNotThrow(() -> sendInsideCommittedTransaction(request));

		assertTrue(queuedTasksFor(request).isEmpty());
		assertTrue(statusRecordsFor(request).isEmpty());
	}

	// ------------------------------------------------------------------
	// User resolution
	// ------------------------------------------------------------------

	@Test
	@DisplayName("when the host's resolver does not know the user, the listener skips quietly and nothing is queued")
	void unknownUser_nothingQueued_noException() {
		// Not stubbed, so the mock resolver returns null for this user.
		String unknownUserId = UUID.randomUUID().toString();
		NotificationRequest request = requestFor(unknownUserId, SECURITY_ALERT, List.of());

		assertDoesNotThrow(() -> sendInsideCommittedTransaction(request));

		assertTrue(queuedTasksFor(request).isEmpty());
		assertTrue(statusRecordsFor(request).isEmpty());
		verify(userPreferenceResolver, times(1)).resolve(unknownUserId);
	}

	@Test
	@DisplayName("when the request has no priority, the task gets the type's priority from the registry")
	void missingPriority_isResolvedFromTypeRegistry() {
		String userId = userWithChannels(EMAIL);
		NotificationRequest request = requestFor(userId, ORDER_CONFIRMATION, List.of(), null);

		sendInsideCommittedTransaction(request);

		List<DeliveryTaskEntity> tasks = queuedTasksFor(request);
		assertEquals(1, tasks.size());
		assertEquals(MEDIUM, tasks.getFirst().getPriority());
	}

	@Test
	@DisplayName("the saved context is encrypted and decrypts back to the original")
	void savedContext_isEncrypted_andDecryptsBack() {
		String userId = userWithChannels(SMS);
		NotificationRequest request = new NotificationRequest(UUID.randomUUID().toString(), userId, OTP, HIGH,
				List.of(), Map.of("name", "Ada", "otp", "547320"), null);

		sendInsideCommittedTransaction(request);

		String stored = queuedTasksFor(request).getFirst().getEncryptedContext();
		assertNotNull(stored);
		assertFalse(stored.contains("547320"));
		assertFalse(stored.contains("Ada"));
		assertEquals(request.context(), contextEncryptor.decrypt(stored));
	}

	@Test
	@DisplayName("a LOW notification inside the user's quiet hours is held until the window ends")
	void lowPriorityInsideQuietHours_isHeldUntilWindowEnds() {
		String userId = userWithQuietHours(LocalTime.of(22, 0), LocalTime.of(7, 0), EMAIL, PUSH);
		NotificationRequest request = requestFor(userId, PROMO, List.of(), LOW);

		sendInsideCommittedTransaction(request);

		List<DeliveryTaskEntity> tasks = queuedTasksFor(request);
		assertEquals(2, tasks.size());
		tasks.forEach(task -> assertEquals(Instant.parse("2026-05-31T06:00:00Z"), task.getAvailableAt()));
	}

	@Test
	@DisplayName("a HIGH notification inside the user's quiet hours is available immediately")
	void highPriorityInsideQuietHours_isAvailableNow() {
		String userId = userWithQuietHours(LocalTime.of(22, 0), LocalTime.of(7, 0), EMAIL);
		NotificationRequest request = requestFor(userId, SECURITY_ALERT, List.of(), HIGH);

		sendInsideCommittedTransaction(request);

		assertEquals(clock.instant(), queuedTasksFor(request).getFirst().getAvailableAt());
	}


	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	/**
	 * All requests use HIGH priority on purpose, so these tests stay valid once quiet hours
	 * are added (HIGH always bypasses quiet hours). Quiet-hours behavior gets its own tests.
	 */
	private NotificationRequest requestFor(String userId, NotificationType type, List<NotificationChannel> preferredChannels) {
		return requestFor(userId, type, preferredChannels, HIGH);
	}

	private NotificationRequest requestFor(String userId, NotificationType type,
										   List<NotificationChannel> preferredChannels, NotificationPriority priority) {
		return new NotificationRequest(UUID.randomUUID().toString(), userId, type, priority,
				preferredChannels, Map.of("name", "Ada"), null);
	}

	private String userWithChannels(NotificationChannel... enabledChannels) {
		return userWithQuietHours(null, null, enabledChannels);
	}

	private String userWithQuietHours(LocalTime start, LocalTime end, NotificationChannel... enabledChannels) {
		String userId = UUID.randomUUID().toString();
		NotificationPreference preference = new NotificationPreference(userId, "ada@example.com", "+2348012345678",
				"fcm-token-ada", Set.of(enabledChannels), "Africa/Lagos", start, end);
		when(userPreferenceResolver.resolve(userId)).thenReturn(preference);
		return userId;
	}

	private void sendInsideCommittedTransaction(NotificationRequest request) {
		hostTransaction.executeWithoutResult(status -> notificationService.send(request));
	}

	private List<DeliveryTaskEntity> queuedTasksFor(NotificationRequest request) {
		return deliveryTaskRepository.findAll().stream()
				.filter(task -> task.getIdempotencyKey().equals(request.idempotencyKey()))
				.toList();
	}

	private List<NotificationStatusEntity> statusRecordsFor(NotificationRequest request) {
		return notificationStatusRepository.findAll().stream()
				.filter(statusRecord -> statusRecord.getIdempotencyKey().equals(request.idempotencyKey()))
				.toList();
	}

	private Set<NotificationChannel> channelsOf(List<DeliveryTaskEntity> tasks) {
		return tasks.stream().map(DeliveryTaskEntity::getChannel).collect(Collectors.toSet());
	}

	private int dedupRowsFor(String idempotencyKey) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM deduplication_store WHERE idempotency_key = ?", Integer.class, idempotencyKey);
		return count == null ? 0 : count;
	}

	private void clearTables() {
		deliveryTaskRepository.deleteAllInBatch();
		notificationStatusRepository.deleteAllInBatch();
		jdbcTemplate.update("DELETE FROM deduplication_store");
	}

	private static final class SimulatedHostFailure extends RuntimeException {
		SimulatedHostFailure() {
			super("Simulated failure in the host's business logic");
		}
	}
}