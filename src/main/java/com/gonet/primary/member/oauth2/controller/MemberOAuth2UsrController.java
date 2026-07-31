package com.gonet.primary.member.oauth2.controller;

import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.crypto.EmailHasher;
import com.gonet.common.util.CsvUtils;
import com.gonet.common.util.IpUtils;
import com.gonet.config.oauth2.OAuth2Properties;
import com.gonet.primary.member.dto.JoinSessionData;
import com.gonet.primary.member.dto.Member;
import com.gonet.primary.member.dto.MemberJoinForm;
import com.gonet.primary.member.oauth2.dto.ExternalProfile;
import com.gonet.primary.member.oauth2.dto.MemberOAuth;
import com.gonet.primary.member.oauth2.dto.OAuth2Provider;
import com.gonet.primary.member.oauth2.service.MemberOAuthService;
import com.gonet.primary.member.oauth2.service.OAuth2Exception;
import com.gonet.primary.member.oauth2.service.OAuth2Service;
import com.gonet.primary.member.service.MemberService;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import com.gonet.primary.system.login.dto.UserLogin;
import com.gonet.primary.system.site.service.SiteContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;

/**
 * 회원 OAuth2 소셜 로그인 — 네이버/카카오/구글.
 *
 * <pre>
 *  GET  /member/oauth2/{provider}                 인가 시작 (state 발급 + provider redirect)
 *  GET  /member/oauth2/{provider}/callback        provider 콜백 — 매핑 있으면 즉시 로그인
 *                                                                 없으면 가입 폼으로 redirect
 *  GET  /member/oauth2/join                       외부 프로필 prefill 가입 폼
 *  POST /member/oauth2/join                       가입 + 매핑 INSERT + 자동 로그인
 * </pre>
 *
 * <p>SecurityConfig 에서 {@code /member/oauth2/**} 를 PERMIT_ALL 로 풀고,
 * 본 컨트롤러가 RestClient 기반 {@link OAuth2Service} 와 매핑/로그인 도메인 조작을 담당하는
 * {@link MemberOAuthService} / {@link MemberService} 를 호출. Mapper 직접 의존 금지 (eGov §4.1).
 */
@Controller
@RequestMapping("/member/oauth2")
public class MemberOAuth2UsrController {

    private static final Logger log = LoggerFactory.getLogger(MemberOAuth2UsrController.class);

    private static final String CALLBACK_PATH_FMT = "/member/oauth2/%s/callback";

    private final OAuth2Service        oauth2Service;
    private final OAuth2Properties     props;
    private final MemberOAuthService   memberOAuthService;
    private final MemberService        memberService;
    private final EmailHasher          emailHasher;
    private final AuditLogger          auditLogger;
    private final SiteContextResolver  siteContextResolver;

    private final SecureRandom random = new SecureRandom();
    private final SecurityContextRepository contextRepo = new HttpSessionSecurityContextRepository();

    public MemberOAuth2UsrController(OAuth2Service oauth2Service,
                                      OAuth2Properties props,
                                      MemberOAuthService memberOAuthService,
                                      MemberService memberService,
                                      EmailHasher emailHasher,
                                      AuditLogger auditLogger,
                                      SiteContextResolver siteContextResolver) {
        this.oauth2Service = oauth2Service;
        this.props = props;
        this.memberOAuthService = memberOAuthService;
        this.memberService = memberService;
        this.emailHasher = emailHasher;
        this.auditLogger = auditLogger;
        this.siteContextResolver = siteContextResolver;
    }

    @ModelAttribute
    public void injectSiteContext(HttpServletRequest req, Model model) {
        siteContextResolver.inject(req, model);
    }

    // ==================================================================
    // 1) 인가 시작 — /member/oauth2/{provider}
    // ==================================================================

