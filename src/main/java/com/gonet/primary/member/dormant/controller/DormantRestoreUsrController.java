package com.gonet.primary.member.dormant.controller;

import com.gonet.common.crypto.TokenHasher;
import com.gonet.primary.identity.controller.NiceCheckUsrController;
import com.gonet.primary.identity.dto.NiceCheckResult;
import com.gonet.primary.member.dormant.service.DormantRestoreService;
import com.gonet.primary.member.dto.Member;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 휴면 해제 — <b>실명인증 / 이메일 OTP 택1</b> (개발가이드 §10-6).
 *
 * <p>001 은 로그인ID + 이름 + 이메일 + 비밀번호 <b>3요소 일치</b> 한 화면이었다.
 * 003 은 그 방식을 쓰지 않는다 — 휴면 계정의 비밀번호는 사용자가 가장 잊기 쉬운
 * 정보라, 정작 본인이 못 푸는 화면이 된다.
 *
 * <h2>화면 흐름</h2>
 * <pre>
 *   GET  /member/dormant/restore            수단 선택
 *
 *   [A] 실명인증
 *   POST /member/dormant/restore/identity   → /member/identity/nice 로 위임(세션에 loginId 보관)
 *   GET  /member/dormant/restore/identity/done  ← NICE 콜백 후 복귀. DI 해시 대조 → 해제
 *
 *   [B] 이메일 OTP
 *   POST /member/dormant/restore/otp/request  코드 발송(항상 같은 화면)
 *   GET  /member/dormant/restore/otp          코드 입력
 *   POST /member/dormant/restore/otp          검증 → 해제
 * </pre>
 *
 * <p><b>이 컨트롤러는 인증 없이 접근된다</b> — 휴면 계정은 로그인할 수 없으므로
 * 인증을 요구하면 해제 자체가 불가능하다. 대신 위 두 수단이 본인을 확인한다.
 *
 * <p>열거 차단 계약상 발송 요청은 <b>성공·실패를 구분해 보여주지 않는다.</b>
 * 서비스가 예외를 던지지 않으므로 컨트롤러는 분기할 것도 없다 —
 * 이 무조건성이 계약의 핵심이라 {@code try/catch} 로 분기를 되살리지 말 것.
 */
@Controller
@RequestMapping("/member/dormant/restore")
public class DormantRestoreUsrController {

    private static final Logger log = LoggerFactory.getLogger(DormantRestoreUsrController.class);

    /** 실명인증 왕복 중 로그인ID 보관 — NICE 콜백에는 우리 파라미터가 실리지 않는다. */
    private static final String SESSION_RESTORE_LOGIN_ID = "DORMANT_RESTORE_LOGIN_ID";

    private final DormantRestoreService service;
    private final TokenHasher           tokenHasher;

    public DormantRestoreUsrController(DormantRestoreService service, TokenHasher tokenHasher) {
        this.service     = service;
        this.tokenHasher = tokenHasher;
    }

    // ── 수단 선택 ───────────────────────────────────────────────────────

    @GetMapping
    public String chooseMethod() {
        return "front/dormant-restore";
    }

    // ── [A] 실명인증 ────────────────────────────────────────────────────

    /**
     * 실명인증 시작 — 로그인ID 를 세션에 두고 NICE 표준 창으로 넘긴다.
     * 인증 후 {@code NEXT_URL} 로 돌아온다.
     */
    @PostMapping("/identity")
    public String startIdentity(@RequestParam String loginId, HttpSession session) {
        session.setAttribute(SESSION_RESTORE_LOGIN_ID, loginId);
        session.setAttribute(NiceCheckUsrController.SESSION_NEXT_URL, "/member/dormant/restore/identity/done");
        return "redirect:/member/identity/nice";
    }

    /**
     * 실명인증 복귀 — 세션의 NICE 결과에서 DI 를 꺼내 해시하고 스냅샷과 대조한다.
     *
     * <p>DI 원문을 그대로 조회 조건에 쓰지 않는다. DB 에는 {@code di_hash} 만 있고,
     * 원문을 파라미터로 흘리면 로그·APM 에 개인 식별값이 남는다.
     */
    @GetMapping("/identity/done")
    public String finishIdentity(HttpSession session, Model model, RedirectAttributes ra) {
        String loginId = (String) session.getAttribute(SESSION_RESTORE_LOGIN_ID);
        Object raw     = session.getAttribute(NiceCheckUsrController.SESSION_RESULT);

        if (loginId == null || !(raw instanceof NiceCheckResult result) || !result.isSuccess()) {
            ra.addFlashAttribute("errorMessage", "본인인증이 완료되지 않았습니다. 처음부터 다시 시도해 주세요.");
            return "redirect:/member/dormant/restore";
        }
        // 인증 결과는 1회용으로 소비한다 — 남겨 두면 뒤로가기로 재사용된다
        session.removeAttribute(NiceCheckUsrController.SESSION_RESULT);
        session.removeAttribute(SESSION_RESTORE_LOGIN_ID);

        try {
            Member restored = service.restoreByIdentity(loginId, tokenHasher.hash(result.getDi()));
            return doneRedirect(ra, restored);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/member/dormant/restore";
        }
    }

    // ── [B] 이메일 OTP ──────────────────────────────────────────────────

    /**
     * 인증번호 발송 요청.
     *
     * <p><b>결과를 분기하지 않는다.</b> 계정이 없어도, 이메일이 달라도, 쿨다운에
     * 걸려도 같은 화면·같은 문구다. 여기서 "가입되지 않은 아이디입니다" 를 띄우는
     * 순간 이 화면은 계정 열거 도구가 된다.
     */
    @PostMapping("/otp/request")
    public String requestOtp(@RequestParam String loginId,
                             @RequestParam String email,
                             HttpServletRequest req,
                             RedirectAttributes ra) {
        service.requestOtp(loginId, email, clientIp(req));

        ra.addFlashAttribute("infoMessage",
            "입력하신 정보와 일치하는 계정이 있다면 인증번호를 메일로 보냈습니다. 5분 안에 입력해 주세요.");
        ra.addFlashAttribute("loginId", loginId);
        ra.addFlashAttribute("email",   email);
        return "redirect:/member/dormant/restore/otp";
    }

    /** 인증번호 입력 화면. */
    @GetMapping("/otp")
    public String otpForm(Model model) {
        // loginId·email 은 flash 로 넘어와 이미 모델에 있다. 없으면 사용자가 직접 채운다.
        return "front/dormant-restore-otp";
    }

    /** 인증번호 검증 → 해제. */
    @PostMapping("/otp")
    public String verifyOtp(@RequestParam String loginId,
                            @RequestParam String email,
                            @RequestParam String code,
                            RedirectAttributes ra) {
        try {
            Member restored = service.restoreByOtp(loginId, email, code);
            return doneRedirect(ra, restored);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            ra.addFlashAttribute("loginId", loginId);
            ra.addFlashAttribute("email",   email);
            return "redirect:/member/dormant/restore/otp";
        }
    }

    // ------------------------------------------------------------------

    /** 두 수단의 성공 처리는 동일하다 — 로그인 화면으로 보내고 안내만 남긴다. */
    private String doneRedirect(RedirectAttributes ra, Member restored) {
        log.info("DORMANT_RESTORE_DONE memberId={}", restored.getMemberId());
        ra.addFlashAttribute("infoMessage", "휴면 상태가 해제되었습니다. 다시 로그인해 주세요.");
        return "redirect:/member/login";
    }

    /** 프록시 뒤에서도 원 IP 를 남긴다. 신뢰 프록시 판정은 필터가 이미 했다. */
    private static String clientIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
