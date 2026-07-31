package com.gonet.primary.board.master.controller;

import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.dto.ExcelDownloadRequest;
import com.gonet.common.dto.PageResponse;
import com.gonet.common.excel.ExcelResponseWriter;
import com.gonet.common.util.JsonUtils;
import com.gonet.primary.board.master.dto.BbsMaster;
import com.gonet.primary.board.master.dto.BbsMasterSaveForm;
import com.gonet.primary.board.master.dto.BbsMasterSearch;
import com.gonet.primary.board.master.dto.BbsType;
import com.gonet.primary.board.master.service.BoardMasterService;
import com.gonet.primary.system.site.dto.Site;
import com.gonet.primary.system.site.dto.SiteSearch;
import com.gonet.primary.system.site.service.SiteService;
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
import java.util.List;

import static com.gonet.common.excel.ExcelResponseWriter.nvl;
import static com.gonet.common.excel.ExcelResponseWriter.str;

/**
 * 게시판 마스터 관리 Controller — CRUD + 사용중지 토글 + 엑셀.
 *
 * <p>URL prefix: {@code /admin/system/board}.
 *
 * <p>B1 단계는 마스터 자체만 — 게시글/댓글 화면은 후속 PR 에서 추가 (BoardArticleMngController).
 */
@Controller
@RequestMapping("/admin/system/board")
public class BoardMngController {

    private static final Logger log = LoggerFactory.getLogger(BoardMngController.class);

    private final BoardMasterService  service;
    private final SiteService         siteService;
    private final AuditLogger         auditLogger;
    private final ExcelResponseWriter excelWriter;

    public BoardMngController(BoardMasterService service,
                                SiteService siteService,
                                AuditLogger auditLogger,
                                ExcelResponseWriter excelWriter) {
        this.service     = service;
        this.siteService = siteService;
        this.auditLogger = auditLogger;
        this.excelWriter = excelWriter;
    }

    // ==================================================================
    // 조회
    // ==================================================================

    @GetMapping
    public String list(@ModelAttribute("search") BbsMasterSearch search, Model model) {
        List<BbsMaster> rows = service.search(search);
        int total = service.count(search);
        model.addAttribute("page",
            PageResponse.of(rows, search.getPage(), search.getPageSize(), total));
        model.addAttribute("sites", listSites());
        model.addAttribute("bbsTypes", BbsType.values());
        return "admin/system/board/list";
    }

    @GetMapping("/{bbsMasterId}")
    public String detail(@PathVariable String bbsMasterId,
                          Model model,
                          RedirectAttributes ra) {
        BbsMaster m = service.get(bbsMasterId);
        if (m == null) {
            ra.addFlashAttribute("error", "게시판을 찾을 수 없습니다.");
            return "redirect:/admin/system/board";
        }
        model.addAttribute("master", m);
        return "admin/system/board/detail";
    }

    // ==================================================================
    // Create / Update 폼
    // ==================================================================

    @GetMapping("/new")
    public String createForm(Model model) {
        if (!model.containsAttribute("form")) {
            BbsMasterSaveForm form = new BbsMasterSaveForm();
            form.setBbsType("FREE");
            form.setCommentYn("Y");
            form.setFileYn("Y");
            form.setNoticeTopYn("Y");
            form.setUseYn("Y");
            model.addAttribute("form", form);
        }
        model.addAttribute("sites", listSites());
        model.addAttribute("bbsTypes", BbsType.values());
        model.addAttribute("groupCandidates", java.util.Collections.emptyList());
        model.addAttribute("mode", "create");
        return "admin/system/board/form";
    }

    @GetMapping("/{bbsMasterId}/edit")
    public String editForm(@PathVariable String bbsMasterId,
                            Model model,
                            RedirectAttributes ra) {
        BbsMaster m = service.get(bbsMasterId);
        if (m == null) {
            ra.addFlashAttribute("error", "게시판을 찾을 수 없습니다.");
            return "redirect:/admin/system/board";
        }
        model.addAttribute("form", toForm(m));
        model.addAttribute("sites", listSites());
        model.addAttribute("bbsTypes", BbsType.values());
        model.addAttribute("groupCandidates", listGroupCandidates(m.getSiteId(), m.getBbsMasterId()));
        model.addAttribute("mode", "edit");
        return "admin/system/board/form";
    }

    // ==================================================================
    // CUD
    // ==================================================================

