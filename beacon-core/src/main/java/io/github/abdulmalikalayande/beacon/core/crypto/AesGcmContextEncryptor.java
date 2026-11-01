package io.github.abdulmalikalayande.beacon.core.crypto;

import io.github.abdulmalikalayande.beacon.api.exception.NotificationException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

public final class AesGcmContextEncryptor implements ContextEncryptor {

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int KEY_LENGTH_BYTES = 32;
	private static final int IV_LENGTH_BYTES = 12;
	private static final int TAG_LENGTH_BITS = 128;
	private static final TypeReference<Map<String, String>> CONTEXT_TYPE = new TypeReference<>() {};

	private final SecretKeySpec key;
	private final SecureRandom secureRandom = new SecureRandom();
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	public AesGcmContextEncryptor(byte[] keyBytes) {
		if (keyBytes == null || keyBytes.length != KEY_LENGTH_BYTES) {
			throw new IllegalArgumentException("Encryption key must be exactly 32 bytes (AES-256)");
		}
		this.key = new SecretKeySpec(keyBytes.clone(), "AES");
	}

	public static AesGcmContextEncryptor fromBase64Key(String base64Key) {
		if (base64Key == null || base64Key.isBlank()) {
			throw new IllegalArgumentException("Encryption key must not be blank");
		}
		return new AesGcmContextEncryptor(Base64.getDecoder().decode(base64Key));
	}

	@Override
	public String encrypt(Map<String, String> context) {
		if (context == null) {
			return null;
		}
		try {
			byte[] iv = new byte[IV_LENGTH_BYTES];
			secureRandom.nextBytes(iv);

			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
			byte[] ciphertext = cipher.doFinal(jsonMapper.writeValueAsBytes(context));

			byte[] ivAndCiphertext = ByteBuffer.allocate(iv.length + ciphertext.length)
					.put(iv)
					.put(ciphertext)
					.array();
			return Base64.getEncoder().encodeToString(ivAndCiphertext);
		} catch (GeneralSecurityException e) {
			throw new NotificationException("Failed to encrypt notification context", e);
		}
	}

	@Override
	public Map<String, String> decrypt(String encryptedContext) {
		if (encryptedContext == null) {
			return Map.of();
		}
		try {
			byte[] ivAndCiphertext = Base64.getDecoder().decode(encryptedContext);
			if (ivAndCiphertext.length <= IV_LENGTH_BYTES) {
				throw new NotificationException("Encrypted context is too short to be valid");
			}

			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, key,
					new GCMParameterSpec(TAG_LENGTH_BITS, ivAndCiphertext, 0, IV_LENGTH_BYTES));
			byte[] plaintext = cipher.doFinal(ivAndCiphertext, IV_LENGTH_BYTES, ivAndCiphertext.length - IV_LENGTH_BYTES);

			return jsonMapper.readValue(plaintext, CONTEXT_TYPE);
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			throw new NotificationException("Failed to decrypt notification context", e);
		}
	}
}