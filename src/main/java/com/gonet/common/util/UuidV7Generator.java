package com.gonet.common.util;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * UUID v7 생성기
 * - 시간 기반 정렬 보장 (밀리초 단위 타임스탬프)
 * - 48-bit timestamp + 4-bit version(7) + 12-bit random + 2-bit variant + 62-bit random
 *
 * <p>도메인 prefix 지원 (2026-05-01 신설):
 * <ul>
 *   <li>{@link #generate()}                — 36자 순수 UUID (레거시 호환)</li>
 *   <li>{@link #generate(String)}          — {@code "PFX_<36자UUID>"} = 40자.
 *       prefix 는 반드시 대문자 3자 (A-Z 또는 0-9). 2자 도메인은 {@code "0"} 패딩으로
 *       3자 강제 (예: file_group → "FG0", code_group → "CG0").</li>
 * </ul>
 *
 * <p>모든 도메인 ID 컬럼은 DB {@code VARCHAR(40)}. 신규 PK 발급 시 도메인 Service 가
 * 본인의 prefix 를 명시 — Generator 가 검증·합성. 기존 36자 데이터는 prefix 없는
 * 형태로 영구 보존하며 새 PK 만 prefix 적용 (백워드 호환).
 */
public final class UuidV7Generator {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** prefix 형식: 대문자 A-Z 또는 숫자 0-9 정확히 3자. */
    private static final Pattern PREFIX_PATTERN = Pattern.compile("^[A-Z0-9]{3}$");

    private UuidV7Generator() {
        // 유틸리티 클래스 인스턴스화 방지
    }

    /**
     * UUID v7 문자열 생성 (prefix 없음, 레거시 호환)
     * @return UUID v7 문자열 (36자, 하이픈 포함)
     */
    public static String generate() {
        return toUuid(Instant.now().toEpochMilli()).toString();
    }

    /**
     * 도메인 prefix 가 결합된 UUID v7 문자열 생성.
     * <pre>
     *   POP_019d9b80-0b79-78c8-af0d-47a764a8878d  (총 40자)
     *   └─┬─┘└────────────── 36자 UUID v7 ─────────────┘
     *    3자 prefix
     * </pre>
     *
     * @param prefix 대문자 3자 또는 숫자 패딩 (예: "POP", "BAN", "FG0", "CG0")
     * @return {@code "<PREFIX>_<UUID>"} (40자)
     * @throws IllegalArgumentException prefix 가 null / 길이 != 3 / 대문자·숫자 외 문자 포함
     */
    public static String generate(String prefix) {
        if (prefix == null || !PREFIX_PATTERN.matcher(prefix).matches()) {
            throw new IllegalArgumentException(
                "prefix must be exactly 3 chars of [A-Z0-9]: " + prefix);
        }
        return prefix + "_" + generate();
    }

    /**
     * UUID v7 객체 생성
     * @return UUID v7 객체
     */
    public static UUID generateUuid() {
        return toUuid(Instant.now().toEpochMilli());
    }

    /**
     * 타임스탬프 기반 UUID v7 생성
     */
    private static UUID toUuid(long timestamp) {
        // 48-bit timestamp (상위 48비트)
        long msb = (timestamp & 0xFFFF_FFFF_FFFFL) << 16;

        // version 7 (4비트: 0111)
        msb |= 0x7000L;

        // 12-bit random (하위 12비트)
        msb |= (RANDOM.nextLong() & 0xFFFL);

        // LSB: variant (10xx) + 62-bit random
        long lsb = RANDOM.nextLong();
        lsb = (lsb & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;

        return new UUID(msb, lsb);
    }
}
