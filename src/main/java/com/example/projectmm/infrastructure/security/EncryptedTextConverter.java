package com.example.projectmm.infrastructure.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

@Component
@Converter
public class EncryptedTextConverter implements AttributeConverter<String, String> {
    private final StoredTextCipher cipher;
    public EncryptedTextConverter(
            @org.springframework.beans.factory.annotation.Value("${POCKETMM_DATA_ENCRYPTION_KEY:}") String key,
            @org.springframework.beans.factory.annotation.Value("${pocketmm.encryption.required:true}") boolean required) {
        this.cipher = new StoredTextCipher(key, required);
    }
    @Override public String convertToDatabaseColumn(String value) { return cipher.encrypt(value); }
    @Override public String convertToEntityAttribute(String value) { return cipher.decrypt(value); }
}
