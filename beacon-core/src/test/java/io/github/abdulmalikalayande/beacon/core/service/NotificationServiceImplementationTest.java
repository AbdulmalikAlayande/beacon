package io.github.abdulmalikalayande.beacon.core.service;

import static io.github.abdulmalikalayande.beacon.api.enums.NotificationType.*;
import static io.github.abdulmalikalayande.beacon.api.enums.NotificationChannel.*;
import static io.github.abdulmalikalayande.beacon.api.enums.NotificationPriority.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.github.abdulmalikalayande.beacon.api.enums.NotificationStatus;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationType;
import io.github.abdulmalikalayande.beacon.api.exception.DuplicateNotificationException;
import io.github.abdulmalikalayande.beacon.api.exception.NotificationException;
import io.github.abdulmalikalayande.beacon.api.port.DeduplicationStore;
import io.github.abdulmalikalayande.beacon.api.request.NotificationRequest;
import io.github.abdulmalikalayande.beacon.api.response.NotificationResponse;
import io.github.abdulmalikalayande.beacon.api.service.NotificationService;

import io.github.abdulmalikalayande.beacon.core.dedup.DefaultDeduplicationStore;
import io.github.abdulmalikalayande.beacon.core.event.NotificationRequestedEvent;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.validation.beanvalidation.CustomValidatorBean;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@DisplayName("DefaultNotificationService")
public class NotificationServiceImplementationTest {
	
	Clock clock;
	static Validator validator;
	ApplicationEventPublisher eventPublisher;
	NotificationService service;
	DeduplicationStore deduplicationStore;
	
	@BeforeAll
	static void executeOneBeforeAll(){
		try (var context = Validation.buildDefaultValidatorFactory()) {
			validator = context.getValidator();
		} catch (Exception e) {
			validator = new CustomValidatorBean();
		}
	}
	
	@BeforeEach
	void beginEachTestWith(){
		clock = Clock.fixed(Instant.parse("2026-07-25T15:00:00Z"), Clock.systemUTC().getZone());
		deduplicationStore = new DefaultDeduplicationStore(clock);
		eventPublisher = mock(ApplicationEventPublisher.class);
		service = new NotificationServiceImplementation(clock, validator, eventPublisher, deduplicationStore);
	}
	
	@Nested
	@DisplayName("Notification Request Validation Tests")
	class RequestValidationTests {
		
		
		@Test
		@DisplayName("Test that request contains a blank idempotency key, request is rejected and exception is thrown, event publisher was never called")
		void blankIdempotencyKey_RequestIsRejected_ExceptionThrown() {
			String idempotencyKey = "";
			String userId = "ed567ghftry6789jwehnhjskdhrgjfk";
			
			NotificationRequest request = createNotificationRequest(idempotencyKey, userId, ORDER_CONFIRMATION);
			
			assertThatThrownBy(() -> service.send(request))
				.hasMessageContaining("idempotencyKey must not be blank")
				.isExactlyInstanceOf(ConstraintViolationException.class);

			verify(eventPublisher, never()).publishEvent(any());
		}
		
		@Test
		@DisplayName("Test that request contains a blank user id, request is rejected and exception is thrown, event publisher was never called")
		void blankUserId_RequestIsRejected_ExceptionThrown() {
			String idempotencyKey = "ed567ghftry6789jwehnhjskdhrgjfk";
			String userId = "";
			NotificationRequest request = createNotificationRequest(idempotencyKey, userId, ORDER_CONFIRMATION);
			
			assertThatThrownBy(() -> service.send(request))
				.hasMessageContaining("userId must not be blank")
				.isExactlyInstanceOf(ConstraintViolationException.class);
			
			verify(eventPublisher, never()).publishEvent(any());
		}
		
