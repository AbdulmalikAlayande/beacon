package io.github.abdulmalikalayande.beacon.core.crypto;

import java.util.Map;

/** Encrypts a notification's template context before it is queued, and decrypts it for the worker. */
public interface ContextEncryptor {

	/** @return the encrypted form, or {@code null} when the context is {@code null} */
	String encrypt(Map<String, String> context);

	/** @return the original context, or an empty map when {@code encryptedContext} is {@code null} */
	Map<String, String> decrypt(String encryptedContext);
}