    @GetMapping("/{provider}")
    public String start(@PathVariable("provider") String providerCode,
                         HttpServletRequest req,
                         RedirectAttributes ra) {
        OAuth2Provider provider = OAuth2Provider.fromCode(providerCode);
        if (provider == null) {
            return "redirect:/member/login?error=OAUTH2_INVALID_PROVIDER";
        }
        if (!oauth2Service.isProviderConfigured(provider)) {
            log.warn("OAUTH2_DISABLED provider={} — client-id/secret 미설정", provider);
            return "redirect:/member/login?error=OAUTH2_NOT_CONFIGURED";
        }

        String state = randomState();
        HttpSession session = req.getSession(true);
        session.setAttribute(stateKey(provider), state);

        String redirectUri = buildRedirectUri(req, provider);
        String authorizeUrl = oauth2Service.buildAuthorizeUrl(provider, state, redirectUri);
        log.info("===OAUTH2_START provider={} ip={} sessionId={} stateKey={} statePrefix={} redirectUri={}",
            provider, IpUtils.clientIp(req), session.getId(),
            stateKey(provider),
            state.length() > 8 ? state.substring(0, 8) + "..." : state,
            redirectUri);
        return "redirect:" + authorizeUrl;
    }

    // ==================================================================
    // 2) 콜백 — /member/oauth2/{provider}/callback
    // ==================================================================

    @GetMapping("/{provider}/callback")
    public String callback(@PathVariable("provider") String providerCode,
                            @RequestParam(name = "code", required = false) String code,
                            @RequestParam(name = "state", required = false) String state,
                            @RequestParam(name = "error", required = false) String error,
                            HttpServletRequest req,
                            HttpServletResponse res) {
        OAuth2Provider provider = OAuth2Provider.fromCode(providerCode);
        if (provider == null) {
            return "redirect:/member/login?error=OAUTH2_INVALID_PROVIDER";
        }
        if (error != null && !error.isBlank()) {
            log.info("===OAUTH2_USER_CANCEL provider={} error={}", provider, error);
            return "redirect:/member/login?error=OAUTH2_CANCELED";
        }
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            return "redirect:/member/login?error=OAUTH2_INVALID_RESPONSE";
        }

        // state 검증 — 1회 소비
        HttpSession existingSess = req.getSession(false);
        log.info("===OAUTH2_CALLBACK_ENTRY provider={} ip={} sessionId={} hasExistingSession={} cookieHeader={} receivedStatePrefix={}",
            provider, IpUtils.clientIp(req),
            existingSess == null ? "(none)" : existingSess.getId(),
            existingSess != null,
            req.getHeader("Cookie"),
            state.length() > 8 ? state.substring(0, 8) + "..." : state);

        HttpSession session = req.getSession(true);
        String expected = (String) session.getAttribute(stateKey(provider));
        // 세션의 모든 PCMS_OAUTH2_STATE:* 키를 디버그용으로 노출 — sessionId 동일하나 stateKey 미스매치 케이스 식별
        java.util.List<String> allStateKeys = new java.util.ArrayList<>();
        java.util.Enumeration<String> attrNames = session.getAttributeNames();
        while (attrNames.hasMoreElements()) {
            String name = attrNames.nextElement();
            if (name.startsWith(props.getStateSessionKey())) allStateKeys.add(name);
        }
        log.info("===OAUTH2_CALLBACK_LOOKUP provider={} sessionId={} stateKey={} expected={} allStateKeys={}",
            provider, session.getId(), stateKey(provider),
            expected == null ? "(null)" : (expected.length() > 8 ? expected.substring(0, 8) + "..." : expected),
            allStateKeys);

