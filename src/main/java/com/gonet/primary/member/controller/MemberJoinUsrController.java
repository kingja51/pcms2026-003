package com.gonet.primary.member.controller;

import com.gonet.common.captcha.CaptchaGuard;
import com.gonet.common.crypto.EmailHasher;
import com.gonet.common.util.IpUtils;
import com.gonet.primary.identity.controller.NiceCheckUsrController;
import com.gonet.primary.identity.dto.NiceCheckResult;
import com.gonet.primary.member.dto.JoinSessionData;
import com.gonet.primary.member.dto.Member;
import com.gonet.primary.member.dto.MemberJoinForm;
import com.gonet.primary.member.service.MemberService;
import com.gonet.primary.system.site.dto.SiteContext;
import com.gonet.primary.system.site.service.SiteContextResolver;
import com.gonet.primary.system.site.service.SiteContextService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.regex.Pattern;

/**
 * 회원 가입 — 7단계 플로우. 비로그인 접근.
 *
 * <pre>
 *  Step 1  GET  /member/join                           유형 선택 (일반/14세미만)
 *  Step 2  GET  /member/join/agree?userType=...        약관 동의
 *  Step 3  GET  /member/identity/nice?userType=...     NICE 안심본인인증(팝업)
 *  Step 4  GET  /member/join/form                      NICE 결과 흡수 + 가입 폼 prefill
 *  Step 5  GET  /member/join/form                      가입 폼 (세션값 prefill)
 *  Step 6  POST /member/join                           가입 실행 (세션 CI 소비)
 *  Step 7  GET  /member/join/complete?memberId=...     가입 완료 페이지
 * </pre>
 *
 * <p>세션 키 {@link JoinSessionData#SESS_KEY} — 단계 사이 값 보관. Step 6 완료 시 clear.
 */
@Controller
@RequestMapping("/member")
public class MemberJoinUsrController {

    private static final Logger log = LoggerFactory.getLogger(MemberJoinUsrController.class);

    /** 가입 폼과 동일 규칙: 소문자 시작, 소문자·숫자·_ 만, 8자 이상. */
    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{7,49}$");

    private final MemberService service;
    private final EmailHasher   emailHasher;
    private final SiteContextResolver siteContextResolver;
    private final CaptchaGuard captchaGuard;

    public MemberJoinUsrController(MemberService service, EmailHasher emailHasher,
                                    SiteContextResolver siteContextResolver,
                                    CaptchaGuard captchaGuard) {
        this.service = service;
        this.emailHasher = emailHasher;
        this.siteContextResolver = siteContextResolver;
        this.captchaGuard = captchaGuard;
    }


    @ModelAttribute
    public void injectSiteContext(HttpServletRequest req, Model model) {
        siteContextResolver.inject(req, model);
    }


    // ==================================================================
    // Step 1 — 유형 선택
    // ==================================================================

    @GetMapping("/join")
    public String step1TypeSelect(HttpSession session) {
        // 세션 초기화 — 이전 반쯤 진행된 플로우 정리
        session.removeAttribute(JoinSessionData.SESS_KEY);
        return "front/join-type-select";
    }

    // ==================================================================
    // Step 2 — 약관 동의
    // ==================================================================

    @GetMapping("/join/agree")
    public String step2Agree(@RequestParam(name = "userType", required = false) String userType,
                              HttpSession session, Model model) {
        String safe = normalizeUserType(userType);
        JoinSessionData s = getOrCreate(session);
        s.setUserType(safe);
        s.setAgreed(false);
        session.setAttribute(JoinSessionData.SESS_KEY, s);
        model.addAttribute("userType", safe);
        return "front/join-agree";
    }

