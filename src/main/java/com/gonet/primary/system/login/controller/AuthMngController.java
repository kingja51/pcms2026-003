package com.gonet.primary.system.login.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 관리자 로그인 폼 — {@code /admin/login}.
 *
 * <p>접미사 규약: {@code MngController}. POST 처리는 Spring Security 의
 * UsernamePasswordAuthenticationFilter (loginProcessingUrl=/admin/login)
 * 가 담당하고 본 컨트롤러는 GET 폼만 렌더링.
 *
 * <p>실패 카운터·잠금 플래그는 {@link AbstractLoginFailureHandler} 가 세션에 기록하며,
 * 본 컨트롤러가 1회 소비 후 즉시 삭제한다 — URL 노출을 피해 user enumeration 을 차단.
 */
@Controller
public class AuthMngController {

    @GetMapping("/admin/login")
    public String loginPage(
            @RequestParam(value = "error",   required = false) String error,
            @RequestParam(value = "logout",  required = false) String logout,
            @RequestParam(value = "expired", required = false) String expired,
            HttpSession session,
            Model model) {
        if (error != null)   model.addAttribute("errorCode", error);
        if (logout != null)  model.addAttribute("loggedOut", true);
        if (expired != null) model.addAttribute("sessionExpired", true);
        LoginFeedback.consume(session, model);
        return "admin/login";
    }
}
