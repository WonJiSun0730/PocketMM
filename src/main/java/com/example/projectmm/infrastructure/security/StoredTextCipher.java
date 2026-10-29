package com.example.projectmm.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** Server-side encryption at rest; authenticated users still receive plaintext over HTTPS. */
@Component
public final class StoredTextCipher {
    private static final String PREFIX = "enc:v2:";
    private static final String LEGACY = "enc:v1:";
    private static final byte[] AAD = "PocketMM:stored-text:enc:v2".getBytes(StandardCharsets.UTF_8);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    public StoredTextCipher(
            @Value("${POCKETMM_DATA_ENCRYPTION_KEY:}") String configured,
            @Value("${pocketmm.encryption.required:true}") boolean required) {
        if (configured == null || configured.isBlank()) {
            if (required) throw new IllegalStateException("POCKETMM_DATA_ENCRYPTION_KEY is required");
            key = null; // explicit development/test opt-out only
            return;
        }
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(configured); }
        catch (IllegalArgumentException ex) { throw new IllegalStateException("Encryption key must be Base64"); }
        if (decoded.length != 32) throw new IllegalStateException("Encryption key must decode to 32 bytes");
        key = new SecretKeySpec(decoded, "AES");
        Arrays.fill(decoded, (byte) 0);
    }

    public String encrypt(String value) {
        if (value == null) return null;
        if (value.startsWith("enc:")) throw new IllegalArgumentException("Reserved encrypted text prefix");
        if (key == null) return value;
        try {
            byte[] nonce = new byte[12];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(AAD);
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] payload = Arrays.copyOf(nonce, nonce.length + ciphertext.length);
            System.arraycopy(ciphertext, 0, payload, nonce.length, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException ex) { throw new IllegalStateException("Stored text encryption failed"); }
    }

    /** Keyed equality index; a separate derived key prevents offline plaintext guessing from the DB. */
    public String fingerprint(String value) {
        if (key == null) return TextFingerprint.sha256(value); // explicitly opted-out test/development DB
        try {
            var mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getEncoded(), "HmacSHA256"));
            byte[] derived = mac.doFinal("PocketMM:detail-index:v1".getBytes(StandardCharsets.UTF_8));
            mac.init(new SecretKeySpec(derived, "HmacSHA256"));
            Arrays.fill(derived, (byte) 0);
            return java.util.HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) { throw new IllegalStateException("Stored text fingerprint failed"); }
    }

    public String decrypt(String value) {
        if (value == null || !value.startsWith("enc:")) return value; // controlled rolling migration
        boolean v2 = value.startsWith(PREFIX);
        if (!v2 && !value.startsWith(LEGACY)) throw new IllegalStateException("Unsupported encrypted text version");
        if (key == null) throw new IllegalStateException("Encrypted text requires a configured key");
        try {
            byte[] payload = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            if (payload.length < 28) throw new IllegalArgumentException(); // nonce + tag, including empty text
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, payload, 0, 12));
            if (v2) cipher.updateAAD(AAD);
            return new String(cipher.doFinal(payload, 12, payload.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Stored text authentication failed");
        }
    }
}
