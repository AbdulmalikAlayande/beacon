package io.github.abdulmalikalayande.beacon.api.exception;

public class InvalidWebhookSignatureException extends NotificationException{
	
	public InvalidWebhookSignatureException(String message) {
		super(message);
	}
}
