package com.gonet.common.crypto;

import java.util.Base64;

/**
 * PII 키(base64 32바이트) 검증 헬퍼.
 *
 * <p>키가 잘못됐을 때 <b>어느 프로퍼티가 왜 틀렸는지</b> 알려주기 위해 존재한다.
 * 그냥 {@code Base64.getDecoder().decode(raw)} 를 호출하면
 * {@code IllegalArgumentException: Illegal base64 character 5c} 처럼
 * 원인 프로퍼티도, 고치는 방법도 알 수 없는 예외가 나온다(2026-07-31 실측).
 *
 * <p>{@code RequiredPropertyValidator} 는 <b>미주입</b>을 잡고, 이 클래스는 <b>형식 오류</b>를 잡는다.
 */
final class PiiKeys {

    /** base64 표준 알파벳 + 패딩. */
    private static final String B64_ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";

    private static final int AES_256_BYTES = 32;

    private PiiKeys() {}

    /**
     * base64 문자열을 32바이트 키로 디코드한다.
     *
     * @param raw      프로퍼티 값
     * @param property 오류 메시지에 넣을 프로퍼티 경로 (예: gopcms.crypto.pii.master-key)
     * @param envVar   오류 메시지에 넣을 환경변수 이름 (예: PCMS_PII_MASTER_KEY)
     */
    static byte[] decode32(String raw, String property, String envVar) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException(msg(property, envVar, "값이 비어 있습니다"));
        }

        // 어떤 문자가 문제인지 먼저 짚어 준다 — base64 디코더의 "character 5c" 보다 훨씬 낫다.
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (B64_ALPHABET.indexOf(c) < 0) {
                throw new IllegalStateException(msg(property, envVar,
                    String.format("base64 가 아닌 문자 '%s'(U+%04X)가 %d번째에 있습니다."
                        + " 경로·따옴표·줄바꿈이 섞여 들어갔는지 확인하세요", displayable(c), (int) c, i + 1)));
            }
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(msg(property, envVar,
                "base64 디코딩에 실패했습니다 (" + e.getMessage() + ")"));
        }

        if (bytes.length != AES_256_BYTES) {
            throw new IllegalStateException(msg(property, envVar,
                String.format("디코딩 결과가 %d바이트입니다. AES-256 은 정확히 %d바이트여야 합니다",
                    bytes.length, AES_256_BYTES)));
        }
        return bytes;
    }

    private static String displayable(char c) {
        return (c == '\n') ? "\\n" : (c == '\r') ? "\\r" : (c == '\t') ? "\\t" : String.valueOf(c);
    }

    private static String msg(String property, String envVar, String detail) {
        return System.lineSeparator()
            + "============================================================" + System.lineSeparator()
            + " PII 키 형식 오류 — " + property + System.lineSeparator()
            + "============================================================" + System.lineSeparator()
            + "  문제 : " + detail + System.lineSeparator()
            + "  주입 : 환경변수 " + envVar + System.lineSeparator()
            + "  형식 : base64 32바이트 (디코딩 후 32바이트, 문자열은 보통 44자 '=' 끝)" + System.lineSeparator()
            + "  생성 : openssl rand -base64 32" + System.lineSeparator()
            + "         PowerShell:  [Convert]::ToBase64String((1..32|%{Get-Random -Max 256}))"
            + System.lineSeparator()
            + "  주의 : master-key 와 hmac-key 는 서로 다른 값을 쓴다(D11)" + System.lineSeparator()
            + "============================================================";
    }
}
