package io.github.abdulmalikalayande.beacon.core.queue;

import io.github.abdulmalikalayande.beacon.api.dto.DeliveryTask;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationStatus;
import io.github.abdulmalikalayande.beacon.api.port.NotificationQueue;
import io.github.abdulmalikalayande.beacon.core.entity.DeliveryTaskEntity;
import io.github.abdulmalikalayande.beacon.core.repository.DeliveryTaskRepository;
import org.jspecify.annotations.NonNull;

import java.time.Clock;
import java.time.Instant;

/**
 *
 */
public class DatabaseNotificationQueue implements NotificationQueue {
	
	private final Clock clock;
	private final DeliveryTaskRepository deliveryTaskRepository;
	
	public DatabaseNotificationQueue(Clock clock, DeliveryTaskRepository deliveryTaskRepository) {
		this.clock = clock;
		this.deliveryTaskRepository = deliveryTaskRepository;
	}
	
	@Override
	public void enqueue(@NonNull DeliveryTask task) {
		DeliveryTaskEntity entity = new DeliveryTaskEntity();
		entity.setId(task.taskId());
		entity.setType(task.type());
		entity.setUserId(task.userId());
		entity.setChannel(task.channel());
		entity.setUpdatedAt(clock.instant());
		entity.setPriority(task.priority());
		entity.setCreatedAt(task.createdAt());
		entity.setAvailableAt(task.availableAt());
		entity.setRetryCount(task.retryCount());
		entity.setStatus(NotificationStatus.QUEUED);
		entity.setIdempotencyKey(task.idempotencyKey());
		entity.setNotificationId(task.notificationId());
		entity.setEncryptedContext(task.encryptedContext());
		
		deliveryTaskRepository.save(entity);
	}
	
	@Override
	public void enqueueDelayed(DeliveryTask task, Instant deliverAt) {
	
	}
	
	@Override
	public void enqueueDead(DeliveryTask task, String reason) {
	
	}
}
