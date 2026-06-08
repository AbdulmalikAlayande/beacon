package io.github.abdulmalikalayande.beacon.core.listener;

import io.github.abdulmalikalayande.beacon.api.dto.NotificationPreference;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationChannel;
import io.github.abdulmalikalayande.beacon.api.spi.NotificationTypeRegistry;
import io.github.abdulmalikalayande.beacon.api.spi.UserPreferenceResolver;
import io.github.abdulmalikalayande.beacon.core.entity.DeliveryTaskEntity;
import io.github.abdulmalikalayande.beacon.core.entity.NotificationStatusEntity;
import io.github.abdulmalikalayande.beacon.core.event.NotificationRequestedEvent;
import io.github.abdulmalikalayande.beacon.core.repository.DeliveryTaskRepository;
import io.github.abdulmalikalayande.beacon.core.repository.NotificationStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

public class NotificationRequestedEventListener {

	private final Clock clock;
	private final DeliveryTaskRepository deliveryTaskRepository;
	private final UserPreferenceResolver userPreferenceResolver;
	private final NotificationTypeRegistry notificationTypeRegistry;
	private final NotificationStatusRepository notificationStatusRepository;
	private final Logger log = LoggerFactory.getLogger(NotificationRequestedEventListener.class);

	public NotificationRequestedEventListener(Clock clock, DeliveryTaskRepository deliveryTaskRepository,
	                                          UserPreferenceResolver userPreferenceResolver,
	                                          NotificationTypeRegistry notificationTypeRegistry,
	                                          NotificationStatusRepository notificationStatusRepository) {
		this.clock = clock;
		this.deliveryTaskRepository = deliveryTaskRepository;
		this.userPreferenceResolver = userPreferenceResolver;
		this.notificationStatusRepository = notificationStatusRepository;
		this.notificationTypeRegistry = notificationTypeRegistry;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@TransactionalEventListener(phase = AFTER_COMMIT)
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

		List<DeliveryTaskEntity> deliveryTasks = channels.stream()
				.map(channel -> DeliveryTaskEntity.createQueued(event, channel, clock, encryptContext(event.context())))
				.toList();
		deliveryTaskRepository.saveAll(deliveryTasks);

		List<NotificationStatusEntity> statusRecords = deliveryTasks.stream()
				.map(task -> NotificationStatusEntity.fromTask(task, clock))
				.toList();
		notificationStatusRepository.saveAll(statusRecords);
	}

	private Set<NotificationChannel> resolveEffectiveChannels(NotificationRequestedEvent event, NotificationPreference preference) {
		Set<NotificationChannel> channels = event.hasExplicitChannels()
				? new HashSet<>(event.preferredChannels())
				: new HashSet<>(notificationTypeRegistry.getSupportedChannels(event.type()));
		channels.retainAll(preference.enabledChannels());
		return channels;
	}

	private String encryptContext(Map<String, String> context) {
		//tbd: plug in encryption — returns null until an encryption strategy is implemented
		return null;
	}
}
