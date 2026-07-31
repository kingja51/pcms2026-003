package com.gonet.primary.system.login.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;

/**
 * 로그인 실패 상세(카운터·잠금) 를 세션에서 꺼내 모델에 바인딩하고 즉시 삭제하는 1회성 소비기.
 *
 * <p>{@link AbstractLoginFailureHandler} 가 실패 직후 세션에 기록한 값을, 다음 GET /login
 * 요청에서 폼이 렌더링될 때 본 유틸이 꺼내 제거한다. URL 노출이 없어 user enumeration 이
 * 차단된다.
 */
final class LoginFeedback {

    private LoginFeedback() {}

    /** 세션의 실패 상세를 {@link Model} 에 복사한 뒤 세션에서 삭제. */
    static void consume(HttpSession session, Model model) {
        Object failCount   = session.getAttribute(AbstractLoginFailureHandler.SESS_FAIL_COUNT);
        Object locked      = session.getAttribute(AbstractLoginFailureHandler.SESS_LOCKED);
        Object lockMinutes = session.getAttribute(AbstractLoginFailureHandler.SESS_LOCK_MINUTES);

        if (failCount instanceof Integer n && n > 0) {
            model.addAttribute("failCount", n);
        }
        if (Boolean.TRUE.equals(locked)) {
            model.addAttribute("accountLocked", true);
        }
        if (lockMinutes instanceof Integer m && m > 0) {
            model.addAttribute("lockMinutes", m);
        }

        session.removeAttribute(AbstractLoginFailureHandler.SESS_FAIL_COUNT);
        session.removeAttribute(AbstractLoginFailureHandler.SESS_LOCKED);
        session.removeAttribute(AbstractLoginFailureHandler.SESS_LOCK_MINUTES);
    }
}
