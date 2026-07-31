package com.gonet.primary.system.login.controller;

import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.util.JsonUtils;
import com.gonet.common.util.QrCodeGenerator;
import com.gonet.logging.security.dto.SecurityEvent;
import com.gonet.logging.security.dto.SecurityEventType;
import com.gonet.logging.security.service.SecurityLogger;
import com.gonet.primary.admin.dto.Admin;
import com.gonet.primary.admin.service.AdminService;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import com.gonet.primary.system.login.service.TotpService;
import com.gonet.primary.system.login.service.TwoFactorSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 2FA 관문 + 셀프 등록 — 관리자 전용 기능. (클래스명은 호환성 유지)
 *
 * <p>엔드포인트:
 * <ul>
 *   <li>GET  /admin/login/2fa — 코드 입력 폼 (관리자 로그인 성공 후 강제 진입)</li>
 *   <li>POST /admin/login/2fa — 코드 검증 → 세션 PASSED + /admin/dashboard 리다이렉트</li>
 *   <li>GET  /admin/me/2fa/setup — secret 생성 + QR/otpauth URL 표시</li>
 *   <li>POST /admin/me/2fa/setup — 코드 검증 → secret 저장 + 2FA 활성</li>
 *   <li>GET  /admin/me/2fa/help  — Authenticator 앱 설치 가이드 (비기술자 대응)</li>
 * </ul>
 */
@Controller
@RequestMapping
public class TwoFactorUsrController {

    private static final Logger log = LoggerFactory.getLogger(TwoFactorUsrController.class);

    private final TotpService    totp;
    private final AdminService   adminService;
    private final AuditLogger    auditLogger;
    private final SecurityLogger securityLogger;

    public TwoFactorUsrController(TotpService totp,
                                   AdminService adminService,
                                   AuditLogger auditLogger,
                                   SecurityLogger securityLogger) {
        this.totp = totp;
        this.adminService = adminService;
        this.auditLogger = auditLogger;
        this.securityLogger = securityLogger;
    }

    // ------------------------------------------------------------------
    // /admin/login/2fa — 관문
    // ------------------------------------------------------------------

    @GetMapping("/admin/login/2fa")
    public String gateForm(HttpServletRequest req, Model model) {
        CustomUserDetails ud = principalOrNull();
        if (ud == null) return "redirect:/admin/login";
        model.addAttribute("loginId", ud.getUsername());
        return "admin/login-2fa";
    }

    @PostMapping("/admin/login/2fa")
    public String gateVerify(@RequestParam("code") String rawCode,
                              HttpServletRequest req,
                              HttpServletResponse res,
                              Model model) {
        CustomUserDetails ud = principalOrNull();
        if (ud == null) return "redirect:/admin/login";

        Admin admin = adminService.get(ud.getUserId());
        if (admin == null || admin.getTwoFactorSecret() == null) {
            log.warn("2FA gate: admin missing secret loginId={}", ud.getUsername());
            model.addAttribute("error", "2FA 설정 상태가 올바르지 않습니다. 관리자에게 문의하세요.");
            model.addAttribute("loginId", ud.getUsername());
            return "admin/login-2fa";
        }

        int code = totp.parseCode(rawCode);
        boolean ok = code > 0 && totp.verify(admin.getTwoFactorSecret(), code);

        auditLogger.write(AuditEvent.of("LOGIN_2FA", "tb_admin")
            .withActor(ud.getUserId(), ud.getUserType(), ud.getUsername())
            .withHttp(req.getMethod(), req.getRequestURI(),
                      req.getRemoteAddr(), req.getHeader("User-Agent"))
            .withTarget(ud.getUserId())
            .withResult(ok ? "SUCCESS" : "FAIL"));

        if (!ok) {
            log.info("===LOGIN_2FA_FAIL loginId={}", ud.getUsername());
            // §0.48 후속 — TWO_FACTOR_FAIL 적재 (severity=WARN)
            try {
                securityLogger.write(SecurityEvent.of(SecurityEventType.TWO_FACTOR_FAIL)
                    .withActor(ud.getUserId())
                    .withDetail("{\"loginId\":" + JsonUtils.quote(ud.getUsername())
                        + ",\"codeLen\":" + (rawCode == null ? 0 : rawCode.length()) + "}"));
            } catch (Exception ignored) {
                // fallback logger 가 흡수 — 응답 흐름은 그대로
            }
            model.addAttribute("error", "인증 코드가 올바르지 않습니다.");
            model.addAttribute("loginId", ud.getUsername());
            return "admin/login-2fa";
        }

        TwoFactorSession.markPassed(req);
        log.info("===LOGIN_2FA_OK loginId={}", ud.getUsername());
        return "redirect:/admin/dashboard";
    }

