package io.github.abdulmalikalayande.beacon.core.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Encryption utility for testing. Uses AES encryption with a fixed test key.
 *
 * <p><strong>Warning:</strong> This is for testing only. Do NOT use this in production.
 * The key is hardcoded and not secure. Use a proper key management system in production.
 */
public final class TestEncryptionUtil {

	/**
	 * A fixed 256-bit (32-byte) key used for all test encryption.
	 * This ensures encryption is deterministic in tests.
	 *
	 * <p>In production, keys should be:
	 * <ul>
	 *   <li>Generated securely (not hardcoded)
	 *   <li>Stored in a key management service (AWS KMS, Vault, etc.)
	 *   <li>Rotated regularly
	 *   <li>Never committed to version control
	 * </ul>
	 */
	private static final byte[] TEST_KEY = new byte[]{
			0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
			0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
			0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
			0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F
	};

	private static final String ALGORITHM = "AES";

	private TestEncryptionUtil() {
		// Utility class, no instantiation
	}

	/**
	 * Encrypts plaintext using AES and returns the Base64-encoded result.
	 *
	 * @param plaintext the data to encrypt
	 * @return Base64-encoded ciphertext
	 * @throws IllegalArgumentException if encryption fails
	 */
	public static String encrypt(String plaintext) {
		if (plaintext == null) {
			throw new IllegalArgumentException("plaintext must not be null");
		}

		try {
			SecretKeySpec key = new SecretKeySpec(TEST_KEY, 0, TEST_KEY.length, ALGORITHM);
			Cipher cipher = Cipher.getInstance(ALGORITHM);
			cipher.init(Cipher.ENCRYPT_MODE, key);

			byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
			byte[] ciphertextBytes = cipher.doFinal(plaintextBytes);

			return Base64.getEncoder().encodeToString(ciphertextBytes);
		} catch (Exception e) {
			throw new IllegalArgumentException("Failed to encrypt data", e);
		}
	}

	/**
	 * Decrypts Base64-encoded ciphertext using AES and returns the plaintext.
	 *
	 * @param base64Ciphertext the Base64-encoded ciphertext
	 * @return the decrypted plaintext
	 * @throws IllegalArgumentException if decryption fails
	 */
	public static String decrypt(String base64Ciphertext) {
		if (base64Ciphertext == null) {
			throw new IllegalArgumentException("base64Ciphertext must not be null");
		}

		try {
			SecretKeySpec key = new SecretKeySpec(TEST_KEY, 0, TEST_KEY.length, ALGORITHM);
			Cipher cipher = Cipher.getInstance(ALGORITHM);
			cipher.init(Cipher.DECRYPT_MODE, key);

			byte[] ciphertextBytes = Base64.getDecoder().decode(base64Ciphertext);
			byte[] plaintextBytes = cipher.doFinal(ciphertextBytes);

			return new String(plaintextBytes, StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new IllegalArgumentException("Failed to decrypt data", e);
		}
	}
}


