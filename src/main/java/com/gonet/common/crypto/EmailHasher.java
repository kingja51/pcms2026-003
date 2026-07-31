package com.gonet.common.crypto;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/**
 * 이메일(또는 기타 완전일치 검색 대상 PII) 의 HMAC-SHA256 해시 산출.
 *
 * <p>DB 저장 전 소문자 정규화 후 HMAC. 결과는 16진수 64자.
 *
 * <p><b>salt 를 쓰지 않는다.</b> 완전일치 검색({@code WHERE email_hash = ?})과 중복확인이 목적이라
 * 같은 입력이 항상 같은 해시를 내야 한다 — 의도된 설계다.
 *
 * <p><b>키는 {@code hmac-key} 다</b>(D11, 2026-07-31). 001 은 {@code master-key}(AES) 를
 * 재사용했으나 003 은 key separation 원칙에 따라 분리한다.
 * 회전 시에는 {@code master-key} 로 평문을 복원해 재해시해야 하므로
 * {@link PiiCryptoProperties} 주석의 절차를 따른다.
 */
@Component
public class EmailHasher {

    private static final String ALGO = "HmacSHA256";
    private final SecretKeySpec key;

    public EmailHasher(PiiCryptoProperties props) {
        String raw = props.getHmacKey();
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("gopcms.crypto.pii.hmac-key 가 필요합니다.");
        }
        byte[] keyBytes = Base64.getDecoder().decode(raw);
        this.key = new SecretKeySpec(keyBytes, ALGO);
    }

    public String hash(String email) {
        if (email == null || email.isBlank()) return null;
        try {
            Mac mac = Mac.getInstance(ALGO);
            mac.init(key);
            byte[] out = mac.doFinal(email.trim().toLowerCase(Locale.ROOT)
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("email hash failed", e);
        }
    }
}
