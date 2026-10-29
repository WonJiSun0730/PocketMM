package com.example.projectmm.infrastructure.security;

import com.example.projectmm.domain.memory.ProtectedMemoryDetail;
import jakarta.persistence.PrePersist;
import org.springframework.beans.factory.annotation.Value;

/** Hibernate obtains this listener from Spring, including in repository-only integration tests. */
public class ProtectedDetailFingerprintListener {
    private final StoredTextCipher cipher;
    public ProtectedDetailFingerprintListener(
            @Value("${POCKETMM_DATA_ENCRYPTION_KEY:}") String key,
            @Value("${pocketmm.encryption.required:true}") boolean required) {
        cipher = new StoredTextCipher(key, required);
    }
    @PrePersist public void fingerprint(ProtectedMemoryDetail detail) {
        detail.assignDetailFingerprint(cipher.fingerprint(detail.getDetailText()));
    }
}
