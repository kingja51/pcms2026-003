package com.gonet.primary.system.login.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 세션에 2FA 관문 통과 상태를 저장/조회하는 유틸.
 *
 * <p>로그인 성공 시 사용자가 2FA 활성 상태라면 {@code PASSED=false} 로 초기화되고,
 * {@code /login/2fa} 에서 코드 검증 성공 시 {@code PASSED=true} 로 전환.
 * <p>관리자 영역({@code /admin/**}) 접근 시 {@link com.gonet.config.interceptor.TwoFactorEnforcementInterceptor} 가 이 플래그 확인.
 */
public final class TwoFactorSession {

    public  static final String ATTR_PASSED  = "gopcms.2fa.passed";
    public  static final String ATTR_SETUP_SECRET = "gopcms.2fa.setupSecret";

    private TwoFactorSession() {}

    public static void markPending(HttpServletRequest req) {
        req.getSession(true).setAttribute(ATTR_PASSED, Boolean.FALSE);
    }

    public static void markPassed(HttpServletRequest req) {
        req.getSession(true).setAttribute(ATTR_PASSED, Boolean.TRUE);
    }

    public static boolean isPassed(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        if (s == null) return true; // 세션 없으면 비로그인 — 이 경로는 인증 단계에서 이미 처리됨
        Object v = s.getAttribute(ATTR_PASSED);
        return !(v instanceof Boolean b) || b;
    }

    public static void clear(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        if (s == null) return;
        s.removeAttribute(ATTR_PASSED);
        s.removeAttribute(ATTR_SETUP_SECRET);
    }
}
