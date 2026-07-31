package com.gonet.primary.system.menu.controller;

import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.dto.ExcelDownloadRequest;
import com.gonet.common.excel.ExcelResponseWriter;
import com.gonet.common.util.JsonUtils;
import com.gonet.primary.system.menu.dto.Menu;
import com.gonet.primary.system.menu.dto.MenuSaveForm;
import com.gonet.primary.system.menu.dto.MenuSearch;
import com.gonet.primary.system.menu.service.MenuService;
import com.gonet.primary.system.site.dto.Site;
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
 * 메뉴 관리 Controller — 사이트 종속 CRUD + 형제 정렬 이동 + 엑셀.
 *
 * <p>URL prefix: {@code /admin/system/site/{siteId}/menu}.
 */
@Controller
@RequestMapping("/admin/system/site/{siteId}/menu")
public class MenuMngController {

    private static final Logger log = LoggerFactory.getLogger(MenuMngController.class);

    private final MenuService service;
    private final SiteService siteService;
    private final AuditLogger auditLogger;
    private final ExcelResponseWriter excelWriter;

    public MenuMngController(MenuService service, SiteService siteService,
                              AuditLogger auditLogger,
                              ExcelResponseWriter excelWriter) {
        this.service = service;
        this.siteService = siteService;
        this.auditLogger = auditLogger;
        this.excelWriter = excelWriter;
    }

    // ==================================================================
    // 조회
    // ==================================================================

    @GetMapping
    public String list(@PathVariable String siteId,
                        @ModelAttribute("search") MenuSearch search,
                        Model model,
                        RedirectAttributes ra) {
        Site site = siteService.get(siteId);
        if (site == null) {
            ra.addFlashAttribute("error", "사이트를 찾을 수 없습니다.");
            return "redirect:/admin/system/site";
        }
        search.setSiteId(siteId);

        boolean hasKeyword = search.getKeyword() != null && !search.getKeyword().isBlank();
        model.addAttribute("site", site);
        model.addAttribute("hasKeyword", hasKeyword);
        if (hasKeyword) {
            model.addAttribute("rows", service.searchFlat(search));
        } else {
            model.addAttribute("tree", service.tree(search));
        }
        return "admin/system/menu/list";
    }

    @GetMapping("/{menuId}")
    public String detail(@PathVariable String siteId,
                          @PathVariable String menuId,
                          Model model,
                          RedirectAttributes ra) {
        Menu m = service.get(menuId);
        if (m == null || !siteId.equals(m.getSiteId())) {
            ra.addFlashAttribute("error", "메뉴를 찾을 수 없습니다.");
            return "redirect:/admin/system/site/" + siteId + "/menu";
        }
        model.addAttribute("site", siteService.get(siteId));
        model.addAttribute("menu", m);
        return "admin/system/menu/detail";
    }

    // ==================================================================
    // Form
    // ==================================================================

    @GetMapping("/new")
    public String createForm(@PathVariable String siteId,
                              @ModelAttribute("parentMenuId") String parentMenuId,
                              Model model,
                              RedirectAttributes ra) {
        Site site = siteService.get(siteId);
        if (site == null) {
            ra.addFlashAttribute("error", "사이트를 찾을 수 없습니다.");
            return "redirect:/admin/system/site";
        }
        if (!model.containsAttribute("form")) {
            MenuSaveForm form = new MenuSaveForm();
            form.setSiteId(siteId);
            if (parentMenuId != null && !parentMenuId.isBlank()) {
                form.setParentMenuId(parentMenuId);
            }
            form.setMenuType(Menu.TYPE_CONTENT);
            model.addAttribute("form", form);
        }
        model.addAttribute("site", site);
        model.addAttribute("mode", "create");
        model.addAttribute("parents", service.findParentCandidates(siteId, null));
        return "admin/system/menu/form";
    }

