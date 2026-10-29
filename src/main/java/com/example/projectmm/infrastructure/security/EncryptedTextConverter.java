package com.example.projectmm.infrastructure.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Encrypted text at rest. Existing unprefixed rows remain readable for a separately
 * controlled migration. Do not enable on production before migrating legacy rows.
 */
@Converter
public class EncryptedTextConverter implements AttributeConverter<String, String> {
    private static final String PREFIX = "enc:v1:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final byte[] KEY = loadKey();

    private static byte[] loadKey() {
        String configured = System.getenv("POCKETMM_DATA_ENCRYPTION_KEY");
        if (configured == null || configured.isBlank()) {
            if (Boolean.parseBoolean(System.getenv("POCKETMM_ENCRYPTION_REQUIRED")))
                throw new IllegalStateException("POCKETMM_DATA_ENCRYPTION_KEY is required");
            return null;
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(configured);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("POCKETMM_DATA_ENCRYPTION_KEY must be Base64", ex);
        }
        if (key.length != 32) throw new IllegalStateException("POCKETMM_DATA_ENCRYPTION_KEY must decode to 32 bytes");
        return key;
    }

    @Override
    public String convertToDatabaseColumn(String value) {
        if (value == null) return null;
        if (KEY == null) return value; // development/QA compatibility; production must configure key
        if (value.startsWith(PREFIX)) throw new IllegalArgumentException("Reserved encrypted text prefix");
        try {
            byte[] nonce = new byte[12];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY, "AES"), new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] combined = Arrays.copyOf(nonce, nonce.length + encrypted.length);
            System.arraycopy(encrypted, 0, combined, nonce.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encrypt stored text", ex);
        }
    }

    @Override
    public String convertToEntityAttribute(String value) {
        if (value == null || !value.startsWith(PREFIX)) return value; // legacy plaintext
        if (KEY == null) throw new IllegalStateException("Encrypted text requires POCKETMM_DATA_ENCRYPTION_KEY");
        try {
            byte[] combined = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            if (combined.length < 29) throw new IllegalArgumentException("Invalid encrypted payload");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY, "AES"),
                    new GCMParameterSpec(128, combined, 0, 12));
            return new String(cipher.doFinal(combined, 12, combined.length - 12), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to decrypt stored text; verify encryption key and data integrity", ex);
        }
    }
}