    /**
     * 약관 동의 완료 표시. 필수 2종(이용약관/개인정보) 미동의 시 다시 요구.
     * 5종 동의값을 세션에 그대로 저장 → Step 4 폼은 hidden 으로 재주입, Step 6 submit
     * 시 서버가 폼 동의 필드를 세션값으로 덮어쓴다 (조작 방지).
     */
    @PostMapping("/join/agree")
    public String step2AgreeSubmit(@RequestParam(name = "userType") String userType,
                                     @RequestParam(name = "termsAgreeYn", required = false) String termsAgreeYn,
                                     @RequestParam(name = "privacyAgreeYn", required = false) String privacyAgreeYn,
                                     @RequestParam(name = "marketingAgreeYn", required = false, defaultValue = "N") String marketingAgreeYn,
                                     @RequestParam(name = "smsAgreeYn", required = false, defaultValue = "N") String smsAgreeYn,
                                     @RequestParam(name = "emailAgreeYn", required = false, defaultValue = "N") String emailAgreeYn,
                                     HttpSession session, RedirectAttributes ra) {
        String safe = normalizeUserType(userType);
        if (!"Y".equals(termsAgreeYn) || !"Y".equals(privacyAgreeYn)) {
            ra.addFlashAttribute("error", "필수 약관에 동의해 주세요.");
            return "redirect:/member/join/agree?userType=" + safe;
        }
        JoinSessionData s = getOrCreate(session);
        s.setUserType(safe);
        s.setAgreed(true);
        s.setTermsAgreeYn("Y");
        s.setPrivacyAgreeYn("Y");
        s.setMarketingAgreeYn("Y".equals(marketingAgreeYn) ? "Y" : "N");
        s.setSmsAgreeYn("Y".equals(smsAgreeYn) ? "Y" : "N");
        s.setEmailAgreeYn("Y".equals(emailAgreeYn) ? "Y" : "N");
        session.setAttribute(JoinSessionData.SESS_KEY, s);

        // NICE 안심본인인증 완료 후 부모창이 이동할 다음 단계 — 가입 폼.
        // 가입 폼 진입 시 NiceCheckResult 를 JoinSessionData 로 흡수한다.
        session.setAttribute(NiceCheckUsrController.SESSION_NEXT_URL, "/member/join/form");
        return "redirect:/member/identity/nice?userType=" + safe;
    }

    // ==================================================================
    // Step 3 — CI 본인인증 (NICE CheckPlus)
    //
    // 레거시 mock 라우트 (/member/join/ci, /member/join/ci/callback) 는 제거됨.
    // 약관 동의 POST 가 곧바로 /member/identity/nice 로 redirect 한다.
    //
    // 향후 다중 본인인증 제공자(서울신용평가/KCB 등) 추가 가이드:
    //   1) 새 Controller 를 com.gonet.primary.identity.<provider> 패키지에 추가
    //      (NiceCheckUsrController 와 동등한 launch/success/fail 3 엔드포인트)
    //   2) 가입 흐름에서 제공자 선택 화면을 두거나, redirect 대상만 분기
    //   3) 콜백 Controller 가 결과를 세션에 SESSION_RESULT 키로 저장 (공통 인터페이스 권장)
    //   4) absorbNiceResultIfPresent() 를 일반화하여 어떤 제공자든 흡수
    // ==================================================================

    // ==================================================================
    // Step 5 — 가입 폼 (prefill)
    // ==================================================================

    @GetMapping("/join/form")
    public String step5JoinForm(HttpSession session, Model model, RedirectAttributes ra) {
        JoinSessionData s = (JoinSessionData) session.getAttribute(JoinSessionData.SESS_KEY);
        log.info("===JOIN_STEP5_ENTRY sessionId={} hasJoinSess={} agreed={} diVerified={} verifiedDiLen={} verifiedName={}",
                session.getId(),
                s != null,
                s != null && s.isAgreed(),
                s != null && s.isDiVerified(),
                s == null || s.getVerifiedDi() == null ? 0 : s.getVerifiedDi().length(),
                s == null ? null : s.getVerifiedName());

        if (s == null || !s.isAgreed()) {
            ra.addFlashAttribute("error", "약관 동의를 먼저 진행해 주세요.");
            return "redirect:/member/join";
        }
        // NICE 본인인증 결과가 세션에 있으면 가입 세션 데이터로 1회 흡수
        absorbNiceResultIfPresent(session, s);

        log.info("===JOIN_STEP5_AFTER_ABSORB sessionId={} diVerified={} verifiedDiLen={} verifiedName={} verifiedPhone={}",
                session.getId(),
                s.isDiVerified(),
                s.getVerifiedDi() == null ? 0 : s.getVerifiedDi().length(),
                s.getVerifiedName(),
                s.getVerifiedPhone());

        if (!s.isDiVerified()) {
            ra.addFlashAttribute("error", "본인인증이 필요합니다. 처음부터 다시 시작해 주세요.");
            return "redirect:/member/join";
        }
        if (!model.containsAttribute("form")) {
            MemberJoinForm f = new MemberJoinForm();
            f.setUserType(s.getUserType());
            // Step 2 에서 받은 5종 동의값을 폼에 prefill — Step 4 화면은 hidden 으로 보존
            f.setTermsAgreeYn(s.getTermsAgreeYn());
            f.setPrivacyAgreeYn(s.getPrivacyAgreeYn());
            f.setMarketingAgreeYn(s.getMarketingAgreeYn());
            f.setSmsAgreeYn(s.getSmsAgreeYn());
            f.setEmailAgreeYn(s.getEmailAgreeYn());
            if (s.isAdult()) {
                // 일반 — 인증된 이름·휴대폰·생년월일을 본인 입력 기본값으로 세팅
                f.setMemberName(s.getVerifiedName());
                f.setPhone(s.getVerifiedPhone());
                f.setBirthDate(s.getVerifiedBirthday());
            } else {
                // 14세 미만 — 인증된 이름·휴대폰을 부모 정보로 prefill. 자녀 이름·생년월일은 사용자 직접 입력
                // (인증 birthDate 는 부모 본인의 것이므로 자녀 birthDate 에 자동 입력하지 않는다).
                f.setParentName(s.getVerifiedName());
                f.setPhone(s.getVerifiedPhone());
            }
            model.addAttribute("form", f);
        }
        model.addAttribute("userType", s.getUserType());
        model.addAttribute("joinSess", s);
        return "front/member-join";
    }