		@Test
		@DisplayName("Test that request contains a null notification type, request is rejected and exception is thrown, event publisher was never called")
		void notificationTypeIsNull_RequestIsRejected() {
			String idempotencyKey = "ed567ghftry6789jwehnhjskdhrgjfk";
			String userId = "f1234tryuolfhyrieohfjed90943e";
			NotificationRequest request = createNotificationRequest(idempotencyKey, userId, null);
			
			assertThatThrownBy(() -> service.send(request))
				.hasMessageContaining("type must not be null")
				.isExactlyInstanceOf(ConstraintViolationException.class);
			
			verify(eventPublisher, never()).publishEvent(any());
		}
		
		@Test
		@DisplayName("Test that a valid request is passed, validation passes and NotificationRequestedEvent is published exactly once")
		void validRequestIsPassed_ValidationPasses_EventIsPublished() {
			String idempotencyKey = "550e8400e29b41d4a716446655440000";
			String userId = "f1234tryuolfhyrieohfjed90943e";
			NotificationRequest request = createNotificationRequest(idempotencyKey, userId, ORDER_CONFIRMATION);
			
			assertDoesNotThrow(() -> service.send(request));
			verify(eventPublisher, times(1)).publishEvent(any(NotificationRequestedEvent.class));
		}

		@Test
		@DisplayName("Test that a null idempotency key is rejected by validation before any work begins")
		void nullIdempotencyKey_RequestIsRejectedByValidation() {
			String userId = "ed567ghftry6789jwehnhjskdhrgjfk";
			NotificationRequest request = createNotificationRequest(null, userId, ORDER_CONFIRMATION);
			
			assertThatThrownBy(() -> service.send(request))
					.hasMessageContaining("idempotencyKey must not be blank")
					.isExactlyInstanceOf(ConstraintViolationException.class);
			
			verify(eventPublisher, never()).publishEvent(any());
		}

		@Test
		@DisplayName("Test that a whitespace-only idempotency key is rejected by validation before any work begins")
		void whitespaceOnlyIdempotencyKey_RequestIsRejectedByValidation() {
			String idempotencyKey = " ";
			String userId = "ed567ghftry6789jwehnhjskdhrgjfk";
			NotificationRequest request = createNotificationRequest(idempotencyKey, userId, ORDER_CONFIRMATION);
			
			assertThatThrownBy(() -> service.send(request))
					.hasMessageContaining("idempotencyKey must not be blank")
					.isExactlyInstanceOf(ConstraintViolationException.class);
			
			verify(eventPublisher, never()).publishEvent(any());
		}

		@Test
		@DisplayName("Test that multiple validation violations are reported together in a single constraint violation exception")
		void multipleValidationViolations_AreReportedTogether() {
			String idempotencyKey = " ";
			String userId = "ed567ghftry6789jwehnhjskdhrgjfk";
			NotificationRequest request = createNotificationRequest(idempotencyKey, userId, null);
			
			assertThatThrownBy(() -> service.send(request))
					.hasMessageContaining("type must not be null")
					.hasMessageContaining("idempotencyKey must not be blank")
					.isExactlyInstanceOf(ConstraintViolationException.class);
			
			verify(eventPublisher, never()).publishEvent(any());
		}

		@Test
		@DisplayName("Test that validation fails before any deduplication state is touched")
		void invalidRequest_IsRejectedBeforeDeduplicationRuns() {
			DeduplicationStore dedupStoreMock = mock(DeduplicationStore.class);
			NotificationService localService = new NotificationServiceImplementation(clock, validator, eventPublisher, dedupStoreMock);
			
			String idempotencyKey = " ";
			String userId = "ed567ghftry6789jwehnhjskdhrgjfk";
			NotificationRequest request = createNotificationRequest(idempotencyKey, userId, null);
			
			assertThatThrownBy(() -> localService.send(request))
					.hasMessageContaining("type must not be null")
					.hasMessageContaining("idempotencyKey must not be blank")
					.isExactlyInstanceOf(ConstraintViolationException.class);
			
			verify(dedupStoreMock, never()).markSeen(idempotencyKey);
		}
		
