package io.github.abdulmalikalayande.beacon.api.enums;

/**
 * The delivery channels Beacon supports.
 *
 * <p>Each channel maps to one or more providers (e.g. {@code SMS} maps to
 * Twilio as primary and Termii as fallback). A single notification can be
 * delivered across multiple channels depending on the user's preferences
 * and what the notification type supports.
 */
public enum NotificationChannel {
	
	/** Mobile/web push notifications, delivered via providers like Firebase. */
	PUSH,
	
	/** Text messages, delivered via providers like Twilio or Termii. */
	SMS,
	
	/** Email, delivered via providers like SendGrid or Mailgun. */
	EMAIL,
//
//	/**
//	 * Supports all channels, EMAIL, PUSH, SMS
//	 */
//	ALL
}