        session.removeAttribute(stateKey(provider));
        if (expected == null || !expected.equals(state)) {
            String clientIp = IpUtils.clientIp(req);
            log.warn("OAUTH2_STATE_MISMATCH provider={} ip={} sessionId={} expectedNull={} stateLenMatch={}",
                provider, clientIp, session.getId(),
                expected == null,
                expected != null && expected.length() == state.length());
            // M-8 — 보안 이벤트 적재. CSRF 우회 시도 가능성이 있어 감사 추적 필수.
            auditLogger.write(AuditEvent.of("OAUTH2_STATE_MISMATCH", "tb_member_oauth")
                .withAfter("{\"provider\":\"" + provider
                    + "\",\"expectedPresent\":" + (expected != null)
                    + ",\"clientIp\":\"" + clientIp + "\"}")
                .withResult("FAIL"));
            return "redirect:/member/login?error=OAUTH2_STATE_MISMATCH";
        }

        ExternalProfile profile;
        try {
            profile = oauth2Service.exchangeAndFetchProfile(
                provider, code, buildRedirectUri(req, provider));
        } catch (OAuth2Exception ex) {
            log.warn("OAUTH2_EXCHANGE_FAIL provider={} reason={}", provider, ex.getMessage());
            return "redirect:/member/login?error=OAUTH2_EXCHANGE_FAIL";
        }

        // 매핑 조회 — 있으면 즉시 로그인
        MemberOAuth existing = memberOAuthService.findByProviderUser(
            provider.name(), profile.getProviderUserId());
        if (existing != null) {
            Member m = memberService.get(existing.getMemberId());
            if (m == null || "Y".equalsIgnoreCase(m.getDeleteYn())) {
                // 회원은 탈퇴(soft delete) 되었으나 OAuth 매핑이 stale 하게 남아있는 상태.
                // 매핑을 자동 정리하고 신규 가입 폼으로 진입 — 같은 provider 계정으로 재가입 허용.
                log.warn("OAUTH2_MEMBER_GONE_RELINK memberId={} provider={} memberOauthId={}",
                    existing.getMemberId(), provider, existing.getMemberOauthId());
                memberOAuthService.deactivate(existing.getMemberOauthId());
                session.setAttribute(ExternalProfile.SESS_KEY, profile);
                return "redirect:/member/oauth2/join";
            }
            if (!"ACTIVE".equalsIgnoreCase(m.getStatus())) {
                return "redirect:/member/login?error=OAUTH2_NOT_ACTIVE";
            }
            memberOAuthService.updateLastLogin(existing.getMemberOauthId());
            authenticate(req, res, m);
            auditLogger.write(AuditEvent.of("OAUTH2_LOGIN", "tb_member_oauth")
                .withActor(m.getMemberId(), "MEMBER", m.getLoginId())
                .withTarget(existing.getMemberOauthId())
                .withResult("SUCCESS")
                .withAfter("{\"provider\":\"" + provider.name() + "\"}"));
            log.info("===OAUTH2_LOGIN_OK provider={} memberId={} loginId={}",
                provider, m.getMemberId(), m.getLoginId());
            return "redirect:/member/mypage";
        }

