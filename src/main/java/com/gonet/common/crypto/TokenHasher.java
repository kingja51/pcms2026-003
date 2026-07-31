package com.gonet.common.crypto;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 되돌릴 필요 없는 <b>대조용</b> 토큰 해시 — HMAC-SHA256, 64자 소문자 hex.
 *
 * <p>용도: 인증번호(OTP), 일회용 링크 토큰처럼 "저장해 두고 나중에 같은 값인지만
 * 확인" 하면 되는 것들. 원문을 복원할 이유가 없으므로 암호화가 아니라 해시를 쓴다 —
 * DB 가 유출돼도 원문이 나오지 않아야 한다.
 *
 * <p>{@link EmailHasher} 와 같은 키({@code PCMS_PII_HMAC_KEY})를 쓴다. AES 마스터키와
 * <b>분리된 키</b>라는 점이 중요하다(D11) — 한 쪽이 털려도 다른 쪽이 버틴다.
 * EmailHasher 와 나눠 둔 이유는 정규화 규칙이 다르기 때문이다: 이메일은
 * trim + lowercase 를 해야 같은 주소가 같은 해시가 되지만, 토큰은 <b>있는 그대로</b>
 * 해시해야 한다(대소문자를 섞은 토큰이 뭉개지면 안 된다).
 *
 * <p>비교는 반드시 {@link #matches}를 쓴다. {@code String.equals} 는 첫 불일치에서
 * 빠져나와 일치한 접두 길이가 응답 시간에 새어 나간다.
 */
@Component
public class TokenHasher {

    private static final String ALGO = "HmacSHA256";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final SecretKeySpec key;

    public TokenHasher(PiiCryptoProperties props) {
        // 형식 오류 시 어느 프로퍼티가 왜 틀렸는지까지 알려 준다 — PiiKeys 주석 참조
        this.key = new SecretKeySpec(
            PiiKeys.decode32(props.getHmacKey(), "gopcms.crypto.pii.hmac-key", "PCMS_PII_HMAC_KEY"),
            ALGO);
    }

    /**
     * 원문을 정규화 없이 해시한다.
     *
     * @return 64자 소문자 hex. {@code raw} 가 null 이면 null
     * @throws IllegalStateException 해시 생성 실패(키 이상) — 조용히 통과시키지 않는다
     */
    public String hash(String raw) {
        if (raw == null) return null;
        try {
            Mac mac = Mac.getInstance(ALGO);
            mac.init(key);
            byte[] out = mac.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) {
                sb.append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("토큰 해시 생성 실패 — HMAC 키를 확인할 것", ex);
        }
    }

    /**
     * 저장된 해시와 원문을 <b>상수 시간</b>으로 대조한다.
     *
     * @param storedHash DB 에 있던 해시
     * @param raw        사용자가 입력한 원문
     */
    public boolean matches(String storedHash, String raw) {
        if (storedHash == null || raw == null) return false;
        String candidate = hash(raw);
        return MessageDigest.isEqual(
            storedHash.getBytes(StandardCharsets.UTF_8),
            candidate.getBytes(StandardCharsets.UTF_8));
    }
}