		private static @NonNull NotificationRequest createNotificationRequest(String idempotencyKey, String userId, NotificationType type) {
			Map<String, String> templateContext = Map.of(
					"name", "Aisha Bello",
					"orderNumber", "ORD-20260725-1842",
					"productName", "Wireless Noise-Cancelling Headphones",
					"deliveryDate", "2026-07-28",
					"supportEmail", "support@beacon.example"
			);
			Clock clock = Clock.fixed(Instant.parse("2026-07-25T15:00:00Z"), Clock.systemUTC().getZone());
			
			return new NotificationRequest(idempotencyKey, userId, type, HIGH, List.of(EMAIL, SMS, PUSH),
					templateContext, clock.instant());
		}
	}
	
	@Nested
	@DisplayName("Upstream deduplication")
	class DeduplicationTests {
		
		@Test
		@DisplayName("Given a new idempotency key, send succeeds, marks key as seen, and publishes one event")
		void newIdempotencyKey_sendSucceeds_marksSeen_andPublishesEvent() {
			String idempotencyKey = "991e8400e29b41d4a716446655442211";
			String userID = UUID.randomUUID().toString();
			NotificationRequest request = createNotificationRequest(idempotencyKey, userID);
			
			NotificationResponse response = service.send(request);
			assertNotNull(response);
			assertEquals(idempotencyKey, response.idempotencyKey());
			assertTrue(deduplicationStore.isSeen(idempotencyKey), "Expected key to be marked as seen after successful send");
			verify(eventPublisher, times(1)).publishEvent(any(NotificationRequestedEvent.class));
		}

		@Test
		@DisplayName("Given an already-seen idempotency key, second send is rejected and no second event is published")
		void duplicateIdempotencyKey_secondSendRejected_noAdditionalPublish() {
			String idempotencyKey = "991e8400e29b41d4a716446655442211";
			String userID = UUID.randomUUID().toString();
			NotificationRequest request = createNotificationRequest(idempotencyKey, userID);
			
			service.send(request);
			assertThrowsExactly(DuplicateNotificationException.class, ()->service.send(request), "Expected DuplicateNotificationException for duplicate key");
			verify(eventPublisher, times(1)).publishEvent(any(NotificationRequestedEvent.class));
		}
		
		@Test
		@DisplayName("Given concurrent requests sharing one idempotency key, exactly one send succeeds and only one event is published")
		void concurrentRequests_sameIdempotencyKey_onlyOneSucceeds() throws Exception {
			String idempotencyKey = "991e8400e29b41d4a716446655442211";
			String userID = UUID.randomUUID().toString();
			NotificationRequest request = createNotificationRequest(idempotencyKey, userID);
			
			int threads = 20;
			try(ExecutorService executor = Executors.newFixedThreadPool(threads)) {
				CountDownLatch readyLatch = new CountDownLatch(threads);
				CountDownLatch startLatch = new CountDownLatch(1);
				CountDownLatch doneLatch = new CountDownLatch(threads);
				
				AtomicInteger successCount = new AtomicInteger(0);
				AtomicInteger duplicateCount = new AtomicInteger(0);
				AtomicInteger otherFailedCount = new AtomicInteger(0);
				
				for (int i = 0; i < threads; i++) {
					executor.submit(() -> {
						try {
							readyLatch.countDown();
							startLatch.await();
							
							try {
								service.send(request);
								successCount.incrementAndGet();
							} catch (DuplicateNotificationException e) {
								duplicateCount.incrementAndGet();
							} catch (Exception e) {
								otherFailedCount.incrementAndGet();
							}
						}
						catch (Throwable e) {
								throw new RuntimeException(e);
						}finally {
							doneLatch.countDown();
						}
					});
				}
				
				readyLatch.await();
				startLatch.countDown();
				doneLatch.await();
				executor.shutdown();
				
				assertEquals(1, successCount.get(), "Expected exactly one successful send");
				assertEquals(threads-1, duplicateCount.get(), "Expected N-1 duplicate sends");
				assertEquals(0, otherFailedCount.get(), "No unexpected failures should occur");
				verify(eventPublisher, times(1)).publishEvent(any(NotificationRequestedEvent.class));
			}
		}

