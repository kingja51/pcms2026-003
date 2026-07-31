package com.gonet.member;

import com.gonet.common.crypto.PiiCryptoProperties;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 테스트 전용 PII 키.
 *
 * <p>base64 디코드 시 <b>정확히 32바이트</b>여야 한다 — 이걸 틀리면
 * {@code IllegalStateException} 이 나고 원인이 "키 길이" 라는 게 잘 안 보인다
 * (실제로 한 번 겪었다: 33자 문자열을 넣어 실패).
 *
 * <p>운영 키와 무관하며 저장소에 커밋돼도 되는 값이다.
 * master 와 hmac 을 다르게 두는 것은 실제 정책(D11)과 같은 형태를 유지하기 위해서다.
 */
final class TestKeys {

    private static final String MASTER = b64("aes-key-0123456789abcdef01234567");   // 32 bytes
    private static final String HMAC   = b64("hmac-key-0123456789abcdef0123456");   // 32 bytes

    private TestKeys() {}

    static PiiCryptoProperties piiProps() {
        PiiCryptoProperties p = new PiiCryptoProperties();
        p.setMasterKey(MASTER);
        p.setHmacKey(HMAC);
        return p;
    }

    private static String b64(String raw) {
        byte[] bytes = raw.getBytes(StandardCharsets.UTF_8);
        if (bytes.length != 32) {
            throw new IllegalStateException("테스트 키는 32바이트여야 한다: " + bytes.length);
        }
        return Base64.getEncoder().encodeToString(bytes);
    }
}