    // ==================================================================
    // Step 6 — 가입 실행
    // ==================================================================

    @PostMapping("/join")
    public String step6Submit(@Valid @ModelAttribute("form") MemberJoinForm form,
                                BindingResult br,
                                HttpServletRequest req,
                                HttpSession session,
                                Model model,
                                RedirectAttributes ra) {
        JoinSessionData s = (JoinSessionData) session.getAttribute(JoinSessionData.SESS_KEY);
        log.info("===JOIN_STEP6_ENTRY sessionId={} hasJoinSess={} diVerified={} verifiedDiLen={} verifiedDiHashLen={} verifiedName={} verifiedPhone={} userType={}",
                session.getId(),
                s != null,
                s != null && s.isDiVerified(),
                s == null || s.getVerifiedDi() == null ? 0 : s.getVerifiedDi().length(),
                s == null || s.getVerifiedDiHash() == null ? 0 : s.getVerifiedDiHash().length(),
                s == null ? null : s.getVerifiedName(),
                s == null ? null : s.getVerifiedPhone(),
                s == null ? null : s.getUserType());

        if (s == null || !s.isDiVerified()) {
            ra.addFlashAttribute("error", "본인인증이 필요합니다. 처음부터 다시 시작해 주세요.");
            return "redirect:/member/join";
        }
        // userType 과 5종 동의값은 세션 값 신뢰 (폼 hidden 은 조작 가능)
        form.setUserType(s.getUserType());
        form.setTermsAgreeYn(s.getTermsAgreeYn());
        form.setPrivacyAgreeYn(s.getPrivacyAgreeYn());
        form.setMarketingAgreeYn(s.getMarketingAgreeYn());
        form.setSmsAgreeYn(s.getSmsAgreeYn());
        form.setEmailAgreeYn(s.getEmailAgreeYn());

        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/member/join/form";
        }