        // 매핑 없음 — 가입 폼으로
        session.setAttribute(ExternalProfile.SESS_KEY, profile);
        log.info("===OAUTH2_NEW_PROFILE provider={} providerUserId={} email={}",
            provider, profile.getProviderUserId(), profile.getEmail());
        return "redirect:/member/oauth2/join";
    }

    // ==================================================================
    // 3) 가입 폼 (외부 프로필 prefill) — /member/oauth2/join
    // ==================================================================

    @GetMapping("/join")
    public String joinForm(HttpSession session, Model model, RedirectAttributes ra) {
        ExternalProfile profile = (ExternalProfile) session.getAttribute(ExternalProfile.SESS_KEY);
        if (profile == null || !profile.hasIdentity()) {
            ra.addFlashAttribute("error", "OAuth2 인증 정보가 만료되었습니다. 다시 시도해 주세요.");
            return "redirect:/member/login";
        }
        if (!model.containsAttribute("form")) {
            MemberJoinForm f = new MemberJoinForm();
            f.setUserType("ADULT");
            f.setMemberName(safe(profile.getName()));
            f.setNickname(safe(profile.getNickname()));
            f.setEmail(safe(profile.getEmail()));
            // 약관 5종은 폼에서 사용자가 직접 동의 — prefill 하지 않음
            f.setTermsAgreeYn("N");
            f.setPrivacyAgreeYn("N");
            f.setMarketingAgreeYn("N");
            f.setSmsAgreeYn("N");
            f.setEmailAgreeYn("N");
            model.addAttribute("form", f);
        }
        model.addAttribute("profile", profile);
        return "front/oauth2-join";
    }

    @PostMapping("/join")
    public String joinSubmit(@Valid @ModelAttribute("form") MemberJoinForm form,
                              BindingResult br,
                              HttpServletRequest req,
                              HttpServletResponse res,
                              HttpSession session,
                              Model model,
                              RedirectAttributes ra) {
        ExternalProfile profile = (ExternalProfile) session.getAttribute(ExternalProfile.SESS_KEY);
        if (profile == null || !profile.hasIdentity()) {
            ra.addFlashAttribute("error", "OAuth2 인증 정보가 만료되었습니다. 다시 시도해 주세요.");
            return "redirect:/member/login";
        }

        // 필수 약관 강제
        if (!"Y".equals(form.getTermsAgreeYn()) || !"Y".equals(form.getPrivacyAgreeYn())) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "필수 약관에 동의해 주세요.");
            return "redirect:/member/oauth2/join";
        }
        // OAuth2 가입은 본인 NICE 인증을 거치지 않으므로 가짜 JoinSessionData 를 구성한다.
        // di/diHash 는 provider-userId 기반 결정값 (HMAC) — NICE DI 는 비교 대상 아님.
        JoinSessionData sess = new JoinSessionData();
        sess.setUserType("ADULT");
        sess.setAgreed(true);
        sess.setDiVerified(true);
        sess.setVerifiedName(form.getMemberName() == null ? "" : form.getMemberName().trim());
        // OAuth2 회원의 di 는 provider 와 식별자를 결합한 토큰 — 다른 OAuth2 가입자와 충돌 방지
        String oauthDi = "OAUTH2:" + profile.getProvider().name() + ":" + profile.getProviderUserId();
        sess.setVerifiedDi(oauthDi);
        sess.setVerifiedDiHash(emailHasher.hash(oauthDi));
        // tb_member.join_type 직접 세팅 — provider 명 그대로 (KAKAO/NAVER/GOOGLE/APPLE)
        sess.setJoinType(profile.getProvider().name());
        sess.setTermsAgreeYn(form.getTermsAgreeYn());
        sess.setPrivacyAgreeYn(form.getPrivacyAgreeYn());
        sess.setMarketingAgreeYn(form.getMarketingAgreeYn());
        sess.setSmsAgreeYn(form.getSmsAgreeYn());
        sess.setEmailAgreeYn(form.getEmailAgreeYn());

        if (br.hasErrors()) {
            // 검증 실패 — 모든 ObjectError 의 field/defaultMessage 를 로그에 남겨 화면 alert 와 대조 가능
            br.getAllErrors().forEach(e -> log.warn("OAUTH2_JOIN_VALIDATION_FAIL field={} message={}",
                (e instanceof org.springframework.validation.FieldError fe) ? fe.getField() : "(global)",
                e.getDefaultMessage()));
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/member/oauth2/join";
        }

        String siteId = (String) model.getAttribute("siteId");
        if (siteId == null || siteId.isBlank()) {
            log.warn("OAUTH2_JOIN_NO_SITE_CONTEXT provider={} loginId={}", profile.getProvider(), form.getLoginId());
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "사이트 정보가 확인되지 않았습니다.");
            return "redirect:/member/oauth2/join";
        }

        // 회원 가입 + OAuth 매핑 INSERT + 감사 이벤트를 단일 트랜잭션으로 묶는다.
        // 매핑 INSERT 가 실패하면 회원 가입도 함께 롤백 — 부분 실패 차단.
        String memberId;
        try {
            memberId = memberOAuthService.joinAndLink(form, sess, profile, siteId,
                IpUtils.clientIp(req), req.getHeader("User-Agent"));
        } catch (IllegalArgumentException ex) {
            log.warn("OAUTH2_JOIN_FAIL provider={} loginId={} reason={}",
                profile.getProvider(), form.getLoginId(), ex.getMessage());
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/member/oauth2/join";
        } catch (Exception ex) {
            log.warn("OAUTH2_JOIN_ERROR provider={} loginId={} reason={}",
                profile.getProvider(), form.getLoginId(), ex.getMessage(), ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "가입 처리 중 오류가 발생했습니다: " + ex.getMessage());
            return "redirect:/member/oauth2/join";
        }

        // 가입 직후 자동 로그인 — SecurityContext 주입 + 세션 ID 갱신
        Member m = memberService.get(memberId);
        authenticate(req, res, m);
        session.removeAttribute(ExternalProfile.SESS_KEY);
        log.info("===OAUTH2_JOIN_OK provider={} memberId={} loginId={}",
            profile.getProvider(), memberId, form.getLoginId());
        return "redirect:/member/join/complete?memberId=" + memberId;
    }

    // ==================================================================
    // helpers
    // ==================================================================

    private String stateKey(OAuth2Provider provider) {
        return props.getStateSessionKey() + ":" + provider.name();
    }

    private String randomState() {
        byte[] buf = new byte[32];
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    /**
     * provider 콜백 redirect_uri 를 빌드한다. 우선순위:
     * <ol>
     *   <li>provider 별 명시 callback-url (application.yml) — 콘솔 등록 URL 과 정확히 일치하는 값</li>
     *   <li>redirect-base-url + /member/oauth2/{provider}/callback — 공통 origin 패턴</li>
     *   <li>현재 요청 host 폴백 — 로컬 개발 편의</li>
     * </ol>
     */
    private String buildRedirectUri(HttpServletRequest req, OAuth2Provider provider) {
        String explicit = providerCallbackUrl(provider);
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        String origin = props.getRedirectBaseUrl();
        if (origin == null || origin.isBlank()) {
            String scheme = req.getHeader("X-Forwarded-Proto");
            if (scheme == null || scheme.isBlank()) scheme = req.getScheme();
            String host = req.getServerName();
            int    port = req.getServerPort();
            boolean defaultPort = ("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443);
            origin = scheme + "://" + host + (defaultPort ? "" : ":" + port);
        }
        return origin + String.format(CALLBACK_PATH_FMT, provider.name().toLowerCase());
    }

    private String providerCallbackUrl(OAuth2Provider provider) {
        return switch (provider) {
            case NAVER  -> props.getNaver().getCallbackUrl();
            case KAKAO  -> props.getKakao().getCallbackUrl();
            case GOOGLE -> props.getGoogle().getCallbackUrl();
        };
    }

    /**
     * 비밀번호 검증 없이 SecurityContext 를 강제로 주입 — OAuth2 인증이 통과한 회원만 호출.
     * v_user_login VIEW 를 통해 CustomUserDetails 를 구성하고 세션에 저장.
     */
    private void authenticate(HttpServletRequest req, HttpServletResponse res, Member m) {
        UserLogin u = memberOAuthService.loadMemberUserLogin(m.getLoginId());
        if (u == null) {
            log.warn("OAUTH2_USER_LOGIN_VIEW_MISS loginId={} memberId={}", m.getLoginId(), m.getMemberId());
            return;
        }
        CustomUserDetails ud = new CustomUserDetails(u, CsvUtils.toList(u.getRoleIds()),
            Collections.emptyList());
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            ud, null, ud.getAuthorities());
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        contextRepo.saveContext(ctx, req, res);
        // session fixation 방지 — 새 sessionId 발급
        req.changeSessionId();
    }

    private static String safe(String s) {
        return (s == null) ? "" : s.trim();
    }
}
