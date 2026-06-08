package io.github.abdulmalikalayande.beacon.core.entity;

import io.github.abdulmalikalayande.beacon.api.enums.NotificationChannel;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationPriority;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationStatus;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationType;

import io.github.abdulmalikalayande.beacon.core.event.NotificationRequestedEvent;
import jakarta.persistence.*;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "notification_queue")
public class DeliveryTaskEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "notification_id", nullable = false)
	private UUID notificationId;

	@Column(name = "idempotency_key", nullable = false)
	private String idempotencyKey;

	@Column(name = "user_id", nullable = false)
	private String userId;

	@Column(name = "channel", nullable = false)
	@Enumerated(EnumType.STRING)
	private NotificationChannel channel;

	@Column(name = "type", nullable = false)
	@Enumerated(EnumType.STRING)
	private NotificationType type;

	@Column(name = "priority", nullable = false)
	@Enumerated(EnumType.STRING)
	private NotificationPriority priority;

	@Column(name = "encrypted_context")
	private String encryptedContext;

	@Column(name = "status", nullable = false)
	@Enumerated(EnumType.STRING)
	private NotificationStatus status;

	@Column(name = "retry_count", nullable = false)
	private Integer retryCount = 0;

	@Column(name = "available_at", nullable = false)
	private Instant availableAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/**
	 * Creates a new queued delivery task for the given channel.
	 *
	 * @param event             the accepted notification event
	 * @param channel           the delivery channel for this task
	 * @param clock             clock for timestamps
	 * @param encryptedContext  the encrypted template context, or {@code null}
	 */
	public static DeliveryTaskEntity createQueued(
			NotificationRequestedEvent event,
			NotificationChannel channel,
			Clock clock,
			String encryptedContext
	) {
		Instant now = clock.instant();
		DeliveryTaskEntity task = new DeliveryTaskEntity();
		task.notificationId = event.notificationId();
		task.idempotencyKey = event.idempotencyKey();
		task.userId = event.userId();
		task.type = event.type();
		task.priority = event.priority();
		task.channel = channel;
		task.status = NotificationStatus.QUEUED;
		task.retryCount = 0;
		task.availableAt = now;
		task.encryptedContext = encryptedContext;
		task.createdAt = now;
		task.updatedAt = now;
		return task;
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getNotificationId() {
		return notificationId;
	}

	public void setNotificationId(UUID notificationId) {
		this.notificationId = notificationId;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public void setIdempotencyKey(String idempotencyKey) {
		this.idempotencyKey = idempotencyKey;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public NotificationChannel getChannel() {
		return channel;
	}

	public void setChannel(NotificationChannel channel) {
		this.channel = channel;
	}

	public NotificationType getType() {
		return type;
	}

	public void setType(NotificationType type) {
		this.type = type;
	}

	public NotificationPriority getPriority() {
		return priority;
	}

	public void setPriority(NotificationPriority priority) {
		this.priority = priority;
	}

	public String getEncryptedContext() {
		return encryptedContext;
	}

	public void setEncryptedContext(String encryptedContext) {
		this.encryptedContext = encryptedContext;
	}

	public NotificationStatus getStatus() {
		return status;
	}

	public void setStatus(NotificationStatus status) {
		this.status = status;
	}

	public Integer getRetryCount() {
		return retryCount;
	}

	public void setRetryCount(Integer retryCount) {
		this.retryCount = retryCount;
	}

	public Instant getAvailableAt() {
		return availableAt;
	}

	public void setAvailableAt(Instant availableAt) {
		this.availableAt = availableAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		DeliveryTaskEntity that = (DeliveryTaskEntity) o;
		return Objects.equals(id, that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}

	@Override
	public String toString() {
		return "DeliveryTaskEntity{" +
				"id=" + id +
				", notificationId=" + notificationId +
				", idempotencyKey='" + idempotencyKey + '\'' +
				", userId='" + userId + '\'' +
				", channel=" + channel +
				", type=" + type +
				", priority=" + priority +
				", status=" + status +
				", retryCount=" + retryCount +
				", availableAt=" + availableAt +
				", createdAt=" + createdAt +
				", updatedAt=" + updatedAt +
				'}';
	}
}