        // SiteContext 의 siteId — @ModelAttribute injectSiteContext() 가 주입.
        // null 이면 session sticky/EMPTY 폴백도 실패한 상황 → 가입 불가.
        String siteId = (String) model.getAttribute("siteId");
        if (siteId == null || siteId.isBlank()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "사이트 정보가 확인되지 않았습니다. 가입 시작 화면에서 사이트를 선택해 주세요.");
            return "redirect:/member/join";
        }

        // CAPTCHA 검증 — 회원가입은 봇 차단 최우선 적용 지점
        if (!captchaGuard.verifyOrFail(req)) {
            log.warn("MEMBER_JOIN captcha_fail loginId={} ip={}", form.getLoginId(), IpUtils.clientIp(req));
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "보안 검증에 실패했습니다. 다시 시도해 주세요.");
            return "redirect:/member/join/form";
        }

        try {
            String id = service.join(form, s, siteId,
                IpUtils.clientIp(req), req.getHeader("User-Agent"));
            log.info("===MEMBER_JOIN ok memberId={} loginId={} userType={}",
                id, form.getLoginId(), s.getUserType());
            session.removeAttribute(JoinSessionData.SESS_KEY);
            return "redirect:/member/join/complete?memberId=" + id;
        } catch (IllegalArgumentException ex) {
            log.warn("MEMBER_JOIN fail loginId={} reason={}", form.getLoginId(), ex.getMessage());
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/member/join/form";
        } catch (Exception ex) {
            log.warn("MEMBER_JOIN error loginId={} reason={}", form.getLoginId(), ex.getMessage(), ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "가입 처리 중 오류가 발생했습니다.");
            return "redirect:/member/join/form";
        }
    }

    // ==================================================================
    // Step 7 — 가입 완료 페이지
    // ==================================================================

    @GetMapping("/join/complete")
    public String step7Complete(@RequestParam(name = "memberId", required = false) String memberId,
                                  Model model, RedirectAttributes ra) {
        if (memberId == null || memberId.isBlank()) {
            return "redirect:/member/login";
        }
        Member me = service.get(memberId);
        if (me == null) {
            ra.addFlashAttribute("error", "가입 정보를 확인할 수 없습니다.");
            return "redirect:/member/login";
        }
        model.addAttribute("member", me);
        return "front/join-complete";
    }

    // ==================================================================
    // 로그인 ID 중복확인 (htmx fragment)
    // ==================================================================

    @GetMapping("/join/check-login-id")
    public String checkLoginId(@RequestParam(name = "loginId", required = false) String loginId,
                                Model model) {
        String normalized = loginId == null ? "" : loginId.trim().toLowerCase();
        if (normalized.isEmpty() || !LOGIN_ID_PATTERN.matcher(normalized).matches()) {
            model.addAttribute("status", "INVALID");
            return "front/fragments/check-login-id :: result";
        }

        // SiteContext 의 siteId — @ModelAttribute injectSiteContext() 가 주입.
        // Mapper SQL 이 WHERE site_id = #{siteId} 라 null 이면 항상 0건 반환 → 가짜 AVAILABLE.
        String siteId = (String) model.getAttribute("siteId");
        log.info("===CHECK_LOGIN_ID loginId={} siteId={}", normalized, siteId);

        if (siteId == null || siteId.isBlank()) {
            // 사이트 컨텍스트 미해석 — 사용자가 사이트 컨텍스트 없이 가입 시도한 케이스
            model.addAttribute("status", "NO_SITE");
            model.addAttribute("loginId", normalized);
            return "front/fragments/check-login-id :: result";
        }

        boolean taken = service.existsLoginId(siteId, normalized);
        model.addAttribute("status", taken ? "TAKEN" : "AVAILABLE");
        model.addAttribute("loginId", normalized);
        return "front/fragments/check-login-id :: result";
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private JoinSessionData getOrCreate(HttpSession session) {
        JoinSessionData s = (JoinSessionData) session.getAttribute(JoinSessionData.SESS_KEY);
        return s != null ? s : new JoinSessionData();
    }

    /**
     * NICE CheckPlus 콜백이 세션에 저장한 {@link NiceCheckResult} 를 가입 세션 데이터로
     * 흡수한다. 이름/휴대폰/CI/CI-hash 필드를 채우고 {@code ciVerified=true} 마킹.
     * 흡수 후 NICE 결과는 세션에서 제거하여 1회 소비 보장.
     */
    private void absorbNiceResultIfPresent(HttpSession session, JoinSessionData s) {
        Object raw = session.getAttribute(NiceCheckUsrController.SESSION_RESULT);
        log.info("===NICE_ABSORB_ENTRY sessionId={} hasNiceResult={} class={}",
                session.getId(),
                raw != null,
                raw == null ? null : raw.getClass().getName());

        if (!(raw instanceof NiceCheckResult r) || !r.isSuccess()) {
            log.info("===NICE_ABSORB_SKIP reason={}",
                    raw == null ? "no-session-result"
                                : (!(raw instanceof NiceCheckResult) ? "wrong-type"
                                                                     : "result-not-success"));
            return;
        }

        log.info("===NICE_ABSORB_RAW reqSeq={} hasDI={} diLen={} name={} mobile={}",
                r.getReqSeq(),
                r.getDi() != null,
                r.getDi() == null ? 0 : r.getDi().length(),
                r.getName(),
                r.getMobileNo());

        // 정책: 회원 식별 키는 DI(64byte) 만 사용. CI 는 PII 강도 사유로 시스템 전반에서 사용 안 함.
        s.setDiVerified(true);
        if (r.getName() != null) s.setVerifiedName(r.getName());
        if (r.getMobileNo() != null) s.setVerifiedPhone(r.getMobileNo());
        if (r.getBirthDate() != null && !r.getBirthDate().isBlank()) {
            s.setVerifiedBirthday(r.getBirthDate());
        }
        if (r.getDi() != null && !r.getDi().isBlank()) {
            s.setVerifiedDi(r.getDi());
            s.setVerifiedDiHash(emailHasher.hash(r.getDi()));
        } else {
            log.warn("NICE_ABSORB_NO_DI — NICE 결과는 success 인데 DI 필드가 비어 있음. authType={} reqSeq={}",
                    r.getAuthType(), r.getReqSeq());
        }
        session.setAttribute(JoinSessionData.SESS_KEY, s);
        session.removeAttribute(NiceCheckUsrController.SESSION_RESULT);

        log.info("===NICE_ABSORB_DONE sessionId={} userType={} diVerified={} verifiedDiLen={} verifiedName={}",
                session.getId(),
                s.getUserType(),
                s.isDiVerified(),
                s.getVerifiedDi() == null ? 0 : s.getVerifiedDi().length(),
                s.getVerifiedName());
    }

    private static String normalizeUserType(String raw) {
        return "CHILD".equalsIgnoreCase(raw) ? "CHILD" : "ADULT";
    }
}
