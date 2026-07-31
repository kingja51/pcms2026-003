package com.gonet.primary.system.site.service;

import com.gonet.primary.system.site.dto.SiteContext;
import com.gonet.primary.system.site.dto.TemplateInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * 템플릿 프리뷰(?tmpl=CODE) — 사이트의 default_template_id 를 건드리지 않고
 * <b>현재 세션에서만</b> 레이아웃을 임시 교체해 템플릿 전환 결과를 확인하는 장치.
 *
 * <p>사용법 (사용자 화면 URL 에 쿼리 파라미터):
 * <ul>
 *   <li>{@code /me/home?tmpl=KRDS-UNIV-GREEN} — 프리뷰 시작(세션 sticky, 이후 페이지 이동에도 유지)</li>
 *   <li>{@code /me/home?tmpl=off} — 프리뷰 해제(사이트 기본 템플릿 복귀)</li>
 * </ul>
 *
 * <p>안전장치:
 * <ul>
 *   <li>코드는 {@code tb_template}(use_yn='Y', delete_yn='N') 존재 검증 후에만 적용 — 임의 경로 주입 불가</li>
 *   <li>DB 조회는 전환 시점 1회, 이후는 세션 보관 {@link TemplateInfo} 재사용</li>
 *   <li>읽기 전용 — tb_site/tb_template 를 변경하지 않는다. 영구 전환은 관리자 사이트 관리
 *       (default_template_id) 로 수행</li>
 * </ul>
 *
 * <p>적용 지점: {@code AbstractSiteUsrController}(사이트 쉘) · {@code ProgramUsrController}(/prg 쉘)
 * · {@link SiteContextResolver#inject}/{@link SiteContextResolver#injectByCodeAndStick}(게시판 등
 * resolver 경유 도메인). req 가 없는 {@code injectByCode(String, Model)} 경로는 대상 아님.
 */
public final class TemplatePreview {

    private static final Logger log = LoggerFactory.getLogger(TemplatePreview.class);

    /** 쿼리 파라미터명. */
    public static final String PARAM = "tmpl";
    /** 세션 보관 키 — 값은 검증 통과한 {@link TemplateInfo}. */
    public static final String SESSION_KEY = "PCMS_TMPL_PREVIEW";
    /** 템플릿 코드 화이트리스트 패턴 (tb_template.template_code 형식). */
    private static final Pattern SAFE_CODE = Pattern.compile("^[A-Za-z0-9._-]{2,50}$");

    private TemplatePreview() {}

    /**
     * 프리뷰가 활성인 경우 template 만 교체한 새 {@link SiteContext} 를 반환.
     * 비활성/해제/검증 실패면 원본 ctx 그대로 반환 (null 입력은 null).
     */
    public static SiteContext apply(SiteContext ctx, HttpServletRequest req, SiteContextService svc) {
        if (ctx == null) return null;
        TemplateInfo preview = resolve(req, svc);
        if (preview == null) return ctx;
        return new SiteContext(ctx.getSite(), preview, ctx.getMenuTree(), ctx.getMenuId());
    }

    /** 파라미터 우선 → 세션 sticky 순으로 프리뷰 템플릿 해석. 없으면 null. */
    private static TemplateInfo resolve(HttpServletRequest req, SiteContextService svc) {
        String param = req.getParameter(PARAM);
        if (param == null) {
            return fromSession(req);
        }
        HttpSession sess = req.getSession(true);
        if (param.isBlank() || "off".equalsIgnoreCase(param) || "default".equalsIgnoreCase(param)) {
            sess.removeAttribute(SESSION_KEY);
            log.info("TMPL_PREVIEW off");
            return null;
        }
        if (!SAFE_CODE.matcher(param).matches()) {
            log.info("TMPL_PREVIEW invalid code pattern — ignored");
            return fromSession(req);
        }
        TemplateInfo t = svc.findTemplateByCode(param.toUpperCase());
        if (t == null || t.getLayoutPath() == null || t.getLayoutPath().isBlank()) {
            log.info("TMPL_PREVIEW unknown/inactive code={} — ignored", param);
            return fromSession(req);
        }
        sess.setAttribute(SESSION_KEY, t);
        log.info("TMPL_PREVIEW on code={} layout={}", t.getTemplateCode(), t.getLayoutPath());
        return t;
    }

    private static TemplateInfo fromSession(HttpServletRequest req) {
        HttpSession sess = req.getSession(false);
        Object v = (sess != null) ? sess.getAttribute(SESSION_KEY) : null;
        return (v instanceof TemplateInfo t) ? t : null;
    }
}
