package io.github.abdulmalikalayande.beacon.core.crypto;

import io.github.abdulmalikalayande.beacon.api.exception.NotificationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AesGcmContextEncryptor")
class AesGcmContextEncryptorTest {

    private static final byte[] KEY = new byte[32];
    private static final byte[] OTHER_KEY = new byte[32];
    static {
        for (int i = 0; i < 32; i++) {
            KEY[i] = (byte) i;
            OTHER_KEY[i] = (byte) (31 - i);
        }
    }

    private final ContextEncryptor encryptor = new AesGcmContextEncryptor(KEY);
    private final Map<String, String> context = Map.of("name", "Ada", "otp", "547320");

    @Test
    @DisplayName("what goes in comes back out unchanged")
    void roundTrip_returnsOriginalContext() {
        assertEquals(context, encryptor.decrypt(encryptor.encrypt(context)));
    }

    @Test
    @DisplayName("the encrypted text contains none of the original values")
    void encrypted_containsNoPlainValues() {
        String encrypted = encryptor.encrypt(context);
        assertFalse(encrypted.contains("547320"));
        assertFalse(encrypted.contains("Ada"));
        assertFalse(new String(Base64.getDecoder().decode(encrypted)).contains("547320"));
    }

    @Test
    @DisplayName("encrypting the same context twice gives different text (random IV)")
    void sameInputTwice_differentOutput() {
        assertNotEquals(encryptor.encrypt(context), encryptor.encrypt(context));
    }

    @Test
    @DisplayName("a changed byte is detected and rejected")
    void tampered_rejected() {
        byte[] bytes = Base64.getDecoder().decode(encryptor.encrypt(context));
        bytes[bytes.length - 1] ^= 1;
        String tampered = Base64.getEncoder().encodeToString(bytes);
        assertThrows(NotificationException.class, () -> encryptor.decrypt(tampered));
    }

    @Test
    @DisplayName("a different key cannot decrypt")
    void wrongKey_rejected() {
        String encrypted = encryptor.encrypt(context);
        assertThrows(NotificationException.class, () -> new AesGcmContextEncryptor(OTHER_KEY).decrypt(encrypted));
    }

    @Test
    @DisplayName("text that is not valid encrypted context is rejected")
    void garbage_rejected() {
        assertThrows(NotificationException.class, () -> encryptor.decrypt("not-base64-$$$"));
        assertThrows(NotificationException.class, () -> encryptor.decrypt(Base64.getEncoder().encodeToString(new byte[5])));
    }

    @Test
    @DisplayName("null context encrypts to null, and null decrypts to an empty map")
    void nulls_handled() {
        assertNull(encryptor.encrypt(null));
        assertEquals(Map.of(), encryptor.decrypt(null));
    }

    @Test
    @DisplayName("a key that is not 32 bytes is rejected at construction")
    void wrongKeyLength_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new AesGcmContextEncryptor(new byte[16]));
        assertThrows(IllegalArgumentException.class, () -> new AesGcmContextEncryptor(null));
    }
}