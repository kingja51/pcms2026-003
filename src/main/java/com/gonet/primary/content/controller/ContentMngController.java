package com.gonet.primary.content.controller;

import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.dto.ExcelDownloadRequest;
import com.gonet.common.dto.PageResponse;
import com.gonet.common.excel.ExcelResponseWriter;
import com.gonet.common.util.JsonUtils;
import com.gonet.primary.content.dto.Content;
import com.gonet.primary.content.dto.ContentHistory;
import com.gonet.primary.content.dto.ContentSaveForm;
import com.gonet.primary.content.dto.ContentSearch;
import com.gonet.primary.content.dto.ContentStatus;
import com.gonet.primary.content.service.ContentFileWriter;
import com.gonet.primary.content.service.ContentService;
import com.gonet.primary.system.site.dto.Site;
import com.gonet.primary.system.site.dto.SiteSearch;
import com.gonet.primary.system.site.service.SiteContextService;
import com.gonet.primary.system.site.service.SiteService;
import com.gonet.primary.system.site.dto.SiteContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static com.gonet.common.excel.ExcelResponseWriter.nvl;
import static com.gonet.common.excel.ExcelResponseWriter.str;

/**
 * 콘텐츠 관리 Controller — CRUD + 상태 전환 + 버전 이력 + 엑셀.
 *
 * <p>URL prefix: {@code /admin/system/content}.
 *
 * <p>상태 전환은 별도 엔드포인트({@code POST /{id}/status?target=PUBLISHED}) — 본문 저장과 분리.
 * 이력 조회는 {@code GET /{id}/history}.
 */
@Controller
@RequestMapping("/admin/system/content")
public class ContentMngController {

    private static final Logger log = LoggerFactory.getLogger(ContentMngController.class);

    private final ContentService       service;
    private final SiteService          siteService;
    private final SiteContextService   siteContextService;
    private final ContentFileWriter    fileWriter;
    private final AuditLogger          auditLogger;
    private final ExcelResponseWriter  excelWriter;

    public ContentMngController(ContentService service,
                                 SiteService siteService,
                                 SiteContextService siteContextService,
                                 ContentFileWriter fileWriter,
                                 AuditLogger auditLogger,
                                 ExcelResponseWriter excelWriter) {
        this.service            = service;
        this.siteService        = siteService;
        this.siteContextService = siteContextService;
        this.fileWriter         = fileWriter;
        this.auditLogger        = auditLogger;
        this.excelWriter        = excelWriter;
    }

    // ==================================================================
    // 조회
    // ==================================================================

    @GetMapping
    public String list(@ModelAttribute("search") ContentSearch search, Model model) {
        List<Content> rows = service.search(search);
        int total = service.count(search);
        model.addAttribute("page",
            PageResponse.of(rows, search.getPage(), search.getPageSize(), total));
        model.addAttribute("sites", listSites());
        model.addAttribute("statuses", ContentStatus.values());
        return "admin/system/content/list";
    }

    @GetMapping("/{contentId}")
    public String detail(@PathVariable String contentId,
                          Model model,
                          RedirectAttributes ra) {
        Content c = service.get(contentId);
        if (c == null) {
            ra.addFlashAttribute("error", "콘텐츠를 찾을 수 없습니다.");
            return "redirect:/admin/system/content";
        }
        ContentStatus current = c.statusEnum();
        model.addAttribute("content", c);
        model.addAttribute("currentStatus", current);
        model.addAttribute("transitions", allowedTransitions(current));
        model.addAttribute("fileGenerable",
            current == ContentStatus.PUBLISHED
                && c.getSiteCode() != null && !c.getSiteCode().isBlank()
                && c.getSlug() != null && !c.getSlug().isBlank());
        return "admin/system/content/detail";
    }

    @GetMapping("/{contentId}/history")
    public String history(@PathVariable String contentId,
                           Model model,
                           RedirectAttributes ra) {
        Content c = service.get(contentId);
        if (c == null) {
            ra.addFlashAttribute("error", "콘텐츠를 찾을 수 없습니다.");
            return "redirect:/admin/system/content";
        }
        List<ContentHistory> history = service.listHistory(contentId);
        model.addAttribute("content", c);
        model.addAttribute("history", history);
        return "admin/system/content/history";
    }

    // ==================================================================
    // Create / Update 폼
    // ==================================================================

