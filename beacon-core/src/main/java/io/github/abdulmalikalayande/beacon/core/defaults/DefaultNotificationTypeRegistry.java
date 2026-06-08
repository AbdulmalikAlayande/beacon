package io.github.abdulmalikalayande.beacon.core.defaults;

import io.github.abdulmalikalayande.beacon.api.enums.NotificationChannel;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationPriority;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationType;
import io.github.abdulmalikalayande.beacon.api.spi.NotificationTypeRegistry;

import java.util.List;

public class DefaultNotificationTypeRegistry implements NotificationTypeRegistry {
	
	
	@Override
	public NotificationPriority getPriority(NotificationType type) {
		return switch (type) {
			// High-security, time-critical actions where every second counts.
			// These bypass marketing queues entirely to prevent user authentication drop-off.
			case OTP, SECURITY_ALERT ->
					NotificationPriority.HIGH;
			
			// System problems and payment issues affect operations or revenue directly.
			// They need fast resolution but can handle a few seconds of queueing.
			case SYSTEM_ALERT, PAYMENT_FAILED ->
					NotificationPriority.HIGH;
			
			// Core business actions that must be delivered quickly but are not immediate emergencies.
			case PAYMENT_RECEIPT, ORDER_CONFIRMATION ->
					NotificationPriority.MEDIUM;
			
			// high volume, non-urgent traffic. Must be processed with the lowest priority
			// so they never block critical operational traffic.
			case PROMO, ENGAGEMENT ->
					NotificationPriority.LOW;
			
			default -> NotificationPriority.MEDIUM;
		};
	}
	
	
	@Override
	public List<NotificationChannel> getSupportedChannels(NotificationType type) {
		return switch (type) {
			// High-security, ultralow latency, and legally compliant pathways.
			// SMS is critical but expensive; Push is inexpensive but can be dismissed or blocked.
			case OTP ->
					List.of(NotificationChannel.SMS, NotificationChannel.PUSH);
			
			case SECURITY_ALERT, SYSTEM_ALERT, PAYMENT_FAILED ->
					List.of(NotificationChannel.PUSH, NotificationChannel.EMAIL, NotificationChannel.SMS);
			
			// Append-mostly financial and transactional records.
			// EMAIL acts as the nonrepudiable legal paper trail. PUSH handles the instant hook.
			case PAYMENT_RECEIPT, ORDER_CONFIRMATION ->
					List.of(NotificationChannel.EMAIL, NotificationChannel.PUSH);
			
			// Low-urgency, high-volume engagement workflows.
			// SMS is explicitly omitted here to prevent severe telco bill drainage.
			case PROMO, ENGAGEMENT ->
					List.of(NotificationChannel.PUSH, NotificationChannel.EMAIL);
			
			default -> List.of(NotificationChannel.EMAIL);
		};
	}
	
	//	@Override
	//	public List<NotificationChannel> getSupportedChannels(NotificationType type) {
	//		return switch (type) {
	//			case OTP -> List.of(NotificationChannel.EMAIL, NotificationChannel.SMS);
	//			case PAYMENT_RECEIPT, PAYMENT_FAILED -> List.of(NotificationChannel.SMS, NotificationChannel.EMAIL);
	//			case SECURITY_ALERT, ENGAGEMENT, ORDER_CONFIRMATION, SYSTEM_ALERT, PROMO ->
	//					List.of(NotificationChannel.PUSH, NotificationChannel.EMAIL);
	//			default -> List.of();
	//		};
	//	}
}
