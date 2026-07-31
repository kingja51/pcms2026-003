package com.gonet.common.crypto;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 기반 PII 암호화 유틸.
 *
 * <p>저장 포맷: {@code {AG}<base64(iv(12) || ciphertext || tag(16))>}
 * <p>legacy plaintext(평문) 는 {@link #decryptIfEncrypted(String)} 에서 그대로 반환.
 *
 * <p>MyBatis Interceptor 에서 호출하여 DB I/O 경계에서 자동 암복호화.
 */
@Component
@EnableConfigurationProperties(PiiCryptoProperties.class)
public class AesGcmCipher {

    public static final String PREFIX = "{AG}";
    private static final int IV_BYTES  = 12;
    private static final int TAG_BITS  = 128;

    private final SecureRandom rng = new SecureRandom();
    private final SecretKeySpec key;

    public AesGcmCipher(PiiCryptoProperties props) {
        String raw = props.getMasterKey();
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException(
                "gopcms.crypto.pii.master-key 가 설정되지 않았습니다. "
              + "환경변수 PCMS_PII_MASTER_KEY 또는 Vault 경유로 32바이트(base64) 키를 주입하세요.");
        }
        byte[] keyBytes = Base64.getDecoder().decode(raw);
        if (keyBytes.length != 32) {
            throw new IllegalStateException("PII master key 는 32바이트(AES-256) 이어야 합니다.");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_BYTES];
            rng.nextBytes(iv);

            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buf = ByteBuffer.allocate(iv.length + ct.length);
            buf.put(iv).put(ct);
            return PREFIX + Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            throw new IllegalStateException("PII encrypt failed", e);
        }
    }

    /**
     * {AG} 프리픽스가 있으면 복호화, 없으면 legacy plaintext 로 그대로 반환.
     */
    public String decryptIfEncrypted(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return stored;
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv  = new byte[IV_BYTES];
            byte[] ct  = new byte[all.length - IV_BYTES];
            System.arraycopy(all, 0, iv, 0, IV_BYTES);
            System.arraycopy(all, IV_BYTES, ct, 0, ct.length);

            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("PII decrypt failed", e);
        }
    }

    public boolean isEncrypted(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }
}
