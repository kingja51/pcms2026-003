package com.gonet.primary.system.site.controller;

import com.gonet.primary.system.site.dto.SiteContext;
import com.gonet.primary.system.site.service.SiteContextResolver;
import com.gonet.primary.system.site.service.SiteContextService;
import com.gonet.primary.system.site.service.TemplatePreview;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 학과 프로그램 <b>제네릭 쉘</b> — {@code /prg/{program}/{siteCode}}.
 *
 * <p>학과 사이트마다 {@code templates/site/{sc}/faculty·staff·lab·syllabus.html} 를 복사하던
 * 방식을 대체한다. 교수진·직원·연구실·수업계획서를 제네릭 프로그램 URL 로 처리하므로
 * 새 학과 추가 시 시드(메뉴 URL + 데이터)만 넣으면 되고 컨트롤러·템플릿 복사가 없다.
 *
 * <p><b>DB 격리(ArchUnit R5-a)</b>: 이 컨트롤러는 primary 라 secondary 데이터에 의존하지 않는다.
 * 사이트 레이아웃·메뉴만 렌더하고({@link SiteContextService}), 실제 목록은 htmx 가
 * secondary 쪽 조각 엔드포인트를 따로 호출한다.
 *
 * <p><b>주의 — 목록 조각은 아직 없다(2026-07-31).</b> 001 의 {@code ProgramDataUsrController}
 * (secondary, {@code /prg/{program}/{siteCode}/list})는 {@code tb_lab}·{@code tb_staff}·
 * {@code tb_syllabus} 이식 여부가 미결이라 003 에 이식하지 않았다(PLAN §7).
 * 따라서 이 쉘은 <b>레이아웃과 빈 목록 영역까지만</b> 렌더된다 — htmx 호출은 404 다.
 * 데이터 계층이 들어오면 이 컨트롤러는 수정 없이 그대로 동작한다.
 */
@Controller
public class ProgramUsrController {

    private static final Logger log = LoggerFactory.getLogger(ProgramUsrController.class);

    private static final String FALLBACK_LAYOUT = "front/layouts/EMPTY/empty";

    /** 허용 프로그램 → {제목, 태그(영문 상단 라벨)}. path-traversal 방지 화이트리스트 겸용. */
    private static final Map<String, String[]> PROGRAMS = Map.of(
        "faculty",  new String[]{"교수진",     "Faculty"},
        "staff",    new String[]{"직원",       "Staff"},
        "lab",      new String[]{"연구실",     "Research Labs"},
        "syllabus", new String[]{"수업계획서", "Course Syllabus"}
    );

    private final SiteContextService siteContextService;

    public ProgramUsrController(SiteContextService siteContextService) {
        this.siteContextService = siteContextService;
    }

    @GetMapping("/prg/{program}/{siteCode}")
    public String program(@PathVariable String program,
                          @PathVariable String siteCode,
                          Model model, HttpServletRequest req) {
        String[] meta = PROGRAMS.get(program);
        if (meta == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);

        SiteContext ctx;
        try {
            ctx = siteContextService.getContextByCode(siteCode);
        } catch (Exception ex) {
            log.warn("PRG_SHELL_RESOLVE_FAIL siteCode={} reason={}", siteCode, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (ctx == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);

        // 템플릿 프리뷰(?tmpl=CODE / ?tmpl=off — 세션 sticky) — 데모·검수용 임시 레이아웃 교체
        ctx = TemplatePreview.apply(ctx, req, siteContextService);

        String layoutPath = (ctx.getLayoutPath() != null && !ctx.getLayoutPath().isBlank())
                ? ctx.getLayoutPath().trim() : FALLBACK_LAYOUT;

        model.addAttribute("siteContext",  ctx);
        model.addAttribute("layoutPath",   layoutPath);
        model.addAttribute("siteCode",     siteCode);
        model.addAttribute("program",      program);
        model.addAttribute("programTitle", meta[0]);
        model.addAttribute("programTag",   meta[1]);

        // 세션 sticky — /prg/* 이후 평면 URL(로그인 등)도 동일 사이트 레이아웃·테마 유지
        HttpSession sess = req.getSession(true);
        Object cur = sess.getAttribute(SiteContextResolver.SESSION_SITE_CODE);
        if (!siteCode.equals(cur)) sess.setAttribute(SiteContextResolver.SESSION_SITE_CODE, siteCode);

        return "prg/program";
    }
}