    @GetMapping("/{menuId}/edit")
    public String editForm(@PathVariable String siteId,
                            @PathVariable String menuId,
                            Model model,
                            RedirectAttributes ra) {
        Menu m = service.get(menuId);
        if (m == null || !siteId.equals(m.getSiteId())) {
            ra.addFlashAttribute("error", "메뉴를 찾을 수 없습니다.");
            return "redirect:/admin/system/site/" + siteId + "/menu";
        }
        model.addAttribute("site", siteService.get(siteId));
        model.addAttribute("form", toForm(m));
        model.addAttribute("mode", "edit");
        model.addAttribute("parents", service.findParentCandidates(siteId, menuId));
        return "admin/system/menu/form";
    }

    // ==================================================================
    // CUD
    // ==================================================================

    @PostMapping
    public String create(@PathVariable String siteId,
                          @Valid @ModelAttribute("form") MenuSaveForm form,
                          BindingResult br,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        form.setSiteId(siteId);
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/site/" + siteId + "/menu/new";
        }
        try {
            String menuId = service.create(form);
            log.info("===MENU_CREATE ok siteId={} menuId={} type={}", siteId, menuId, form.getMenuType());
            ra.addFlashAttribute("message", "메뉴를 등록했습니다.");
            String target = "/admin/system/site/" + siteId + "/menu/" + menuId;
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            log.warn("MENU_CREATE fail siteId={} reason={}", siteId, ex.getMessage());
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/site/" + siteId + "/menu/new";
        } catch (Exception ex) {
            log.warn("MENU_CREATE error siteId={} reason={}", siteId, ex.getMessage(), ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "등록 중 오류가 발생했습니다.");
            return "redirect:/admin/system/site/" + siteId + "/menu/new";
        }
    }

    @PostMapping("/{menuId}")
    public String update(@PathVariable String siteId,
                          @PathVariable String menuId,
                          @Valid @ModelAttribute("form") MenuSaveForm form,
                          BindingResult br,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        form.setSiteId(siteId);
        form.setMenuId(menuId);
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/site/" + siteId + "/menu/" + menuId + "/edit";
        }
        try {
            service.update(form);
            log.info("===MENU_UPDATE ok siteId={} menuId={}", siteId, menuId);
            ra.addFlashAttribute("message", "메뉴를 수정했습니다.");
            String target = "/admin/system/site/" + siteId + "/menu/" + menuId;
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            log.warn("MENU_UPDATE fail siteId={} menuId={} reason={}", siteId, menuId, ex.getMessage());
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/site/" + siteId + "/menu/" + menuId + "/edit";
        } catch (Exception ex) {
            log.warn("MENU_UPDATE error siteId={} menuId={} reason={}", siteId, menuId, ex.getMessage(), ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "수정 중 오류가 발생했습니다.");
            return "redirect:/admin/system/site/" + siteId + "/menu/" + menuId + "/edit";
        }
    }

    @PostMapping("/{menuId}/move")
    public String move(@PathVariable String siteId,
                        @PathVariable String menuId,
                        @ModelAttribute("direction") String direction,
                        RedirectAttributes ra,
                        HttpServletResponse res) {
        try {
            service.move(menuId, direction);
            log.info("===MENU_MOVE ok siteId={} menuId={} direction={}", siteId, menuId, direction);
            String target = "/admin/system/site/" + siteId + "/menu";
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            log.warn("MENU_MOVE fail siteId={} menuId={} direction={} reason={}",
                siteId, menuId, direction, ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/site/" + siteId + "/menu";
        } catch (Exception ex) {
            log.warn("MENU_MOVE error siteId={} menuId={} reason={}", siteId, menuId, ex.getMessage(), ex);
            ra.addFlashAttribute("error", "정렬 이동 중 오류가 발생했습니다.");
            return "redirect:/admin/system/site/" + siteId + "/menu";
        }
    }

    @DeleteMapping("/{menuId}")
    public String delete(@PathVariable String siteId,
                          @PathVariable String menuId,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        try {
            service.softDelete(menuId);
            log.info("===MENU_DELETE ok siteId={} menuId={}", siteId, menuId);
            ra.addFlashAttribute("message", "메뉴를 삭제했습니다.");
            String target = "/admin/system/site/" + siteId + "/menu";
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            log.warn("MENU_DELETE fail siteId={} menuId={} reason={}", siteId, menuId, ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/site/" + siteId + "/menu/" + menuId;
        } catch (Exception ex) {
            log.warn("MENU_DELETE error siteId={} menuId={} reason={}", siteId, menuId, ex.getMessage(), ex);
            ra.addFlashAttribute("error", "삭제 중 오류가 발생했습니다.");
            return "redirect:/admin/system/site/" + siteId + "/menu/" + menuId;
        }
    }

    // ==================================================================
    // Excel
    // ==================================================================

    @PostMapping("/excel")
    public String excel(@PathVariable String siteId,
                         @Valid @ModelAttribute("excelReq") ExcelDownloadRequest excelReq,
                         BindingResult br,
                         @ModelAttribute("search") MenuSearch search,
                         HttpServletRequest req,
                         HttpServletResponse res,
                         RedirectAttributes ra) throws IOException {
        if (br.hasErrors()) {
            ra.addFlashAttribute("error", "다운로드 사유는 10자 이상 입력해주세요.");
            return "redirect:/admin/system/site/" + siteId + "/menu";
        }
        search.setSiteId(siteId);
        List<Menu> rows = service.searchFlat(search);

        String excelFilename = excelWriter.write(res, "메뉴", "gopcms_menus_" + siteId,
            new String[]{ "메뉴ID", "사이트", "상위메뉴", "메뉴명", "타입",
                          "링크URL", "링크대상ID", "정렬", "깊이", "인증필요", "사용", "등록일시" },
            rows,
            (row, m) -> {
                row.createCell(0).setCellValue(nvl(m.getMenuId()));
                row.createCell(1).setCellValue(nvl(m.getSiteCode()));
                row.createCell(2).setCellValue(nvl(m.getParentMenuName()));
                row.createCell(3).setCellValue(nvl(m.getMenuName()));
                row.createCell(4).setCellValue(nvl(m.getMenuType()));
                row.createCell(5).setCellValue(nvl(m.getLinkUrl()));
                row.createCell(6).setCellValue(nvl(m.getLinkTargetId()));
                row.createCell(7).setCellValue(m.getSortOrder());
                row.createCell(8).setCellValue(m.getDepth());
                row.createCell(9).setCellValue(nvl(m.getAuthRequiredYn()));
                row.createCell(10).setCellValue(nvl(m.getUseYn()));
                row.createCell(11).setCellValue(str(m.getCreatedAt()));
            });

        auditLogger.write(AuditEvent.of("EXCEL_DOWNLOAD", "tb_menu")
            .withHttp(req.getMethod(), req.getRequestURI(),
                      req.getRemoteAddr(), req.getHeader("User-Agent"))
            .withTarget(siteId)
            .withResult("SUCCESS")
            .withAfter("{\"count\":" + rows.size()+",\"filename\":" + excelFilename+",\"keyword\":" + search.getKeyword()
                + ",\"reason\":" + JsonUtils.quote(excelReq.getDownloadReason()) + "}"));
        log.info("===MENU_EXCEL ok siteId={} count={} reason='{}'",
            siteId, rows.size(), excelReq.getDownloadReason());
        return null;
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private MenuSaveForm toForm(Menu m) {
        MenuSaveForm f = new MenuSaveForm();
        f.setMenuId(m.getMenuId());
        f.setSiteId(m.getSiteId());
        f.setParentMenuId(m.getParentMenuId());
        f.setMenuName(m.getMenuName());
        f.setMenuType(m.getMenuType());
        f.setLinkTargetId(m.getLinkTargetId());
        f.setLinkUrl(m.getLinkUrl());
        f.setAuthRequiredYn(m.getAuthRequiredYn());
        f.setUseYn(m.getUseYn());
        return f;
    }

}
