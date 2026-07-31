package com.gonet.primary.identity.controller;

import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.util.SafeReplaceUtils;
import com.gonet.primary.identity.config.NiceCheckProperties;
import com.gonet.primary.identity.dto.NiceCheckResult;
import com.gonet.primary.identity.dto.NiceEncodeResult;
import com.gonet.primary.identity.service.NiceCheckService;
import com.gonet.primary.system.site.service.SiteContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * NICE CheckPlus 안심본인인증 사용자 진입점 — JSP 샘플 3종을 Spring + Thymeleaf 로 이관.
 *
 * <ul>
 *   <li>GET  {@code /member/identity/nice}        — 암호화 데이터 생성 후 NICE 팝업 진입 폼 렌더 (구 {@code checkplus_main.jsp})</li>
 *   <li>POST {@code /member/identity/nice/success} — NICE 가 성공 콜백한 EncodeData 복호화 + 세션 검증 (구 {@code checkplus_success.jsp})</li>
 *   <li>POST {@code /member/identity/nice/fail}    — NICE 가 실패 콜백한 EncodeData 복호화 (구 {@code checkplus_fail.jsp})</li>
 * </ul>
 *
 * <p>{@code /success}/{@code /fail} 은 NICE 서버가 외부에서 POST 하므로 CSRF 필터에서
 * 예외 등록 + URL access 에 PERMIT_ALL 등록 필요 — 본 도메인은 해당 DDL/SecurityConfig
 * 변경과 패키지로 묶여 동작한다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/member/identity/nice")
@EnableConfigurationProperties(NiceCheckProperties.class)
public class NiceCheckUsrController {

    /** 세션에 저장하는 NICE 요청 번호 키 — 콜백 시 변조 검증의 단일 기준. */
    public static final String SESSION_REQ_SEQ = "NICE_REQ_SEQ";

    /** 세션에 잠시 보관하는 본인인증 결과(CI/DI/이름 등) — 회원가입 폼이 1회 소비. */
    public static final String SESSION_RESULT  = "NICE_RESULT";

    /** 부모 창(window.opener) 이 인증 성공 후 이동할 URL — 호출 측에서 세션에 미리 저장. */
    public static final String SESSION_NEXT_URL = "NICE_NEXT_URL";

    /** launch 시 받은 userType 보존 — 실패/재시도 링크에 다시 붙여 흐름 유지. */
    public static final String SESSION_USER_TYPE = "NICE_USER_TYPE";

    /** 인증 성공 후 부모창 이동 기본값 — 회원가입 흐름의 다음 단계. */
    private static final String DEFAULT_NEXT_URL = "/member/join/form";

    private final NiceCheckService niceCheckService;
    private final NiceCheckProperties props;
    private final AuditLogger auditLogger;
    private final SiteContextResolver siteContextResolver;

    /**
     * 모든 핸들러 응답에 사이트 컨텍스트({@code siteContext}, {@code layoutPath}, {@code siteCode}) 자동 주입.
     * 미적용 시 Thymeleaf {@code layout:decorate="${layoutPath ?: 'front/layouts/EMPTY/empty'}"} 가 EMPTY 로 폴백.
     */
    @ModelAttribute
    public void injectSiteContext(HttpServletRequest req, Model model) {
        siteContextResolver.inject(req, model);
    }

    /**
     * 인증 시작 — 암호화 데이터 생성 후 NICE 팝업 진입 폼 렌더.
     *
     * <p>쿼리 파라미터:
     * <ul>
     *   <li>{@code userType} — 가입 흐름의 회원 유형(ADULT/CHILD). 세션에 보존되어 실패/재시도 링크에 자동 부착.</li>
     *   <li>{@code siteCode} — 사이트 컨텍스트 강제 지정. {@code SiteContextInterceptor} 1단계가 자동 해석하므로 본 메서드는 별도 처리 없음.</li>
     * </ul>
     */
    @GetMapping
    public String launch(@RequestParam(name = "userType", required = false) String userType,
                         HttpSession session, Model model) {
        if (userType != null && !userType.isBlank()) {
            session.setAttribute(SESSION_USER_TYPE, userType);
        }

        String reqSeq = niceCheckService.generateRequestNo();
        session.setAttribute(SESSION_REQ_SEQ, reqSeq);

        NiceEncodeResult encResult = niceCheckService.encode(reqSeq);
        model.addAttribute("encData", encResult.getEncData());
        model.addAttribute("message", encResult.getMessage());
        model.addAttribute("popupUrl", props.getPopupUrl());
        model.addAttribute("userType", session.getAttribute(SESSION_USER_TYPE));
        return "front/identity/nice-launch";
    }

