package com.example.projectmm.infrastructure.security;

import org.junit.jupiter.api.Test;
import java.util.Base64;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.assertj.core.api.Assertions.*;

class StoredTextCipherTest {
    static String key() { byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return Base64.getEncoder().encodeToString(bytes); }
    @Test void roundTripUsesFreshNonceAndAuthenticatesUnicodeEmptyAndLargeValues() {
        var cipher=new StoredTextCipher(key(),true);
        for(String value:new String[]{"","안녕 😀","x".repeat(100000)}) {
            String first=cipher.encrypt(value),second=cipher.encrypt(value);
            assertThat(first).startsWith("enc:v2:").isNotEqualTo(second);
            assertThat(cipher.decrypt(first)).isEqualTo(value);
        }
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt("legacy 평문")).isEqualTo("legacy 평문");
    }
    @Test void wrongKeyTamperingTruncationAndUnknownVersionsFailClosed() {
        var cipher=new StoredTextCipher(key(),true);
        String value=cipher.encrypt("private");
        assertThatThrownBy(()->new StoredTextCipher(key(),true).decrypt(value)).isInstanceOf(IllegalStateException.class);
        byte[] payload=Base64.getDecoder().decode(value.substring(7));payload[13]^=1;
        assertThatThrownBy(()->cipher.decrypt("enc:v2:"+Base64.getEncoder().encodeToString(payload))).isInstanceOf(IllegalStateException.class);
        for(String bad:new String[]{"enc:v3:abc","enc:v2:%%","enc:v2:AA==","enc:v1:"})
            assertThatThrownBy(()->cipher.decrypt(bad)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->cipher.encrypt(value)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void keyValidationAndExplicitDevelopmentOptOut() {
        assertThatThrownBy(()->new StoredTextCipher("",true)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->new StoredTextCipher("bad!",false)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->new StoredTextCipher(Base64.getEncoder().encodeToString(new byte[16]),true)).isInstanceOf(IllegalStateException.class);
        var development=new StoredTextCipher("",false);
        assertThat(development.encrypt("QA")).isEqualTo("QA");
        assertThatThrownBy(()->development.decrypt(new StoredTextCipher(key(),true).encrypt("private"))).isInstanceOf(IllegalStateException.class);
    }
    @Test void equalityFingerprintIsStableKeyedAndDistinctFromPlaintextDigest() {
        var cipher = new StoredTextCipher(key(), true);
        assertThat(cipher.fingerprint("private detail")).hasSize(64)
                .isEqualTo(cipher.fingerprint("private detail"))
                .isNotEqualTo(TextFingerprint.sha256("private detail"))
                .isNotEqualTo(new StoredTextCipher(key(), true).fingerprint("private detail"));
    }
    @Test void readsFirstCommitV1PayloadIncludingEmptyText() throws Exception {
        String key=key();var service=new StoredTextCipher(key,true);
        for(String value:new String[]{"","기존 대화"}) {
            byte[] nonce=new byte[12];new SecureRandom().nextBytes(nonce);
            var cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(Base64.getDecoder().decode(key),"AES"),new GCMParameterSpec(128,nonce));
            byte[] encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] payload=Arrays.copyOf(nonce,12+encrypted.length);System.arraycopy(encrypted,0,payload,12,encrypted.length);
            assertThat(service.decrypt("enc:v1:"+Base64.getEncoder().encodeToString(payload))).isEqualTo(value);
        }
    }
}
