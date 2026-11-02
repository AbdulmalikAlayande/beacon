package io.github.abdulmalikalayande.beacon.core.listener;

import io.github.abdulmalikalayande.beacon.api.dto.DeliveryTask;
import io.github.abdulmalikalayande.beacon.api.dto.NotificationPreference;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationChannel;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationPriority;
import io.github.abdulmalikalayande.beacon.api.port.NotificationQueue;
import io.github.abdulmalikalayande.beacon.api.spi.NotificationTypeRegistry;
import io.github.abdulmalikalayande.beacon.api.spi.UserPreferenceResolver;
import io.github.abdulmalikalayande.beacon.core.crypto.ContextEncryptor;
import io.github.abdulmalikalayande.beacon.core.entity.NotificationStatusEntity;
import io.github.abdulmalikalayande.beacon.core.event.NotificationRequestedEvent;
import io.github.abdulmalikalayande.beacon.core.repository.NotificationStatusRepository;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

//todo: To implement tests for this class.
public class NotificationRequestedEventListener {

	private final Clock clock;
	private final ContextEncryptor contextEncryptor;
	private final NotificationQueue notificationQueue;
	private final UserPreferenceResolver userPreferenceResolver;
	private final NotificationTypeRegistry notificationTypeRegistry;
	private final NotificationStatusRepository notificationStatusRepository;
	private final Logger log = LoggerFactory.getLogger(NotificationRequestedEventListener.class);
	private final QuietHoursPolicy quietHoursPolicy = new QuietHoursPolicy();

	public NotificationRequestedEventListener(Clock clock, NotificationQueue notificationQueue,
											  UserPreferenceResolver userPreferenceResolver,
											  NotificationTypeRegistry notificationTypeRegistry,
											  NotificationStatusRepository notificationStatusRepository,
											  ContextEncryptor contextEncryptor) {
		this.contextEncryptor = contextEncryptor;
		this.clock = clock;
		this.notificationQueue = notificationQueue;
		this.userPreferenceResolver = userPreferenceResolver;
		this.notificationTypeRegistry = notificationTypeRegistry;
		this.notificationStatusRepository = notificationStatusRepository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)
	public void onNotificationRequested(NotificationRequestedEvent event) {
		NotificationPreference userPreference = userPreferenceResolver.resolve(event.userId());
		if (userPreference == null) {
			log.warn("No preferences found for user {}, skipping notification {}", event.userId(), event.idempotencyKey());
			return;
		}

		Set<NotificationChannel> channels = resolveEffectiveChannels(event, userPreference);
		if (channels.isEmpty()) {
			log.warn("No channel overlap for user {}, notification {}", event.userId(), event.idempotencyKey());
			return;
		}

		String encryptedContext = contextEncryptor.encrypt(event.context());
		channels.forEach(channel -> {
			DeliveryTask task = createDeliveryTask(event, channel, userPreference, encryptedContext);
			notificationQueue.enqueue(task);
			notificationStatusRepository.save(NotificationStatusEntity.fromTask(task, clock));
			
			log.info(
				"Enqueued delivery task {} for notification {} to user {} via channel {}",
				task.taskId(), task.notificationId(), task.userId(), task.channel().name()
			);
		});
	}

	private @NonNull DeliveryTask createDeliveryTask(NotificationRequestedEvent event, NotificationChannel channel,
													 NotificationPreference preference, String encryptedContext) {
		NotificationPriority priority = resolvePriority(event);
		Instant requestedAt = event.scheduledAt() != null ? event.scheduledAt() : clock.instant();

		return new DeliveryTask(
				UUID.randomUUID(), event.notificationId(), event.idempotencyKey(), event.userId(), channel,
				event.type(), priority, encryptedContext, 0, clock.instant(),
				quietHoursPolicy.availableAt(priority, preference, requestedAt)
		);
	}
	
	private Set<NotificationChannel> resolveEffectiveChannels(NotificationRequestedEvent event, NotificationPreference preference) {
		Set<NotificationChannel> channels = event.hasExplicitChannels()
				? new HashSet<>(event.preferredChannels())
				: new HashSet<>(notificationTypeRegistry.getSupportedChannels(event.type()));
		channels.retainAll(preference.enabledChannels());
		return channels;
	}

	private NotificationPriority resolvePriority(NotificationRequestedEvent event) {
		return event.priority() != null
				? event.priority()
				: notificationTypeRegistry.getPriority(event.type());
	}
}
