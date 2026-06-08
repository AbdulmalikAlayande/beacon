package io.github.abdulmalikalayande.beacon.core.entity;

import io.github.abdulmalikalayande.beacon.api.enums.NotificationChannel;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationStatus;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationType;
import io.github.abdulmalikalayande.beacon.api.enums.ProviderName;
import jakarta.persistence.*;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
	name = "notification_status",
	uniqueConstraints = {@UniqueConstraint(
		name = "uq_status_idempotency_channel",
		columnNames = {"idempotency_key", "channel"}
	)}
)
public class NotificationStatusEntity {

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

	@Column(name = "provider")
	@Enumerated(EnumType.STRING)
	private ProviderName provider;

	@Column(name = "status", nullable = false)
	@Enumerated(EnumType.STRING)
	private NotificationStatus status;

	@Column(name = "retry_count", nullable = false)
	private Integer retryCount = 0;

	@Column(name = "failure_reason")
	private String failureReason;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/**
	 * Creates a new queued status record that mirrors a delivery task.
	 * Provider and failure reason are left null — they are set when
	 * delivery is actually attempted.
	 */
	public static NotificationStatusEntity fromTask(DeliveryTaskEntity task, Clock clock) {
		Instant now = clock.instant();
		NotificationStatusEntity record = new NotificationStatusEntity();
		record.notificationId = task.getNotificationId();
		record.idempotencyKey = task.getIdempotencyKey();
		record.userId = task.getUserId();
		record.channel = task.getChannel();
		record.type = task.getType();
		record.status = NotificationStatus.QUEUED;
		record.retryCount = 0;
		record.createdAt = now;
		record.updatedAt = now;
		return record;
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
	
	public ProviderName getProvider() {
		return provider;
	}
	
	public void setProvider(ProviderName provider) {
		this.provider = provider;
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
	
	public String getFailureReason() {
		return failureReason;
	}
	
	public void setFailureReason(String failureReason) {
		this.failureReason = failureReason;
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
		NotificationStatusEntity that = (NotificationStatusEntity) o;
		return id.equals(that.id);
	}
	
	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
