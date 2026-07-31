package com.gonet.primary.member.controller;

import com.gonet.common.captcha.CaptchaGuard;
import com.gonet.common.util.IpUtils;
import com.gonet.primary.member.dto.Member;
import com.gonet.primary.member.dto.MemberPasswordForm;
import com.gonet.primary.member.dto.MemberProfileForm;
import com.gonet.primary.member.dto.MemberReauthForm;
import com.gonet.primary.member.dto.MemberWithdrawForm;
import com.gonet.primary.member.service.MemberService;
import com.gonet.primary.member.service.MyPageReauth;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import com.gonet.primary.system.site.dto.SiteContext;
import com.gonet.primary.system.site.service.SiteContextResolver;
import com.gonet.primary.system.site.service.SiteContextService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 회원 마이페이지 — {@code /member/mypage*}.
 *
 * <p>엔드포인트:
 * <ul>
 *   <li>GET  /member/mypage                 — 대시보드(요약). 재인증 불필요</li>
 *   <li>GET  /member/mypage/verify          — 민감 페이지 진입 전 재인증 폼</li>
 *   <li>POST /member/mypage/verify          — 이름+비밀번호 검증, 세션 승인 토큰 발급</li>
 *   <li>GET  /member/mypage/profile         — 개인정보 수정 폼. <b>재인증 필수</b></li>
 *   <li>POST /member/mypage/profile         — 개인정보 수정 처리. <b>재인증 필수</b></li>
 *   <li>GET  /member/mypage/password        — 비밀번호 변경 폼 (현재 비밀번호 입력 필수이므로 재인증 별도 없음)</li>
 *   <li>POST /member/mypage/password        — 비밀번호 변경 처리</li>
 *   <li>GET  /member/mypage/withdraw        — 탈퇴 폼. <b>재인증 필수</b></li>
 *   <li>POST /member/mypage/withdraw        — 탈퇴 처리(세션 무효화). <b>재인증 필수</b></li>
 * </ul>
 *
 * <p>재인증 세션 토큰 관리: {@link MyPageReauth}. TTL 5분, 세션당 5회 실패 시 잠금.
 */
@Controller
@RequestMapping("/member/mypage")
public class MyPageUsrController {

    private static final Logger log = LoggerFactory.getLogger(MyPageUsrController.class);

    private final MemberService service;
    private final SiteContextResolver siteContextResolver;
    private final CaptchaGuard captchaGuard;

    public MyPageUsrController(MemberService service,
                                SiteContextResolver siteContextResolver,
                                CaptchaGuard captchaGuard) {
        this.service = service;
        this.siteContextResolver = siteContextResolver;
        this.captchaGuard = captchaGuard;
    }

    @ModelAttribute
    public void injectSiteContext(HttpServletRequest req, Model model) {
        siteContextResolver.inject(req, model);
    }

    // ==================================================================
    // 대시보드
    // ==================================================================

    @GetMapping
    public String mypage(@AuthenticationPrincipal CustomUserDetails principal,
                          HttpSession session, Model model, RedirectAttributes ra) {
        Member me = service.get(principal.getUserId());
        if (me == null) {
            ra.addFlashAttribute("error", "회원 정보를 찾을 수 없습니다.");
            return "redirect:/member/login";
        }
        model.addAttribute("member", me);
        model.addAttribute("consents", service.listConsents(principal.getUserId()));
        model.addAttribute("reauthValid",      MyPageReauth.isValid(session));
        model.addAttribute("reauthRemaining",  MyPageReauth.remainingSeconds(session));
        return "front/mypage";
    }

    // ==================================================================
    // 재인증(step-up)
    // ==================================================================

    @GetMapping("/verify")
    public String verifyForm(@RequestParam(name = "next", required = false, defaultValue = "profile") String next,
                              HttpSession session, Model model) {
        // next 를 허용 목록으로 정규화
        String safeNext = ("withdraw".equals(next)) ? "withdraw" : "profile";
        if (!model.containsAttribute("form")) {
            MemberReauthForm f = new MemberReauthForm();
            f.setNext(safeNext);
            model.addAttribute("form", f);
        }
        model.addAttribute("next", safeNext);
        model.addAttribute("failCount", MyPageReauth.failCount(session));
        model.addAttribute("failLimit", MyPageReauth.FAIL_LIMIT);
        return "front/mypage-reauth";
    }

    @PostMapping("/verify")
    public String verifySubmit(@AuthenticationPrincipal CustomUserDetails principal,
                                @Valid @ModelAttribute("form") MemberReauthForm form,
                                BindingResult br,
                                HttpSession session,
                                RedirectAttributes ra) {
        String safeNext = ("withdraw".equals(form.getNext())) ? "withdraw" : "profile";
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/member/mypage/verify?next=" + safeNext;
        }