    /**
     * NICE 성공 콜백 — 외부 도메인이 POST/GET 하므로 CSRF 예외 + URL access PERMIT_ALL 필요.
     * 세션의 {@code REQ_SEQ} 와 복호화 결과의 {@code REQ_SEQ} 를 비교해 CSRF 유사 변조 차단.
     *
     * <p>NICE 는 인증 수단(M/X/U/F/S/C)에 따라 콜백을 POST 또는 GET 으로 보낸다 — 둘 다 허용.
     */
    @RequestMapping(value = "/success", method = { RequestMethod.GET, RequestMethod.POST })
    public String success(@RequestParam(value = "EncodeData", required = false) String encodeData,
                          HttpSession session, Model model) {
        String safe = SafeReplaceUtils.requestReplace(encodeData, "encodeData");
        NiceCheckResult result = niceCheckService.decode(safe);

        if (result.isSuccess()) {
            String sessionReq = (String) session.getAttribute(SESSION_REQ_SEQ);
            if (sessionReq == null || !sessionReq.equals(result.getReqSeq())) {
                log.warn("NICE_SESSION_MISMATCH expected={} actual={}", sessionReq, result.getReqSeq());
                model.addAttribute("result", NiceCheckResult.fail(-99, "세션 불일치 — 처음부터 다시 시도해 주세요."));
                return "front/identity/nice-fail";
            }
            session.removeAttribute(SESSION_REQ_SEQ);
            session.setAttribute(SESSION_RESULT, result);

            log.info("===NICE_SUCCESS_STORED sessionId={} reqSeq={} hasDI={} diLen={} name={} mobile={} authType={}",
                    session.getId(),
                    result.getReqSeq(),
                    result.getDi() != null,
                    result.getDi() == null ? 0 : result.getDi().length(),
                    result.getName(),
                    result.getMobileNo(),
                    result.getAuthType());

            auditLogger.write(AuditEvent.of("NICE_VERIFY_OK", "identity")
                    .withTarget(result.getReqSeq())
                    .withResult("SUCCESS"));
        } else {
            auditLogger.write(AuditEvent.of("NICE_VERIFY_FAIL", "identity")
                    .withResult("FAIL")
                    .withAfter("{\"rc\":" + result.getReturnCode() + "}"));
        }

        // 부모창이 이동할 URL — 호출자가 세션에 저장한 값 우선, 없으면 가입 폼 기본값
        String nextUrl = (String) session.getAttribute(SESSION_NEXT_URL);
        if (nextUrl == null || nextUrl.isBlank()) nextUrl = DEFAULT_NEXT_URL;
        //session.removeAttribute(SESSION_NEXT_URL);

		log.info("====== nextUrl = {}",nextUrl);
		log.info("====== result = {}",result);
		log.info("====== userType = {}", session.getAttribute(SESSION_USER_TYPE));

        model.addAttribute("result", result);
        model.addAttribute("nextUrl", nextUrl);
        model.addAttribute("userType", session.getAttribute(SESSION_USER_TYPE));
        return "front/identity/nice-success";
    }

    /** NICE 실패 콜백 — 외부 도메인이 POST/GET 하므로 CSRF 예외 + URL access PERMIT_ALL 필요. */
    @RequestMapping(value = "/fail", method = { RequestMethod.GET, RequestMethod.POST })
    public String fail(@RequestParam(value = "EncodeData", required = false) String encodeData,
                       HttpSession session, Model model) {
        String safe = SafeReplaceUtils.requestReplace(encodeData, "encodeData");
        NiceCheckResult result = niceCheckService.decode(safe);

        auditLogger.write(AuditEvent.of("NICE_VERIFY_FAIL", "identity")
                .withResult("FAIL")
                .withAfter("{\"rc\":" + result.getReturnCode() + "}"));

        model.addAttribute("result", result);
        model.addAttribute("userType", session.getAttribute(SESSION_USER_TYPE));
        return "front/identity/nice-fail";
    }
}