    @GetMapping("/new")
    public String createForm(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new ContentSaveForm());
        }
        model.addAttribute("sites", listSites());
        model.addAttribute("mode", "create");
        return "admin/system/content/form";
    }

    @GetMapping("/{contentId}/edit")
    public String editForm(@PathVariable String contentId,
                            Model model,
                            RedirectAttributes ra) {
        Content c = service.get(contentId);
        if (c == null) {
            ra.addFlashAttribute("error", "콘텐츠를 찾을 수 없습니다.");
            return "redirect:/admin/system/content";
        }
        model.addAttribute("form", toForm(c));
        model.addAttribute("sites", listSites());
        model.addAttribute("mode", "edit");
        return "admin/system/content/form";
    }

    // ==================================================================
    // CUD
    // ==================================================================

    @PostMapping
    public String create(@Valid @ModelAttribute("form") ContentSaveForm form,
                          BindingResult br,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/content/new";
        }
        try {
            String contentId = service.create(form);
            log.info("===CONTENT_CREATE ok contentId={} title={}", contentId, form.getTitle());
            ra.addFlashAttribute("message", "콘텐츠를 등록했습니다.");
            String target = "/admin/system/content/" + contentId;
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            log.warn("CONTENT_CREATE fail title={} reason={}", form.getTitle(), ex.getMessage());
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/content/new";
        } catch (Exception ex) {
            log.warn("CONTENT_CREATE error title={} reason={}", form.getTitle(), ex.getMessage(), ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "등록 중 오류가 발생했습니다.");
            return "redirect:/admin/system/content/new";
        }
    }

    @PostMapping("/{contentId}")
    public String update(@PathVariable String contentId,
                          @Valid @ModelAttribute("form") ContentSaveForm form,
                          BindingResult br,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        form.setContentId(contentId);
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/content/" + contentId + "/edit";
        }
        try {
            service.update(form);
            log.info("===CONTENT_UPDATE ok contentId={}", contentId);
            ra.addFlashAttribute("message", "콘텐츠를 수정했습니다.");
            String target = "/admin/system/content/" + contentId;
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            log.warn("CONTENT_UPDATE fail contentId={} reason={}", contentId, ex.getMessage());
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/content/" + contentId + "/edit";
        } catch (Exception ex) {
            log.warn("CONTENT_UPDATE error contentId={} reason={}", contentId, ex.getMessage(), ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "수정 중 오류가 발생했습니다.");
            return "redirect:/admin/system/content/" + contentId + "/edit";
        }
    }

    @DeleteMapping("/{contentId}")
    public String delete(@PathVariable String contentId,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        try {
            service.softDelete(contentId);
            log.info("===CONTENT_DELETE ok contentId={}", contentId);
            ra.addFlashAttribute("message", "콘텐츠를 삭제했습니다.");
            String target = "/admin/system/content";
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            log.warn("CONTENT_DELETE fail contentId={} reason={}", contentId, ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/content/" + contentId;
        } catch (Exception ex) {
            log.warn("CONTENT_DELETE error contentId={} reason={}", contentId, ex.getMessage(), ex);
            ra.addFlashAttribute("error", "삭제 중 오류가 발생했습니다.");
            return "redirect:/admin/system/content/" + contentId;
        }
    }

    // ==================================================================
    // 상태 전환
    // ==================================================================

    @PostMapping("/{contentId}/status")
    public String changeStatus(@PathVariable String contentId,
                                @RequestParam("target") String targetRaw,
                                RedirectAttributes ra,
                                HttpServletResponse res) {
        String redirect = "/admin/system/content/" + contentId;
        try {
            ContentStatus target = ContentStatus.valueOf(targetRaw.trim().toUpperCase());
            service.changeStatus(contentId, target);
            ra.addFlashAttribute("message", "상태를 변경했습니다: " + target);
            res.setHeader("HX-Redirect", redirect);
            return "redirect:" + redirect;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            log.warn("CONTENT_STATUS fail contentId={} target={} reason={}",
                contentId, targetRaw, ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:" + redirect;
        } catch (Exception ex) {
            log.warn("CONTENT_STATUS error contentId={} target={} reason={}",
                contentId, targetRaw, ex.getMessage(), ex);
            ra.addFlashAttribute("error", "상태 변경 중 오류가 발생했습니다.");
            return "redirect:" + redirect;
        }
    }

    // ==================================================================
    // 파일 생성 (PUBLISHED only)
    // ==================================================================

    /**
     * 콘텐츠를 실제 Thymeleaf 파일({@code templates/site/{siteCode}/{slug}.html})로 디스크에 기록.
     * PUBLISHED 상태이고 siteCode/slug 가 모두 채워져 있을 때만 허용.
     */
    @PostMapping("/{contentId}/generate-file")
    public String generateFile(@PathVariable String contentId,
                                RedirectAttributes ra,
                                HttpServletResponse res) {
        String redirect = "/admin/system/content/" + contentId;
        Content c = service.get(contentId);
        if (c == null) {
            ra.addFlashAttribute("error", "콘텐츠를 찾을 수 없습니다.");
            return "redirect:/admin/system/content";
        }
        ContentStatus current = c.statusEnum();
        if (current != ContentStatus.PUBLISHED) {
            ra.addFlashAttribute("error",
                "PUBLISHED 상태일 때만 파일을 생성할 수 있습니다. 현재 상태: " + current);
            return "redirect:" + redirect;
        }
        if (c.getSlug() == null || c.getSlug().isBlank()) {
            ra.addFlashAttribute("error", "slug 가 비어 있어 파일 경로를 결정할 수 없습니다.");
            return "redirect:" + redirect;
        }
        try {
            Path written = fileWriter.write(c);
            auditLogger.write(AuditEvent.of("CONTENT_FILE_WRITE", "tb_content")
                .withTarget(contentId)
                .withAfter("{\"site\":" + JsonUtils.quote(c.getSiteCode())
                    + ",\"slug\":" + JsonUtils.quote(c.getSlug())
                    + ",\"path\":" + JsonUtils.quote(written.toString()) + "}")
                .withResult("SUCCESS"));
            log.info("===CONTENT_FILE_WRITE ok contentId={} path={}", contentId, written);
            ra.addFlashAttribute("message", "파일을 생성했습니다: " + written);
            res.setHeader("HX-Redirect", redirect);
            return "redirect:" + redirect;
        } catch (IllegalArgumentException ex) {
            log.warn("CONTENT_FILE_WRITE fail contentId={} reason={}", contentId, ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:" + redirect;
        } catch (Exception ex) {
            log.warn("CONTENT_FILE_WRITE error contentId={}", contentId, ex);
            ra.addFlashAttribute("error", "파일 생성 중 오류가 발생했습니다: " + ex.getMessage());
            return "redirect:" + redirect;
        }
    }

    // ==================================================================
    // 미리보기
    // ==================================================================

    /**
     * 통합 미리보기 페이지 — admin layout 안에서 BODY 편집 + iframe 미리보기를 한 화면에 배치.
     * 진입 직후 textarea 에 저장된 body 가 채워지고, 사용자가 편집하면 자동/수동으로
     * iframe 이 {@link #previewLive} 를 호출해 갱신된다. 본 페이지의 편집은 임시 — 저장은 편집 화면에서 수행.
     */
    @GetMapping("/{contentId}/preview")
    public String preview(@PathVariable String contentId,
                           Model model,
                           RedirectAttributes ra) {
        Content c = service.get(contentId);
        if (c == null) {
            ra.addFlashAttribute("error", "콘텐츠를 찾을 수 없습니다.");
            return "redirect:/admin/system/content";
        }
        model.addAttribute("content", c);
        model.addAttribute("body", c.getBody() == null ? "" : c.getBody());
        return "admin/system/content/preview";
    }

    /**
     * 미리보기 화면에서 BODY 만 빠르게 저장 — 이력 스냅샷 + version_no 증가 + 색인 갱신.
     * title/slug/menu/seo 는 보존. 변경이 없으면 no-op.
     */
    @PostMapping("/{contentId}/body")
    public String updateBody(@PathVariable String contentId,
                              @RequestParam(value = "body", required = false) String body,
                              @RequestParam(value = "changeNote", required = false) String changeNote,
                              RedirectAttributes ra,
                              HttpServletResponse res) {
        String redirect = "/admin/system/content/" + contentId + "/preview";
        try {
            service.updateBody(contentId, body, changeNote);
            ra.addFlashAttribute("message", "본문을 저장했습니다.");
            res.setHeader("HX-Redirect", redirect);
            return "redirect:" + redirect;
        } catch (IllegalArgumentException ex) {
            log.warn("CONTENT_UPDATE_BODY fail contentId={} reason={}", contentId, ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:" + redirect;
        } catch (Exception ex) {
            log.warn("CONTENT_UPDATE_BODY error contentId={}", contentId, ex);
            ra.addFlashAttribute("error", "저장 중 오류가 발생했습니다.");
            return "redirect:" + redirect;
        }
    }

    /**
     * iframe 안에서 렌더되는 실제 미리보기 — 사이트 layoutPath 로 decorate 한 결과.
     * 통합 미리보기 페이지의 편집 panel 이 POST 로 호출하며, body/title/siteId 를 매 호출마다 새로 받는다.
     */
    @PostMapping("/preview-live")
    public String previewLive(@RequestParam(value = "siteId",   required = false) String siteId,
                               @RequestParam(value = "title",   required = false) String title,
                               @RequestParam(value = "body",    required = false) String body,
                               Model model) {
        injectLayoutForSite(siteId, model);
        model.addAttribute("title", title == null ? "(미리보기)" : title);
        model.addAttribute("body",  body  == null ? ""           : body);
        model.addAttribute("previewMode", "live");
        return "admin/system/content/preview-render";
    }

    private void injectLayoutForSite(String siteId, Model model) {
        String layoutPath = "front/layouts/EMPTY/empty";
        String siteCode   = null;
        if (siteId != null && !siteId.isBlank()) {
            try {
                SiteContext ctx = siteContextService.getContext(siteId);
                if (ctx != null) {
                    siteCode = ctx.getSiteCode();
                    String lp = ctx.getLayoutPath();
                    if (lp != null && !lp.isBlank()) layoutPath = lp;
                    model.addAttribute("siteContext", ctx);
                }
            } catch (Exception ex) {
                log.info("preview layout resolve failed siteId={} reason={}", siteId, ex.getMessage());
            }
        }
        model.addAttribute("layoutPath", layoutPath);
        model.addAttribute("siteCode",   siteCode);
    }

    // ==================================================================
    // Excel
    // ==================================================================

    @PostMapping("/excel")
    public String excel(@Valid @ModelAttribute("excelReq") ExcelDownloadRequest excelReq,
                         BindingResult br,
                         @ModelAttribute("search") ContentSearch search,
                         HttpServletRequest req,
                         HttpServletResponse res,
                         RedirectAttributes ra) throws IOException {
        if (br.hasErrors()) {
            ra.addFlashAttribute("error", "다운로드 사유는 10자 이상 입력해주세요.");
            return "redirect:/admin/system/content";
        }
        search.setPage(1);
        search.setPageSize(10_000);
        List<Content> rows = service.search(search);

        String excelFilename = excelWriter.write(res, "콘텐츠", "gopcms_contents",
            new String[]{ "콘텐츠ID", "사이트", "메뉴", "제목", "slug",
                          "상태", "버전", "게시시각", "수정자", "수정시각" },
            rows,
            (row, c) -> {
                row.createCell(0).setCellValue(nvl(c.getContentId()));
                row.createCell(1).setCellValue(nvl(c.getSiteCode()));
                row.createCell(2).setCellValue(nvl(c.getMenuName()));
                row.createCell(3).setCellValue(nvl(c.getTitle()));
                row.createCell(4).setCellValue(nvl(c.getSlug()));
                row.createCell(5).setCellValue(nvl(c.getStatus()));
                row.createCell(6).setCellValue(c.getVersionNo());
                row.createCell(7).setCellValue(str(c.getPublishedAt()));
                row.createCell(8).setCellValue(nvl(c.getUpdatedBy()));
                row.createCell(9).setCellValue(str(c.getUpdatedAt()));
            });

        auditLogger.write(AuditEvent.of("EXCEL_DOWNLOAD", "tb_content")
            .withHttp(req.getMethod(), req.getRequestURI(),
                      req.getRemoteAddr(), req.getHeader("User-Agent"))
            .withResult("SUCCESS")
            .withAfter("{\"count\":" + rows.size()+",\"filename\":" + excelFilename+",\"keyword\":" + search.getKeyword()
                + ",\"reason\":" + JsonUtils.quote(excelReq.getDownloadReason()) + "}"));
        log.info("===CONTENT_EXCEL ok count={} reason='{}'",
            rows.size(), excelReq.getDownloadReason());
        return null;
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private List<Site> listSites() {
        SiteSearch ss = new SiteSearch();
        ss.setPage(1);
        ss.setPageSizeUnbounded(500);
        return siteService.search(ss);
    }

    private List<ContentStatus> allowedTransitions(ContentStatus current) {
        List<ContentStatus> out = new java.util.ArrayList<>();
        for (ContentStatus s : ContentStatus.values()) {
            if (current.canTransitionTo(s)) out.add(s);
        }
        return out;
    }

    private ContentSaveForm toForm(Content c) {
        ContentSaveForm f = new ContentSaveForm();
        f.setContentId(c.getContentId());
        f.setSiteId(c.getSiteId());
        f.setMenuId(c.getMenuId());
        f.setTitle(c.getTitle());
        f.setSlug(c.getSlug());
        f.setBody(c.getBody());
        f.setOriginalContent(c.getOriginalContent());
        f.setSummary(c.getSummary());
        f.setMetaKeywords(c.getMetaKeywords());
        f.setMetaDescription(c.getMetaDescription());
        return f;
    }
}
