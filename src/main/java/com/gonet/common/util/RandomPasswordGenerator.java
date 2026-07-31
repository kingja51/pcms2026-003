package com.gonet.common.util;

import java.security.SecureRandom;

/**
 * 초기화 비밀번호 생성기 — 암호학적으로 안전한 난수(SecureRandom) 기반.
 *
 * <p>기본 12자: 영대/영소/숫자/특수문자 각 최소 1자 포함 → 정책 통과 보장.
 * <p>관리자 비밀번호 초기화, 이메일 인증 임시 코드 등에 공용.
 */
public final class RandomPasswordGenerator {

    private static final char[] UPPER   = "ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final char[] LOWER   = "abcdefghijkmnopqrstuvwxyz".toCharArray();
    private static final char[] DIGITS  = "23456789".toCharArray();        // 0/1 제외
    private static final char[] SYMBOLS = "!@#$%^*+=?-".toCharArray();      // 인쇄 가능한 흔한 문자만
    private static final char[] ALL;
    static {
        ALL = new char[UPPER.length + LOWER.length + DIGITS.length + SYMBOLS.length];
        int i = 0;
        for (char c : UPPER)   ALL[i++] = c;
        for (char c : LOWER)   ALL[i++] = c;
        for (char c : DIGITS)  ALL[i++] = c;
        for (char c : SYMBOLS) ALL[i++] = c;
    }

    private static final SecureRandom RNG = new SecureRandom();

    private RandomPasswordGenerator() {}

    /** 기본 12자 비밀번호 생성 (영대/영소/숫자/특수 각 최소 1자). */
    public static String generate() {
        return generate(12);
    }

    /** {@code length} 자 비밀번호 (최소 8자, 최대 64자). */
    public static String generate(int length) {
        int n = Math.max(8, Math.min(length, 64));
        char[] out = new char[n];

        // 각 카테고리 최소 1자 배치
        out[0] = UPPER[RNG.nextInt(UPPER.length)];
        out[1] = LOWER[RNG.nextInt(LOWER.length)];
        out[2] = DIGITS[RNG.nextInt(DIGITS.length)];
        out[3] = SYMBOLS[RNG.nextInt(SYMBOLS.length)];
        for (int i = 4; i < n; i++) {
            out[i] = ALL[RNG.nextInt(ALL.length)];
        }
        // Fisher-Yates 셔플
        for (int i = n - 1; i > 0; i--) {
            int j = RNG.nextInt(i + 1);
            char t = out[i]; out[i] = out[j]; out[j] = t;
        }
        return new String(out);
    }
}
