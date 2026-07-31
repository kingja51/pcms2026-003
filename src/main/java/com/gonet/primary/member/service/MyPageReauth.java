package com.gonet.primary.member.service;

import jakarta.servlet.http.HttpSession;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 회원 마이페이지 민감 페이지 진입 전 재인증(step-up) 세션 토큰 헬퍼.
 *
 * <p>대상: {@code /member/mypage/profile} 개인정보 수정 · {@code /member/mypage/withdraw} 탈퇴.
 * 로그인만으로는 부족 — 분실된 세션 탈취(XSS·CSRF 우회 등) 시에도 치명 행위를 2차 검증하기 위함.
 *
 * <p>플로우:
 * <ol>
 *   <li>사용자가 민감 페이지 GET → {@link #isValid(HttpSession)} 체크 → 만료 시 {@code /member/mypage/verify} 로 리다이렉트</li>
 *   <li>verify 폼에서 이름·비밀번호 일치 검증 성공 → {@link #mark(HttpSession)} 호출</li>
 *   <li>{@link #TTL} 동안 재인증 유효 → 민감 페이지 자유 왕래</li>
 *   <li>만료 또는 실패 카운트 초과 시 자동 무효화 → 재입력 강제</li>
 * </ol>
 *
 * <p>{@link #FAIL_LIMIT} 회 실패 시 {@link #clearAll(HttpSession)} 으로 흔적 제거 — 로그인
 * 자체는 유지해 일반 페이지 이용 가능. 민감 페이지는 재시도 필요.
 */
public final class MyPageReauth {

    /** 재인증 승인 시각(세션 속성). */
    public static final String SESS_AT          = "PCMS_MEMBER_REAUTH_AT";
    /** 재인증 실패 카운트(세션 속성). */
    public static final String SESS_FAIL_COUNT  = "PCMS_MEMBER_REAUTH_FAIL";

    /** 재인증 유효 기간. 너무 길면 보안 저하, 너무 짧으면 UX 저하 — 기본 5분. */
    public static final Duration TTL         = Duration.ofMinutes(5);
    /** 세션당 재인증 실패 허용 상한. 초과 시 재시도 차단 + 로그인으로 돌려보내기 권장. */
    public static final int      FAIL_LIMIT  = 5;

    private MyPageReauth() {}

    /** 현재 세션이 TTL 내 유효 재인증 상태인지. */
    public static boolean isValid(HttpSession session) {
        if (session == null) return false;
        Object at = session.getAttribute(SESS_AT);
        if (!(at instanceof LocalDateTime t)) return false;
        return t.plus(TTL).isAfter(LocalDateTime.now());
    }

    /** 재인증 성공 마킹 — 실패 카운트도 함께 리셋. */
    public static void mark(HttpSession session) {
        if (session == null) return;
        session.setAttribute(SESS_AT, LocalDateTime.now());
        session.removeAttribute(SESS_FAIL_COUNT);
    }

    /** 실패 카운트 +1. FAIL_LIMIT 도달 시 재인증 흔적 전부 제거하고 true 반환. */
    public static boolean incrementFail(HttpSession session) {
        if (session == null) return false;
        int n = 0;
        Object cur = session.getAttribute(SESS_FAIL_COUNT);
        if (cur instanceof Integer i) n = i;
        n += 1;
        session.setAttribute(SESS_FAIL_COUNT, n);
        if (n >= FAIL_LIMIT) {
            clearAll(session);
            return true;
        }
        return false;
    }

    /** 세션에 기록된 실패 카운트. 없으면 0. */
    public static int failCount(HttpSession session) {
        if (session == null) return 0;
        Object cur = session.getAttribute(SESS_FAIL_COUNT);
        return cur instanceof Integer i ? i : 0;
    }

    /** 재인증 관련 모든 세션 속성 제거 — 로그아웃·성공 후 즉시 파기 등에 사용. */
    public static void clearAll(HttpSession session) {
        if (session == null) return;
        session.removeAttribute(SESS_AT);
        session.removeAttribute(SESS_FAIL_COUNT);
    }

    /** 남은 유효 시간(초) — UI 카운트다운용. 없거나 만료 시 0. */
    public static long remainingSeconds(HttpSession session) {
        if (session == null) return 0L;
        Object at = session.getAttribute(SESS_AT);
        if (!(at instanceof LocalDateTime t)) return 0L;
        long sec = Duration.between(LocalDateTime.now(), t.plus(TTL)).toSeconds();
        return Math.max(0L, sec);
    }
}
