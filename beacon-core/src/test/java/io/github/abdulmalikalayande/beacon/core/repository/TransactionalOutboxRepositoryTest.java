package io.github.abdulmalikalayande.beacon.core.repository;

import io.github.abdulmalikalayande.beacon.api.enums.*;
import io.github.abdulmalikalayande.beacon.core.entity.DeliveryTaskEntity;
import io.github.abdulmalikalayande.beacon.core.entity.NotificationStatusEntity;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@DataJpaTest
@TestPropertySource(locations = "classpath:application-test.properties")
public class TransactionalOutboxRepositoryTest {
	
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private DeliveryTaskRepository deliveryTaskRepository;
	@Autowired
	private NotificationStatusRepository notificationStatusRepository;
	
	/**
	 * Verifies that the `notification_queue` database table exists and contains the expected columns.
	 <p>
	 * This test queries the database metadata to fetch the column names of the `notification_queue` table
	 * and asserts that the table includes all required columns with the correct names.
	 <p>
	 * Expected columns:
	 * - id
	 * - notification_id
	 * - idempotency_key
	 * - user_id
	 * - channel
	 * - type
	 * - priority
	 * - encrypted_context
	 * - status
	 * - retry_count
	 * - available_at
	 * - created_at
	 * - updated_at
	 <p>
	 * Assertions:
	 * - The table must exist and include non-empty column definitions.
	 * - The retrieved column names must match the expected list exactly, but order does not matter.
	 <p>
	 * Method uses `jdbcTemplate` to execute the database metadata query and performs assertions on the results.
	 */
	@Test
	public void assertDeliveryTaskTableExistsWithTheRightColumns(){
		String tableName = "notification_queue";
		String sqlQuery = "SELECT column_name FROM information_schema.columns WHERE table_name = ?";
		
		List<String> columns = jdbcTemplate.queryForList(sqlQuery, String.class, tableName);
		
		assertThat(columns).isNotEmpty();
		assertThat(columns).containsExactlyInAnyOrder(
			"id", "notification_id", "idempotency_key", "user_id",
			"channel", "type", "priority", "encrypted_context", "status",
			"retry_count", "available_at", "created_at", "updated_at"
		);
	}
	
	/**
	 * Tests the persistence and retrieval capabilities of the `DeliveryTaskEntity` within the database.
	 <p>
	 * This test performs the following operations:
	 * - Creates a new `DeliveryTaskEntity` with predefined fields and a unique task identifier.
	 * - Persists the created entity in the database using the repository's `saveAndFlush` method.
	 * - Retrieves the persisted entity using the repository's `findById` method.
	 * - Verifies that the retrieved entity matches the originally saved entity, including its field values.
	 <p>
	 * Assertions include:
	 * - The retrieved entity matches the persisted entity in its entirety using equality comparison.
	 * - Specific fields of the retrieved entity, such as `idempotencyKey`, `channel`, `status`, and dates
	 *   (`createdAt`, `updatedAt`, `availableAt`), contain the expected values.
	 * - Ensures proper mapping of fields such as `retryCount` and `encryptedContext`.
	 <p>
	 * Purpose:
	 * - Ensures that `DeliveryTaskEntity` objects are correctly saved, persisted, and retrieved in the
	 *   database with all expected field values correctly maintained.
	 */
	@Test
	public void assertThat_WhenDeliveryTaskEntityIsSaved_ItIsPersistedInTheDatabase_AndCanBeRetrieved(){
		UUID taskId = UUID.randomUUID();
		DeliveryTaskEntity deliveryTaskEntity = createDeliveryTaskEntity(taskId);
		deliveryTaskEntity.setChannel(NotificationChannel.SMS);
		deliveryTaskRepository.saveAndFlush(deliveryTaskEntity);
		
		DeliveryTaskEntity retrievedEntity = deliveryTaskRepository.findById(taskId).orElseThrow();
		
		assertThat(retrievedEntity).isEqualTo(deliveryTaskEntity);
		assertThat(retrievedEntity.getIdempotencyKey()).isEqualTo("payment-receipt-999");
		assertThat(retrievedEntity.getChannel()).isEqualTo(NotificationChannel.SMS);
		assertThat(retrievedEntity.getStatus()).isEqualTo(NotificationStatus.QUEUED);
		assertThat(retrievedEntity.getRetryCount()).isEqualTo(0);
		assertThat(retrievedEntity.getCreatedAt()).isEqualTo(Instant.parse("2026-06-02T08:44:01Z"));
		assertThat(retrievedEntity.getUpdatedAt()).isEqualTo(Instant.parse("2026-06-02T08:44:02Z"));
		assertThat(retrievedEntity.getEncryptedContext()).isEqualTo("encoded_cipher_text_here");
		assertThat(retrievedEntity.getAvailableAt()).isEqualTo(Instant.parse("2026-06-02T08:45:00Z"));
	}
	