    // ------------------------------------------------------------------
    // /admin/me/2fa/help — Authenticator 앱 설치 가이드 (비기술자 대응)
    // ------------------------------------------------------------------

    @GetMapping("/admin/me/2fa/help")
    public String helpPage(Model model) {
        CustomUserDetails ud = principalOrNull();
        if (ud == null) return "redirect:/admin/login";
        model.addAttribute("loginId", ud.getUsername());
        return "admin/me/2fa-help";
    }

    // ------------------------------------------------------------------
    // /admin/me/2fa/setup — 셀프 등록
    // ------------------------------------------------------------------

    @GetMapping("/admin/me/2fa/setup")
    public String setupForm(HttpServletRequest req, Model model) {
        CustomUserDetails ud = principalOrNull();
        if (ud == null) return "redirect:/admin/login";

        String secret = totp.generateSecret();
        Object existing = req.getSession(true).getAttribute(TwoFactorSession.ATTR_SETUP_SECRET);
        if (existing instanceof String s && !s.isBlank()) {
            secret = s;
        } else {
            req.getSession(true).setAttribute(TwoFactorSession.ATTR_SETUP_SECRET, secret);
        }

        String otpauth = totp.otpAuthUrl(secret, ud.getUsername());
        model.addAttribute("secret", secret);
        model.addAttribute("otpauth", otpauth);
        model.addAttribute("qrPngDataUrl", QrCodeGenerator.toPngDataUrl(otpauth, 240));
        model.addAttribute("loginId", ud.getUsername());
        return "admin/me/2fa-setup";
    }

    @PostMapping("/admin/me/2fa/setup")
    public String setupConfirm(@RequestParam("code") String rawCode,
                                HttpServletRequest req,
                                Model model) {
        CustomUserDetails ud = principalOrNull();
        if (ud == null) return "redirect:/admin/login";

        Object stored = req.getSession(true).getAttribute(TwoFactorSession.ATTR_SETUP_SECRET);
        if (!(stored instanceof String secret) || secret.isBlank()) {
            return "redirect:/admin/me/2fa/setup";
        }

        int code = totp.parseCode(rawCode);
        boolean ok = code > 0 && totp.verify(secret, code);

        if (!ok) {
            String otpauth = totp.otpAuthUrl(secret, ud.getUsername());
            model.addAttribute("secret", secret);
            model.addAttribute("otpauth", otpauth);
            model.addAttribute("qrPngDataUrl", QrCodeGenerator.toPngDataUrl(otpauth, 240));
            model.addAttribute("loginId", ud.getUsername());
            model.addAttribute("error", "인증 코드가 일치하지 않습니다. 시간 오차(30초)를 확인하세요.");
            return "admin/me/2fa-setup";
        }

        adminService.enableTwoFactor(ud.getUserId(), secret);
        req.getSession(false).removeAttribute(TwoFactorSession.ATTR_SETUP_SECRET);
        TwoFactorSession.markPassed(req);

        auditLogger.write(AuditEvent.of("2FA_ENABLE", "tb_admin")
            .withActor(ud.getUserId(), ud.getUserType(), ud.getUsername())
            .withHttp(req.getMethod(), req.getRequestURI(),
                      req.getRemoteAddr(), req.getHeader("User-Agent"))
            .withTarget(ud.getUserId())
            .withResult("SUCCESS"));

        log.info("===2FA_ENABLED loginId={}", ud.getUsername());
        model.addAttribute("message", "2FA 가 활성화되었습니다.");
        return "redirect:/admin/dashboard";
    }

    private static CustomUserDetails principalOrNull() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof CustomUserDetails ud)) return null;
        return ud;
    }
}
