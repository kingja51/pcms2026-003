package com.gonet.primary.system.login.service;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 관리자 2FA — TOTP (RFC 6238) 서비스.
 *
 * <p>{@code com.warrenstrange:googleauth} 라이브러리 기반.
 * 기본 파라미터 (SHA-1 / 30s 주기 / 6자리) 는 Google Authenticator / Microsoft Authenticator
 * / Authy 와 호환.
 *
 * <p>발급된 base32 secret 은 {@code tb_admin.two_factor_secret} 에
 * {AG} AES-GCM 자동 암호화 저장 — DB 평문 유출 시에도 단일 계정 복제 불가.
 */
@Service
public class TotpService {

    private static final String ISSUER = "GoPCMS";

    private final GoogleAuthenticator authenticator = new GoogleAuthenticator();

    /** 새 secret 생성 (base32 문자열) — 저장 전 별도 보관. */
    public String generateSecret() {
        GoogleAuthenticatorKey key = authenticator.createCredentials();
        return key.getKey();
    }

    /**
     * Authenticator 앱이 스캔할 {@code otpauth://} URL.
     * <pre>otpauth://totp/{issuer}:{account}?secret={base32}&issuer={issuer}</pre>
     */
    public String otpAuthUrl(String secret, String accountLabel) {
        String enc = URLEncoder.encode(accountLabel, StandardCharsets.UTF_8);
        return "otpauth://totp/" + ISSUER + ":" + enc
             + "?secret=" + secret
             + "&issuer=" + ISSUER
             + "&digits=6&period=30&algorithm=SHA1";
    }

    /** 사용자가 입력한 6자리 코드 검증 — ±1 step (=30초) 허용 기본값. */
    public boolean verify(String secret, int code) {
        if (secret == null || secret.isBlank()) return false;
        try {
            return authenticator.authorize(secret, code);
        } catch (Exception e) {
            return false;
        }
    }

    /** 문자열 코드를 int 로 파싱. 숫자 6자리 아니면 -1. */
    public int parseCode(String raw) {
        if (raw == null) return -1;
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() != 6) return -1;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