	/**
	 * Verifies the persistence and retrieval of a delivery task entity in the database.
	 * This test method performs the following actions:
	 * - Inserts a new record into the "notification_queue" table using an SQL statement.
	 * - Confirms the number of rows affected by the insertion matches the expected value.
	 * - Queries the database for the inserted record using its unique identifier.
	 * - Asserts that the retrieved record's properties (such as idempotency key, user ID, status,
	 *   channel, type, priority, and retry count) match the values that were inserted.
	 <p>
	 * Ensures that:
	 * - A delivery task entity is successfully persisted in the database.
	 * - The persisted entity can be correctly retrieved with its associated data intact.
	 */
	@Test
	public void assertThat_WhenDeliveryTaskEntityIsSaved_ItIsPersistedInTheDatabase_AndCanBeRetrieved_Variant1() {
		String insertSql = "INSERT INTO notification_queue (" +
				                   "id, notification_id, idempotency_key, user_id, channel, type, priority, status, retry_count, available_at, created_at, updated_at" +
				                   ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), NOW())";
		
		java.util.UUID id = java.util.UUID.randomUUID();
		java.util.UUID notificationId = java.util.UUID.randomUUID();
		
		int rowsAffected = jdbcTemplate.update(insertSql,
				id, notificationId, "key_123", "user_456", "EMAIL", "WELCOME", "HIGH", "QUEUED", 0
		);
		
		assertThat(rowsAffected).isEqualTo(1);
		
		String selectSql = "SELECT idempotency_key, user_id, status, channel, type, priority, retry_count FROM notification_queue WHERE id = ?";
		Map<String, Object> result = jdbcTemplate.queryForMap(selectSql, id);
		
		assertThat(result.get("idempotency_key")).isEqualTo("key_123");
		assertThat(result.get("user_id")).isEqualTo("user_456");
		assertThat(result.get("status")).isEqualTo("QUEUED");
		assertThat(result.get("channel")).isEqualTo("EMAIL");
		assertThat(result.get("type")).isEqualTo("WELCOME");
		assertThat(result.get("priority")).isEqualTo("HIGH");
		assertThat(result.get("retry_count")).isEqualTo(0);
	}
	
	private static @NonNull DeliveryTaskEntity createDeliveryTaskEntity(UUID taskId) {
		DeliveryTaskEntity deliveryTaskEntity = new DeliveryTaskEntity();
		
		deliveryTaskEntity.setId(taskId);
		deliveryTaskEntity.setNotificationId(UUID.randomUUID());
		deliveryTaskEntity.setIdempotencyKey("payment-receipt-999");
		deliveryTaskEntity.setUserId("user-123");
		deliveryTaskEntity.setType(NotificationType.PAYMENT_RECEIPT);
		deliveryTaskEntity.setPriority(NotificationPriority.HIGH);
		deliveryTaskEntity.setEncryptedContext("encoded_cipher_text_here");
		deliveryTaskEntity.setStatus(NotificationStatus.QUEUED);
		deliveryTaskEntity.setRetryCount(0);
		deliveryTaskEntity.setAvailableAt(Instant.parse("2026-06-02T08:45:00Z"));
		deliveryTaskEntity.setCreatedAt(Instant.parse("2026-06-02T08:44:01Z"));
		deliveryTaskEntity.setUpdatedAt(Instant.parse("2026-06-02T08:44:02Z"));
		return deliveryTaskEntity;
	}
	
	@Test
	public void whenMultipleNotificationStatusEntityWithSameIdempotencyKeysButDifferentChannelsAreSaved_DBPersistenceForAllOfThemIsSuccessful(){
		UUID task1Id = UUID.randomUUID();
		UUID task2Id = UUID.randomUUID();
		UUID task3Id = UUID.randomUUID();
		
		NotificationStatusEntity notificationStatusEntity1 = createNotificationStatusEntity(task1Id);
		NotificationStatusEntity notificationStatusEntity2 = createNotificationStatusEntity(task2Id);
		NotificationStatusEntity notificationStatusEntity3 = createNotificationStatusEntity(task3Id);
		
		notificationStatusEntity1.setChannel(NotificationChannel.EMAIL);
		notificationStatusEntity2.setChannel(NotificationChannel.PUSH);
		notificationStatusEntity3.setChannel(NotificationChannel.SMS);
		
		notificationStatusRepository.saveAndFlush(notificationStatusEntity1);
		assertDoesNotThrow(()->{
			notificationStatusRepository.saveAllAndFlush(List.of(notificationStatusEntity2, notificationStatusEntity3));
		}, "");
		
		deliveryTaskRepository.findAll().forEach(deliveryTaskEntity -> {
			assertNotEquals(null, deliveryTaskEntity.getId(), "All delivery task entities should have an id");
		});
	}
	
	@Test
	public void shouldThrowConstraintViolationExceptionWhenMultipleNotificationStatusEntityWithSameIdempotencyKeysAndSameChannelAreSaved(){
		UUID task1Id = UUID.randomUUID();
		UUID task2Id = UUID.randomUUID();
		
		NotificationStatusEntity notificationStatusEntity1 = createNotificationStatusEntity(task1Id);
		NotificationStatusEntity notificationStatusEntity2 = createNotificationStatusEntity(task2Id);
		
		notificationStatusEntity1.setChannel(NotificationChannel.EMAIL);
		notificationStatusEntity2.setChannel(NotificationChannel.EMAIL);
		
		notificationStatusRepository.saveAndFlush(notificationStatusEntity1);
		assertThatThrownBy(() -> notificationStatusRepository.saveAndFlush(notificationStatusEntity2), "").isInstanceOf(DataIntegrityViolationException.class);
	}
	
	private static @NonNull NotificationStatusEntity createNotificationStatusEntity(UUID taskId) {
		NotificationStatusEntity notificationStatusEntity = new NotificationStatusEntity();
		
		notificationStatusEntity.setId(taskId);
		notificationStatusEntity.setNotificationId(UUID.randomUUID());
		notificationStatusEntity.setIdempotencyKey("payment-receipt-999");
		notificationStatusEntity.setUserId("user-123");
		notificationStatusEntity.setType(NotificationType.PAYMENT_RECEIPT);
		notificationStatusEntity.setProvider(ProviderName.MAILGUN);
		notificationStatusEntity.setStatus(NotificationStatus.QUEUED);
		notificationStatusEntity.setRetryCount(0);
		notificationStatusEntity.setCreatedAt(Instant.parse("2026-06-02T08:44:01Z"));
		notificationStatusEntity.setUpdatedAt(Instant.parse("2026-06-02T08:44:02Z"));
		return notificationStatusEntity;
	}
	
	public static void main(String[] args)  {
		String myString = """
				Hello {{name}}, how are you doing today?.
				We are pleased to inform you that your payment has been successfully processed.
				{% if amount %} Your payment amount is {{amount}}. {% endif %}
				{% for item in items %} {{item.name}} {% endfor %}
				
				Thank you {{name}} for your patronizing us once again.
				""";
		
		int i = 0;
		int len = myString.length();
		while (i < len) {
			System.out.println(myString.startsWith("{%", i));
			if (myString.startsWith("{%", i)) {
				int endIdx = myString.indexOf("}", i);
				if (endIdx != -1) throw new IllegalArgumentException("Unclosed tag block near index " + i);
				
				i = endIdx + 2;
			}
		}
	}
}
