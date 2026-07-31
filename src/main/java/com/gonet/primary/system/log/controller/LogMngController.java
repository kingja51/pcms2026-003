package com.gonet.primary.system.log.controller;

import com.gonet.common.dto.PageResponse;
import com.gonet.logging.viewer.dto.LogSearch;
import com.gonet.logging.viewer.dto.TraceTimeline;
import com.gonet.logging.viewer.service.LogViewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Set;

/**
 * 관리자 로그 viewer — 5 종 log_* 테이블 통합 화면.
 *
 * <p>URL:
 * <ul>
 *   <li>{@code /admin/system/log/{type}}        — 목록 (페이지당 100건)</li>
 *   <li>{@code /admin/system/log/{type}/{id}}   — 상세</li>
 *   <li>{@code /admin/system/log/trace?traceId=...} — 통합 timeline</li>
 * </ul>
 *
 * <p>type 화이트리스트: {@code access / audit / login / file-download / error}
 *
 * <p>list 는 {@link LogSearch}({@code page} + {@code pageSize}) 페이징 + keyword/날짜 검색.
 * 모델 attribute 는 {@code page} 단일 (PageResponse) — 프로젝트 표준.
 */
@Controller
@RequestMapping("/admin/system/log")
public class LogMngController {

    private static final Logger log = LoggerFactory.getLogger(LogMngController.class);

    /** 허용 타입 — path 변조 방지 */
    private static final Set<String> ALLOWED_TYPES =
        Set.of("access", "audit", "login", "file-download", "error", "privacy", "security");

    private final LogViewService service;

    public LogMngController(LogViewService service) {
        this.service = service;
    }

    @GetMapping("/{type}")
    public String list(@PathVariable String type,
                        @ModelAttribute("search") LogSearch search,
                        Model model,
                        RedirectAttributes ra) {
        if (!ALLOWED_TYPES.contains(type)) {
            ra.addFlashAttribute("error", "알 수 없는 로그 종류: " + type);
            return "redirect:/admin/system/log/access";
        }
        PageResponse<?> page = switch (type) {
            case "access"        -> service.listAccess(search);
            case "audit"         -> service.listAudit(search);
            case "login"         -> service.listLogin(search);
            case "file-download" -> service.listFileDownload(search);
            case "error"         -> service.listError(search);
            case "privacy"       -> service.listPrivacyAccess(search);
            case "security"      -> service.listSecurity(search);
            default              -> PageResponse.of(java.util.List.of(),
                                       search.getPage(), search.getPageSize(), 0);
        };
        model.addAttribute("type", type);
        model.addAttribute("typeLabel", typeLabel(type));
        model.addAttribute("page", page);
        return "admin/system/log/list";
    }

    @GetMapping("/{type}/{id}")
    public String detail(@PathVariable String type,
                          @PathVariable long id,
                          Model model,
                          RedirectAttributes ra) {
        if (!ALLOWED_TYPES.contains(type)) {
            ra.addFlashAttribute("error", "알 수 없는 로그 종류: " + type);
            return "redirect:/admin/system/log/access";
        }
        Object row;
        switch (type) {
            case "access"        -> row = service.getAccess(id);
            case "audit"         -> row = service.getAudit(id);
            case "login"         -> row = service.getLogin(id);
            case "file-download" -> row = service.getFileDownload(id);
            case "error"         -> row = service.getError(id);
            case "privacy"       -> row = service.getPrivacyAccess(id);
            case "security"      -> row = service.getSecurity(id);
            default              -> row = null;
        }
        if (row == null) {
            ra.addFlashAttribute("error", "로그를 찾을 수 없습니다: id=" + id);
            return "redirect:/admin/system/log/" + type;
        }
        model.addAttribute("type", type);
        model.addAttribute("typeLabel", typeLabel(type));
        model.addAttribute("row", row);
        return "admin/system/log/detail";
    }

    /**
     * trace_id 통합 timeline — 4개 log_* 테이블에서 같은 traceId 행을 시간순.
     *
     * <p>URL: {@code /admin/system/log/trace?traceId=XXXX}
     */
    @GetMapping("/trace")
    public String traceTimeline(@RequestParam(value = "traceId", required = false) String traceId,
                                  Model model) {
        TraceTimeline tl = (traceId == null || traceId.isBlank())
            ? null : service.traceTimeline(traceId);
        model.addAttribute("traceId", traceId);
        model.addAttribute("timeline", tl);
        return "admin/system/log/trace";
    }

    /**
     * 감사로그(audit) action 카탈로그 가이드 페이지 — 어떤 이벤트가 어떤 시점에 적재되는지
     * 도메인 그룹별로 보여주는 정적 가이드. {@code /admin/system/log/{type}/{id}} 매핑보다
     * 구체적인 경로이므로 Spring URL 매칭이 먼저 처리한다.
     */
    @GetMapping("/audit/guide")
    public String auditGuide() {
        return "admin/system/log/audit-guide";
    }

    private static String typeLabel(String type) {
        return switch (type) {
            case "access"        -> "접근 로그 (log_access)";
            case "audit"         -> "감사 로그 (log_audit)";
            case "login"         -> "로그인 이력 (log_login)";
            case "file-download" -> "파일 다운로드 이력 (log_file_download)";
            case "error"         -> "에러 로그 (log_error)";
            case "privacy"       -> "🛡 개인정보 접근 (log_privacy_access · PIPA §29)";
            case "security"      -> "🚨 보안 이벤트 (log_security · UPLOAD_BLOCK/LOGIN_LOCK/IP_DENY/VIRUS 등)";
            default              -> type;
        };
    }
}