		@Test
		@DisplayName("Test that two different idempotency keys are handled independently and both requests can succeed")
		void differentIdempotencyKeys_areHandledIndependently() {
			DeduplicationStore dedupStoreMock = mock(DeduplicationStore.class);
			NotificationService localService = new NotificationServiceImplementation(clock, validator, eventPublisher, dedupStoreMock);
			
			String idempotencyKey1 = "991e8400e29b41d4a716446655442211";
			String idempotencyKey2 = "550e8400e29b41d4a716446655440000";
			String userId = "f1234tryuolfhyrieohfjed90943e";
			
			NotificationRequest request1 = createNotificationRequest(idempotencyKey1, userId);
			NotificationRequest request2 = createNotificationRequest(idempotencyKey2, userId);
			
			assertDoesNotThrow(() -> localService.send(request1));
			assertDoesNotThrow(() -> localService.send(request2));
			
			verify(dedupStoreMock, times(2)).markSeen(any(String.class));
			verify(eventPublisher, times(2)).publishEvent(any(NotificationRequestedEvent.class));
		}

		@Test
		@DisplayName("Test that a failure while publishing the event after marking the key as seen leaves the key reserved")
		void eventPublishingFailure_AfterMarkingKeySeenLeavesKeyReserved() {
			DeduplicationStore dedupStoreMock = mock(DeduplicationStore.class);
			ApplicationEventPublisher failingPublisher = mock(ApplicationEventPublisher.class);
			NotificationService localService = new NotificationServiceImplementation(clock, validator, failingPublisher, dedupStoreMock);
			
			String idempotencyKey = "550e8400e29b41d4a716446655440000";
			String userId = "f1234tryuolfhyrieohfjed90943e";
			NotificationRequest request = createNotificationRequest(idempotencyKey, userId);
			
			doThrow(new NotificationException("Publisher failure")).when(failingPublisher).publishEvent(any(NotificationRequestedEvent.class));

			assertThrows(NotificationException.class, () -> localService.send(request));

			verify(dedupStoreMock, times(1)).markSeen(idempotencyKey);
			verify(failingPublisher, times(1)).publishEvent(any(NotificationRequestedEvent.class));
		}

		@Test
		@DisplayName("Test that when validation fails, the idempotency key is not marked as seen")
		void validationFailure_DoesNotMarkIdempotencyKeyAsSeen() {
			String idempotencyKey = "550e8400e29b41d4a716446655440000";
			String userId = "";
			NotificationRequest request = createNotificationRequest(idempotencyKey, userId);
			
			assertThatThrownBy(() -> service.send(request));
			assertFalse(deduplicationStore.isSeen(idempotencyKey), "Expected key to not be marked as seen after failed validation");
		}
		
		private static @NonNull NotificationRequest createNotificationRequest(String idempotencyKey, String userId) {
			Map<String, String> templateContext = Map.of(
					"firstName", "Aisha",
					"offerTitle", "Early Bird Summer Sale: 25% OFF",
					"discountCode", "SUMMER25",
					"expiryDate", "July 31, 2026",
					"ctaUrl", "https://beacon.example/summer-sale",
					"unsubscribeUrl", "https://beacon.example/unsubscribe/aisha-b"
			);
			Clock clock = Clock.fixed(Instant.parse("2026-07-25T15:00:00Z"), Clock.systemUTC().getZone());
			
			return new NotificationRequest(idempotencyKey, userId, PROMO, LOW, List.of(EMAIL, SMS, PUSH),
					templateContext, clock.instant());
			
		}
	}
	
	@Nested
	@DisplayName("Acceptance response")
	class AcceptanceResponseTests {
		
