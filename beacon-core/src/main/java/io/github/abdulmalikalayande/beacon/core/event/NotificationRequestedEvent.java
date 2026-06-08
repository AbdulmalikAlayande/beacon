package io.github.abdulmalikalayande.beacon.core.event;

import io.github.abdulmalikalayande.beacon.api.enums.NotificationChannel;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationPriority;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationType;
import io.github.abdulmalikalayande.beacon.api.request.NotificationRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Internal event raised when a notification request has been accepted.
 *
 * <p>This is a dedicated payload decoupled from {@link NotificationRequest}
 * so the event contract can evolve independently of the public API contract.
 * If fields are added or removed from the request, this event is unaffected
 * unless explicitly changed.
 *
 * @param notificationId   the generated notification identifier
 * @param idempotencyKey   stable unique key per logical event
 * @param userId           the recipient's host identifier
 * @param type             the notification category
 * @param priority         resolved priority (never null — derived from type if the request omitted it)
 * @param preferredChannels channels the host explicitly requested, or empty if none
 * @param context          template substitution data
 */
public record NotificationRequestedEvent(
		UUID notificationId,
		String idempotencyKey,
		String userId,
		NotificationType type,
		NotificationPriority priority,
		List<NotificationChannel> preferredChannels,
		Map<String, String> context
) {

	public boolean hasExplicitChannels() {
		return preferredChannels != null && !preferredChannels.isEmpty();
	}

	public static NotificationRequestedEvent from(UUID notificationId, NotificationRequest request) {
		return new NotificationRequestedEvent(
				notificationId,
				request.idempotencyKey(),
				request.userId(),
				request.type(),
				request.priority(),
				request.preferredChannels(),
				request.context()
		);
	}
}
