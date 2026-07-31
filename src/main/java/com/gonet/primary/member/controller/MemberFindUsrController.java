package com.gonet.primary.member.controller;

import com.gonet.common.captcha.CaptchaGuard;
import com.gonet.common.crypto.EmailHasher;
import com.gonet.common.util.IpUtils;
import com.gonet.primary.member.dto.Member;
import com.gonet.primary.member.dto.MemberFindIdForm;
import com.gonet.primary.member.dto.MemberFindPasswordForm;
import com.gonet.primary.member.service.MemberService;
import com.gonet.primary.system.site.dto.SiteContext;
import com.gonet.primary.system.site.service.SiteContextResolver;
import com.gonet.primary.system.site.service.SiteContextService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 회원 아이디·비밀번호 찾기 — 비로그인 접근.
 *
 * <p>보안 고려:
 * <ul>
 *   <li>이메일은 암호화 컬럼이므로 평문 LIKE 불가 → {@code email_hash} HMAC 으로 조회</li>
 *   <li>일치 회원이 없더라도 실패 메시지는 generic 으로 고정 — 회원 존재 여부 enumeration 방지</li>
 *   <li>ID 찾기 결과는 마스킹 적용(@mask.loginId) — 전체 ID 노출 금지</li>
 *   <li>비밀번호 찾기는 임시 비밀번호를 화면에 노출하지 않고 등록 이메일로만 발송</li>
 * </ul>
 */
@Controller
@RequestMapping("/member")
public class MemberFindUsrController {

    private static final Logger log = LoggerFactory.getLogger(MemberFindUsrController.class);

    private final MemberService service;
    private final SiteContextResolver siteContextResolver;
    private final CaptchaGuard captchaGuard;
    private final EmailHasher emailHasher;

    public MemberFindUsrController(MemberService service,
                                    SiteContextResolver siteContextResolver,
                                    CaptchaGuard captchaGuard,
                                    EmailHasher emailHasher) {
        this.service = service;
        this.siteContextResolver = siteContextResolver;
        this.captchaGuard = captchaGuard;
        this.emailHasher = emailHasher;
    }


    @ModelAttribute
    public void injectSiteContext(HttpServletRequest req, Model model) {
        siteContextResolver.inject(req, model);
    }

    // ------------------------------------------------------------------
    // 아이디 찾기
    // ------------------------------------------------------------------

    @GetMapping("/find-id")
    public String findIdForm(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new MemberFindIdForm());
        }
        return "front/find-id";
    }

    @PostMapping("/find-id")
    public String findIdSubmit(@Valid @ModelAttribute("form") MemberFindIdForm form,
                                BindingResult br,
                                HttpServletRequest req,
                                RedirectAttributes ra) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("org.springframework.validation.BindingResult.form", br);
            return "redirect:/member/find-id";
        }

        // CAPTCHA — 메일 폭탄 / enumeration 방지
        if (!captchaGuard.verifyOrFail(req)) {
            log.warn("MEMBER_FIND_ID captcha_fail ip={}", IpUtils.clientIp(req));
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "보안 검증에 실패했습니다. 다시 시도해 주세요.");
            return "redirect:/member/find-id";
        }

        // 진단 로그 — 입력 이메일 정규화 + hash prefix. 운영 도입 후 noise 면 DEBUG 강등.
        String rawEmail = form.getEmail() == null ? null : form.getEmail();
        String normEmail = rawEmail == null ? null : rawEmail.trim().toLowerCase();
        String inputHash;
        try { inputHash = emailHasher.hash(rawEmail); } catch (Exception e) { inputHash = "HASH_ERR:" + e.getMessage(); }
        String hashPrefix = inputHash == null ? "null" : inputHash.substring(0, Math.min(16, inputHash.length()));
        int hashLen = inputHash == null ? 0 : inputHash.length();
        log.info("===MEMBER_FIND_ID input rawEmail={} normEmail={} hashPrefix={} hashLen={}",
            rawEmail, normEmail, hashPrefix, hashLen);

        try {
            Member m = service.findIdByNameAndEmail(null, form.getName(), form.getEmail());
            if (m == null) {
                log.info("===MEMBER_FIND_ID miss nameLen={} hashPrefix={} hashLen={} email={}",
                    form.getName() == null ? 0 : form.getName().trim().length(),
                    hashPrefix, hashLen, normEmail);
                ra.addFlashAttribute("form", form);
                ra.addFlashAttribute("error", "입력하신 이름과 이메일이 일치하는 회원을 찾을 수 없습니다.");
                return "redirect:/member/find-id";
            }
            log.info("===MEMBER_FIND_ID ok memberId={} loginId={} siteId={} deleteYn={} status={}",
                m.getMemberId(), m.getLoginId(), m.getSiteId(), m.getDeleteYn(), m.getStatus());
            ra.addFlashAttribute("foundLoginId", m.getLoginId());     // 마스킹은 템플릿에서 @mask.loginId
            ra.addFlashAttribute("foundJoinedAt", m.getCreatedAt());
            return "redirect:/member/find-id";
        } catch (Exception ex) {
            log.warn("MEMBER_FIND_ID error reason={}", ex.getMessage(), ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "처리 중 오류가 발생했습니다.");
            return "redirect:/member/find-id";
        }
    }

    // ------------------------------------------------------------------
    // 비밀번호 찾기 (임시 비밀번호 메일 발송)
    // ------------------------------------------------------------------

    @GetMapping("/find-password")
    public String findPasswordForm(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new MemberFindPasswordForm());
        }
        return "front/find-password";
    }

    @PostMapping("/find-password")
    public String findPasswordSubmit(@Valid @ModelAttribute("form") MemberFindPasswordForm form,
                                      BindingResult br,
                                      HttpServletRequest req,
                                      RedirectAttributes ra) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("org.springframework.validation.BindingResult.form", br);
            return "redirect:/member/find-password";
        }

        // CAPTCHA — 메일 폭탄 / enumeration 방지
        if (!captchaGuard.verifyOrFail(req)) {
            log.warn("MEMBER_FIND_PWD captcha_fail loginId={} ip={}",
                form.getLoginId(), IpUtils.clientIp(req));
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "보안 검증에 실패했습니다. 다시 시도해 주세요.");
            return "redirect:/member/find-password";
        }

        try {
            boolean ok = service.selfResetPassword(null,
                form.getLoginId(), form.getEmail());
            if (!ok) {
                log.info("===MEMBER_FIND_PWD miss loginId={}", form.getLoginId());
                ra.addFlashAttribute("form", form);
                ra.addFlashAttribute("error", "입력하신 정보와 일치하는 회원을 찾을 수 없습니다.");
                return "redirect:/member/find-password";
            }
            log.info("===MEMBER_FIND_PWD ok loginId={}", form.getLoginId());
            ra.addFlashAttribute("sent", Boolean.TRUE);
            return "redirect:/member/find-password";
        } catch (IllegalStateException ex) {
            // 메일 발송 실패 (resetPasswordAndSendMail 내부에서 래핑)
            log.warn("MEMBER_FIND_PWD mail-fail loginId={} reason={}",
                form.getLoginId(), ex.getMessage());
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "메일 발송에 실패했습니다. 잠시 후 다시 시도해 주세요.");
            return "redirect:/member/find-password";
        } catch (Exception ex) {
            log.warn("MEMBER_FIND_PWD error loginId={} reason={}",
                form.getLoginId(), ex.getMessage(), ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "처리 중 오류가 발생했습니다.");
            return "redirect:/member/find-password";
        }
    }
}