    @PostMapping
    public String create(@Valid @ModelAttribute("form") BbsMasterSaveForm form,
                          BindingResult br,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/board/new";
        }
        try {
            String id = service.create(form);
            log.info("===BBS_MASTER_CREATE ok id={} code={}", id, form.getBbsCode());
            ra.addFlashAttribute("message", "게시판을 등록했습니다.");
            String target = "/admin/system/board/" + id;
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            log.warn("BBS_MASTER_CREATE fail code={} reason={}", form.getBbsCode(), ex.getMessage());
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/board/new";
        } catch (Exception ex) {
            log.warn("BBS_MASTER_CREATE error code={}", form.getBbsCode(), ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "등록 중 오류가 발생했습니다.");
            return "redirect:/admin/system/board/new";
        }
    }

    @PostMapping("/{bbsMasterId}")
    public String update(@PathVariable String bbsMasterId,
                          @Valid @ModelAttribute("form") BbsMasterSaveForm form,
                          BindingResult br,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        form.setBbsMasterId(bbsMasterId);
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/board/" + bbsMasterId + "/edit";
        }
        try {
            service.update(form);
            log.info("===BBS_MASTER_UPDATE ok id={}", bbsMasterId);
            ra.addFlashAttribute("message", "게시판을 수정했습니다.");
            String target = "/admin/system/board/" + bbsMasterId;
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            log.warn("BBS_MASTER_UPDATE fail id={} reason={}", bbsMasterId, ex.getMessage());
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/board/" + bbsMasterId + "/edit";
        } catch (Exception ex) {
            log.warn("BBS_MASTER_UPDATE error id={}", bbsMasterId, ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "수정 중 오류가 발생했습니다.");
            return "redirect:/admin/system/board/" + bbsMasterId + "/edit";
        }
    }

    @PostMapping("/{bbsMasterId}/use")
    public String toggleUse(@PathVariable String bbsMasterId,
                              @RequestParam("active") boolean active,
                              RedirectAttributes ra,
                              HttpServletResponse res) {
        String redirect = "/admin/system/board/" + bbsMasterId;
        try {
            service.toggleUse(bbsMasterId, active);
            ra.addFlashAttribute("message", active ? "게시판을 사용 처리했습니다." : "게시판을 사용중지했습니다.");
            res.setHeader("HX-Redirect", redirect);
            return "redirect:" + redirect;
        } catch (IllegalArgumentException ex) {
            log.warn("BBS_MASTER_USE_TOGGLE fail id={} reason={}", bbsMasterId, ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:" + redirect;
        } catch (Exception ex) {
            log.warn("BBS_MASTER_USE_TOGGLE error id={}", bbsMasterId, ex);
            ra.addFlashAttribute("error", "처리 중 오류가 발생했습니다.");
            return "redirect:" + redirect;
        }
    }

    @DeleteMapping("/{bbsMasterId}")
    public String delete(@PathVariable String bbsMasterId,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        try {
            service.softDelete(bbsMasterId);
            log.info("===BBS_MASTER_DELETE ok id={}", bbsMasterId);
            ra.addFlashAttribute("message", "게시판을 삭제했습니다.");
            String target = "/admin/system/board";
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            log.warn("BBS_MASTER_DELETE fail id={} reason={}", bbsMasterId, ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/board/" + bbsMasterId;
        } catch (Exception ex) {
            log.warn("BBS_MASTER_DELETE error id={}", bbsMasterId, ex);
            ra.addFlashAttribute("error", "삭제 중 오류가 발생했습니다.");
            return "redirect:/admin/system/board/" + bbsMasterId;
        }
    }

    // ==================================================================
    // Excel
    // ==================================================================

    @PostMapping("/excel")
    public String excel(@Valid @ModelAttribute("excelReq") ExcelDownloadRequest excelReq,
                         BindingResult br,
                         @ModelAttribute("search") BbsMasterSearch search,
                         HttpServletRequest req,
                         HttpServletResponse res,
                         RedirectAttributes ra) throws IOException {
        if (br.hasErrors()) {
            ra.addFlashAttribute("error", "다운로드 사유는 10자 이상 입력해주세요.");
            return "redirect:/admin/system/board";
        }
        search.setPage(1);
        search.setPageSize(10_000);
        List<BbsMaster> rows = service.search(search);

        String excelFilename = excelWriter.write(res, "게시판", "gopcms_boards",
            new String[]{ "ID", "사이트", "코드", "이름", "타입",
                          "댓글", "파일", "최대수", "최대MB",
                          "익명", "공지상단", "읽기권한", "쓰기권한", "사용",
                          "수정자", "수정시각" },
            rows,
            (row, m) -> {
                row.createCell(0).setCellValue(nvl(m.getBbsMasterId()));
                row.createCell(1).setCellValue(nvl(m.getSiteCode()));
                row.createCell(2).setCellValue(nvl(m.getBbsCode()));
                row.createCell(3).setCellValue(nvl(m.getBbsName()));
                row.createCell(4).setCellValue(nvl(m.getBbsType()));
                row.createCell(5).setCellValue(nvl(m.getCommentYn()));
                row.createCell(6).setCellValue(nvl(m.getFileYn()));
                row.createCell(7).setCellValue(m.getFileCountMax());
                row.createCell(8).setCellValue(m.getFileSizeMaxMb());
                row.createCell(9).setCellValue(nvl(m.getAnonymousYn()));
                row.createCell(10).setCellValue(nvl(m.getNoticeTopYn()));
                row.createCell(11).setCellValue(nvl(m.getReadAuth()));
                row.createCell(12).setCellValue(nvl(m.getWriteAuth()));
                row.createCell(13).setCellValue(nvl(m.getUseYn()));
                row.createCell(14).setCellValue(nvl(m.getUpdatedBy()));
                row.createCell(15).setCellValue(str(m.getUpdatedAt()));
            });

        auditLogger.write(AuditEvent.of("EXCEL_DOWNLOAD", "tb_bbs_master")
            .withHttp(req.getMethod(), req.getRequestURI(),
                      req.getRemoteAddr(), req.getHeader("User-Agent"))
            .withResult("SUCCESS")
            .withAfter("{\"count\":" + rows.size()+",\"filename\":" + excelFilename+",\"keyword\":" + search.getKeyword()
                + ",\"reason\":" + JsonUtils.quote(excelReq.getDownloadReason()) + "}"));
        log.info("===BBS_MASTER_EXCEL ok count={} reason='{}'",
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

    private BbsMasterSaveForm toForm(BbsMaster m) {
        BbsMasterSaveForm f = new BbsMasterSaveForm();
        f.setBbsMasterId(m.getBbsMasterId());
        f.setSiteId(m.getSiteId());
        f.setBbsCode(m.getBbsCode());
        f.setBbsName(m.getBbsName());
        f.setBbsType(m.getBbsType());
        f.setCommentYn(m.getCommentYn());
        f.setFileYn(m.getFileYn());
        f.setFileCountMax(m.getFileCountMax());
        f.setFileSizeMaxMb((int) Math.max(1, m.getFileSizeMax() / (1024L * 1024L)));
        f.setAnonymousYn(m.getAnonymousYn());
        f.setNoticeTopYn(m.getNoticeTopYn());
        f.setReadAuth(m.getReadAuth());
        f.setWriteAuth(m.getWriteAuth());
        f.setDownloadAuth(m.getDownloadAuth());
        f.setUseYn(m.getUseYn());
        f.setDescription(m.getDescription());
        f.setGroupedBoardIds(m.getGroupedBoardIds());
        return f;
    }

    /**
     * 통합 게시판(전체글 보기) 대상 후보 — 활성 + 일반(비-aggregator) 게시판.
     * site 무관 (cross-site 통합 허용 정책).
     *
     * <p>예외 — {@code selfId} 가 주어진 경우 (수정 모드) 본인 마스터는 aggregator 여도 포함.
     * "자기 자신을 통합 대상에 포함" 정책을 허용하기 위함.
     *
     * @param siteId    수정 시 마스터의 site_id (UI 그룹핑용으로만 사용)
     * @param selfId    수정 시 본인 마스터 id. null 이면 신규 모드
     */
    private List<BbsMaster> listGroupCandidates(String siteId, String selfId) {
        BbsMasterSearch s = new BbsMasterSearch();
        s.setPage(1);
        s.setPageSizeUnbounded(2000);
        s.setUseYn("Y");
        List<BbsMaster> all = service.search(s);
        List<BbsMaster> filtered = new java.util.ArrayList<>();
        for (BbsMaster m : all) {
            // 일반 게시판은 모두 후보. 통합 게시판은 본인일 때만 후보 (자기 자신 포함 허용)
            if (m.isAggregator() && !java.util.Objects.equals(m.getBbsMasterId(), selfId)) continue;
            filtered.add(m);
        }
        return filtered;
    }
}