        boolean ok = service.verifyReauth(principal.getUserId(),
            form.getMemberName(), form.getEmail(), form.getPassword());
        if (ok) {
            MyPageReauth.mark(session);
            log.info("===MEMBER_REAUTH ok memberId={} next={}", principal.getUserId(), safeNext);
            return "redirect:/member/mypage/" + safeNext;
        }

        boolean lockedOut = MyPageReauth.incrementFail(session);
        log.warn("MEMBER_REAUTH fail memberId={} next={} failCount={} lockedOut={}",
            principal.getUserId(), safeNext, MyPageReauth.failCount(session), lockedOut);
        if (lockedOut) {
            // 한도 초과 — 민감 페이지 접근 흔적 제거 후 안내.
            ra.addFlashAttribute("error",
                "본인 확인 실패가 허용 횟수를 초과했습니다. 잠시 후 다시 시도하거나 로그아웃 후 재로그인해 주세요.");
            return "redirect:/member/mypage";
        }
        ra.addFlashAttribute("form", form);   // password 는 수동 세팅 안 하므로 flash 로 들어가도 노출 없음 — 하지만 방어적으로 비워둘 것
        form.setPassword(null);
        ra.addFlashAttribute("error", "이름 또는 비밀번호가 일치하지 않습니다.");
        return "redirect:/member/mypage/verify?next=" + safeNext;
    }

    // ==================================================================
    // 개인정보 수정 — 재인증 필수
    // ==================================================================

    @GetMapping("/profile")
    public String profileForm(@AuthenticationPrincipal CustomUserDetails principal,
                                HttpSession session, Model model, RedirectAttributes ra) {
        if (!MyPageReauth.isValid(session)) {
            return "redirect:/member/mypage/verify?next=profile";
        }
        Member me = service.get(principal.getUserId());
        if (me == null) {
            ra.addFlashAttribute("error", "회원 정보를 찾을 수 없습니다.");
            return "redirect:/member/login";
        }
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", toProfileForm(me));
        }
        model.addAttribute("member", me);
        model.addAttribute("reauthRemaining", MyPageReauth.remainingSeconds(session));
        return "front/mypage-profile";
    }

    @PostMapping("/profile")
    public String profileSubmit(@AuthenticationPrincipal CustomUserDetails principal,
                                 @Valid @ModelAttribute("form") MemberProfileForm form,
                                 BindingResult br,
                                 HttpSession session,
                                 RedirectAttributes ra,
                                 HttpServletResponse res) {
        if (!MyPageReauth.isValid(session)) {
            return "redirect:/member/mypage/verify?next=profile";
        }
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/member/mypage/profile";
        }
        try {
            service.updateProfile(principal.getUserId(), form);
            log.info("===MEMBER_PROFILE_UPDATE ok memberId={}", principal.getUserId());
            ra.addFlashAttribute("message", "개인정보를 수정했습니다.");
            res.setHeader("HX-Redirect", "/member/mypage");
            return "redirect:/member/mypage";
        } catch (IllegalArgumentException ex) {
            log.warn("MEMBER_PROFILE_UPDATE fail memberId={} reason={}",
                principal.getUserId(), ex.getMessage());
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/member/mypage/profile";
        } catch (Exception ex) {
            log.warn("MEMBER_PROFILE_UPDATE error memberId={} reason={}",
                principal.getUserId(), ex.getMessage(), ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "수정 중 오류가 발생했습니다.");
            return "redirect:/member/mypage/profile";
        }
    }

    // ==================================================================
    // 비밀번호 변경 — 폼 내부에서 현재 비밀번호 검증하므로 재인증 별도 필요 없음
    // ==================================================================

    @GetMapping("/password")
    public String passwordForm(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new MemberPasswordForm());
        }
        return "front/mypage-password";
    }

    @PostMapping("/password")
    public String passwordSubmit(@AuthenticationPrincipal CustomUserDetails principal,
                                  @Valid @ModelAttribute("form") MemberPasswordForm form,
                                  BindingResult br,
                                  HttpServletRequest req,
                                  RedirectAttributes ra,
                                  HttpServletResponse res) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/member/mypage/password";
        }
        // CAPTCHA — 비번 재설정 단계 봇 차단
        if (!captchaGuard.verifyOrFail(req)) {
            log.warn("MEMBER_PWD_CHANGE captcha_fail memberId={} ip={}",
                principal.getUserId(), IpUtils.clientIp(req));
            ra.addFlashAttribute("error", "보안 검증에 실패했습니다. 다시 시도해 주세요.");
            return "redirect:/member/mypage/password";
        }
        try {
            service.changePassword(principal.getUserId(), form);
            log.info("===MEMBER_PWD_CHANGE ok memberId={}", principal.getUserId());
            ra.addFlashAttribute("message", "비밀번호를 변경했습니다.");
            res.setHeader("HX-Redirect", "/member/mypage");
            return "redirect:/member/mypage";
        } catch (IllegalArgumentException ex) {
            log.warn("MEMBER_PWD_CHANGE fail memberId={} reason={}",
                principal.getUserId(), ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/member/mypage/password";
        } catch (Exception ex) {
            log.warn("MEMBER_PWD_CHANGE error memberId={} reason={}",
                principal.getUserId(), ex.getMessage(), ex);
            ra.addFlashAttribute("error", "비밀번호 변경 중 오류가 발생했습니다.");
            return "redirect:/member/mypage/password";
        }
    }

    // ==================================================================
    // 탈퇴 — 재인증 필수. GET = 탈퇴 폼, POST = 실행
    // ==================================================================

    @GetMapping("/withdraw")
    public String withdrawForm(@AuthenticationPrincipal CustomUserDetails principal,
                                HttpSession session, Model model, RedirectAttributes ra) {
        if (!MyPageReauth.isValid(session)) {
            return "redirect:/member/mypage/verify?next=withdraw";
        }
        Member me = service.get(principal.getUserId());
        if (me == null) {
            ra.addFlashAttribute("error", "회원 정보를 찾을 수 없습니다.");
            return "redirect:/member/login";
        }
        if (!model.containsAttribute("withdrawForm")) {
            model.addAttribute("withdrawForm", new MemberWithdrawForm());
        }
        model.addAttribute("member", me);
        model.addAttribute("reauthRemaining", MyPageReauth.remainingSeconds(session));
        return "front/mypage-withdraw";
    }

    @PostMapping("/withdraw")
    public String withdraw(@AuthenticationPrincipal CustomUserDetails principal,
                            @Valid @ModelAttribute("withdrawForm") MemberWithdrawForm form,
                            BindingResult br,
                            HttpServletRequest req,
                            HttpSession session,
                            RedirectAttributes ra) {
        if (!MyPageReauth.isValid(session)) {
            return "redirect:/member/mypage/verify?next=withdraw";
        }
        if (br.hasErrors()) {
            ra.addFlashAttribute("withdrawForm", form);
            ra.addFlashAttribute("org.springframework.validation.BindingResult.withdrawForm", br);
            ra.addFlashAttribute("error", "탈퇴 사유를 10자 이상 입력해 주세요.");
            return "redirect:/member/mypage/withdraw";
        }
        try {
            service.withdraw(principal.getUserId(), form.getWithdrawReason().trim());
            log.info("===MEMBER_WITHDRAW ok memberId={}", principal.getUserId());
            // 세션 무효화(재인증 토큰 포함 모두 제거) + 시큐리티 컨텍스트 정리
            if (req.getSession(false) != null) req.getSession(false).invalidate();
            SecurityContextHolder.clearContext();
            return "redirect:/member/login?withdraw=1";
        } catch (IllegalArgumentException ex) {
            log.warn("MEMBER_WITHDRAW fail memberId={} reason={}",
                principal.getUserId(), ex.getMessage());
            ra.addFlashAttribute("withdrawForm", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/member/mypage/withdraw";
        } catch (Exception ex) {
            log.warn("MEMBER_WITHDRAW error memberId={} reason={}",
                principal.getUserId(), ex.getMessage(), ex);
            ra.addFlashAttribute("withdrawForm", form);
            ra.addFlashAttribute("error", "탈퇴 처리 중 오류가 발생했습니다.");
            return "redirect:/member/mypage/withdraw";
        }
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private MemberProfileForm toProfileForm(Member m) {
        MemberProfileForm f = new MemberProfileForm();
        f.setMemberName(m.getMemberName());
        f.setNickname(m.getNickname());
        f.setEmail(m.getEmail());
        f.setPhone(m.getPhone());
        f.setAddressZipcode(m.getAddressZipcode());
        f.setAddress(m.getAddress());
        f.setAddressDetail(m.getAddressDetail());
        f.setMarketingAgreeYn(m.getMarketingAgreeYn());
        f.setSmsAgreeYn(m.getSmsAgreeYn());
        f.setEmailAgreeYn(m.getEmailAgreeYn());
        return f;
    }
}