		@Test
		@DisplayName("Test that send returns an acceptance acknowledgement with a generated notification id and CREATED status")
		void send_returnsAcceptanceAcknowledgement_withGeneratedNotificationIdAndCreatedStatus() {
			String idempotencyKey = "550e8400e29b41d4a716446655440000";
			String userId = "f1234tryuolfhyrieohfjed90943e";
			
			NotificationResponse response = service.send(createNotificationRequest(idempotencyKey, userId));
			assertTrue(response.notificationId() != null && !response.notificationId().isBlank(), "Expected non-null, non-blank notification ID");
			assertEquals(NotificationStatus.CREATED, response.status());
		}
		
		@Test
		@DisplayName("Test that the acknowledgement response echoes the original idempotency key")
		void acknowledgementResponse_EchoesOriginalIdempotencyKey() {
			String idempotencyKey = "550e8400e29b41d4a716446655440000";
			String userId = "f1234tryuolfhyrieohfjed90943e";
			
			NotificationResponse response = service.send(createNotificationRequest(idempotencyKey, userId));
			assertEquals(idempotencyKey, response.idempotencyKey());
		}
		
		@Test
		@DisplayName("Test that the acknowledgement response uses the injected clock for the accepted timestamp")
		void acknowledgementResponse_UsesInjectedClockForAcceptedAt() {
			String idempotencyKey = "550e8400e29b41d4a716446655440000";
			String userId = "f1234tryuolfhyrieohfjed90943e";
			
			NotificationResponse response = service.send(createNotificationRequest(idempotencyKey, userId));
			assertEquals(Instant.parse("2026-07-25T15:00:00Z"), response.acceptedAt(),
				"Expected acceptedAt to match the injected fixed clock");
		}
		
		@Test
		@DisplayName("Test that the same request produces the same notification id")
		void sameRequest_ProducesSameNotificationId() {
			String idempotencyKey = "550e8400e29b41d4a716446655440000";
			String userId = "f1234tryuolfhyrieohfjed90943e";
			
			NotificationResponse response1 = service.send(createNotificationRequest(idempotencyKey, userId));
			
			DeduplicationStore freshDedupStore = new DefaultDeduplicationStore(clock);
			ApplicationEventPublisher freshPublisher = mock(ApplicationEventPublisher.class);
			NotificationService freshService = new NotificationServiceImplementation(clock, validator, freshPublisher, freshDedupStore);
			
			NotificationResponse response2 = freshService.send(createNotificationRequest(idempotencyKey, userId));
			
			assertEquals(response1.notificationId(), response2.notificationId(),
				"Expected same idempotency key + same instant to produce the same notification ID (deterministic)");
		}
		
		@Test
		@DisplayName("Test that different requests produce different notification ids")
		void differentRequests_ProduceDifferentNotificationIds() {
			String idempotencyKey1 = "550e8400e29b41d4a716446655440000";
			String idempotencyKey2 = "991e8400e29b41d4a716446655442211";
			String userId = "f1234tryuolfhyrieohfjed90943e";
			
			NotificationResponse response1 = service.send(createNotificationRequest(idempotencyKey1, userId));
			NotificationResponse response2 = service.send(createNotificationRequest(idempotencyKey2, userId));
			
			assertNotEquals(response1.notificationId(), response2.notificationId(),
				"Expected different idempotency keys to produce different notification IDs");
		}

		private NotificationRequest createNotificationRequest(String idempotencyKey, String userId) {
			Map<String, String> templateContext = Map.of(
					"name", "Aisha Bello",
					"otp", "890324",
					"helpPageLink", "https://beacon.example/login-help"
			);
			Clock clock = Clock.fixed(Instant.parse("2026-07-25T15:00:00Z"), Clock.systemUTC().getZone());
			
			return new NotificationRequest(idempotencyKey, userId, OTP, HIGH, List.of(EMAIL, SMS, PUSH),
					templateContext, clock.instant());
		}
	}
}