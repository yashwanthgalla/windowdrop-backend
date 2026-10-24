package com.example.dropcode_backend;

import com.example.dropcode_backend.util.CryptoUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CryptoAndCodeServiceTest {

    @Test
    @DisplayName("Sharing code is exactly 8 characters and contains only un-ambiguous charset")
    void testGenerateShareCode() {
        for (int i = 0; i < 50; i++) {
            String code = CryptoUtils.generateShareCode(8);
            assertEquals(8, code.length());
            assertTrue(code.matches("^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}$"), "Code must not have 0, O, 1, I, L: " + code);
        }
    }

    @Test
    @DisplayName("HMAC-SHA256 produces deterministic 64-hex char hash")
    void testHmacSha256() {
        String data = "TESTCODE";
        String pepper = "secure-random-pepper-key-minimum-32-chars";

        String hash1 = CryptoUtils.hmacSha256(data, pepper);
        String hash2 = CryptoUtils.hmacSha256(data, pepper);

        assertEquals(64, hash1.length());
        assertEquals(hash1, hash2);

        String hash3 = CryptoUtils.hmacSha256("DIFFERENT", pepper);
        assertNotEquals(hash1, hash3);
    }

    @Test
    @DisplayName("AES-256-GCM correctly encrypts and decrypts payload")
    void testAesGcmEncryptionDecryption() {
        byte[] key = CryptoUtils.deriveKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", "test_item_context");
        String originalMessage = "DropCode Ephemeral Transfer Secret Content - 2026";
        byte[] plaintext = originalMessage.getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = CryptoUtils.encryptAesGcm(plaintext, key);
        assertNotNull(encrypted);
        assertTrue(encrypted.length > plaintext.length); // Includes 12-byte IV + 16-byte GCM tag

        byte[] decrypted = CryptoUtils.decryptAesGcm(encrypted, key);
        assertArrayEquals(plaintext, decrypted);
        assertEquals(originalMessage, new String(decrypted, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("AES-256-GCM fails decryption with wrong key")
    void testAesGcmWrongKeyFails() {
        byte[] key1 = CryptoUtils.deriveKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", "context_1");
        byte[] key2 = CryptoUtils.deriveKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", "context_2");

        byte[] plaintext = "Sensitive data".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = CryptoUtils.encryptAesGcm(plaintext, key1);

        assertThrows(RuntimeException.class, () -> CryptoUtils.decryptAesGcm(encrypted, key2));
    }
}
