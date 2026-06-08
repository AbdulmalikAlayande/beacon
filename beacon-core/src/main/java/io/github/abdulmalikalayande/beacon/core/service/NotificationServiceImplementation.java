package io.github.abdulmalikalayande.beacon.core.service;


import io.github.abdulmalikalayande.beacon.api.dto.NotificationStatusRecord;
import io.github.abdulmalikalayande.beacon.api.port.DeduplicationStore;
import io.github.abdulmalikalayande.beacon.api.request.BatchNotificationRequest;
import io.github.abdulmalikalayande.beacon.api.request.NotificationRequest;
import io.github.abdulmalikalayande.beacon.api.response.NotificationResponse;
import io.github.abdulmalikalayande.beacon.api.service.NotificationService;
import io.github.abdulmalikalayande.beacon.core.event.NotificationRequestedEvent;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.context.ApplicationEventPublisher;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;

import static io.github.abdulmalikalayande.beacon.api.enums.NotificationStatus.CREATED;

public class NotificationServiceImplementation implements NotificationService {
	
	private final Clock clock;
	private final Validator validator;
	private final ApplicationEventPublisher eventPublisher;
	private final DeduplicationStore deduplicationStore;
	
	public NotificationServiceImplementation(Clock clock, Validator validator, ApplicationEventPublisher eventPublisher, DeduplicationStore deduplicationStore) {
		this.clock = clock;
		this.validator = validator;
		this.eventPublisher = eventPublisher;
		this.deduplicationStore = deduplicationStore;
	}
	
	@Override
	public NotificationResponse send(NotificationRequest request) {
		Set<ConstraintViolation<NotificationRequest>> violations = validator.validate(request);
		if (!violations.isEmpty()) throw new ConstraintViolationException(violations);

		UUID notificationId = uuidFrom(request.idempotencyKey(), clock.instant().toString());
		deduplicationStore.markSeen(request.idempotencyKey());

		eventPublisher.publishEvent(NotificationRequestedEvent.from(notificationId, request));
		return new NotificationResponse(notificationId.toString(), request.idempotencyKey(), CREATED, clock.instant());
	}
	
	@Override
	public NotificationResponse sendBatch(BatchNotificationRequest request) {
		return null;
	}
	
	@Override
	public NotificationStatusRecord getStatus(String notificationId) {
		return null;
	}
	
	@Override
	public void cancelScheduled(String notificationId) {
	
	}
	
	
	
	public static UUID uuidFrom(String idempotencyKey, String instantText) {
		try {
			String input = idempotencyKey + "|" + instantText;
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
			
			long msb = 0;
			long lsb = 0;
			for (int i = 0; i < 8; i++) {
				msb = (msb << 8) | (hash[i] & 0xffL);
			}
			for (int i = 8; i < 16; i++) {
				lsb = (lsb << 8) | (hash[i] & 0xffL);
			}
			
			// UUID version/variant bits to make it a standard UUID
			msb &= 0xffffffffffff0fffL;
			msb |= 0x0000000000004000L; // version 4 style
			lsb &= 0x3fffffffffffffffL;
			lsb |= 0x8000000000000000L; // IETF variant
			
			return new UUID(msb, lsb);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
