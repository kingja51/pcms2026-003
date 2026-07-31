package com.gonet.primary.notification.controller;

import com.gonet.common.dto.PageResponse;
import com.gonet.primary.notification.dto.Notification;
import com.gonet.primary.notification.dto.NotificationPref;
import com.gonet.primary.notification.dto.NotificationPrefForm;
import com.gonet.primary.notification.dto.NotificationSearch;
import com.gonet.primary.notification.dto.NotificationSendForm;
import com.gonet.primary.notification.dto.NotificationType;
import com.gonet.primary.notification.service.NotificationPrefService;
import com.gonet.primary.notification.service.NotificationRetentionService;
import com.gonet.primary.notification.service.NotificationService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 알림 관리 — {@code /admin/notification}.
 *
 * <p>전체 알림 통합 list (recipient 무관) + 수동 발송 + 강제 삭제.
 */
@Controller
@RequestMapping("/admin/notification")
public class NotificationMngController {

    private static final Logger log = LoggerFactory.getLogger(NotificationMngController.class);

    private final NotificationService          service;
    private final NotificationPrefService      prefService;
    private final NotificationRetentionService retentionService;

    public NotificationMngController(NotificationService service,
                                       NotificationPrefService prefService,
                                       NotificationRetentionService retentionService) {
        this.service          = service;
        this.prefService      = prefService;
        this.retentionService = retentionService;
    }

    @GetMapping
    public String list(@ModelAttribute("search") NotificationSearch search, Model model) {
        // 관리자 통합 list — recipientUserId 강제 안 함
        List<Notification> rows = service.search(search);
        int total = service.count(search);
        model.addAttribute("page",
            PageResponse.of(rows, search.getPage(), search.getPageSize(), total));
        model.addAttribute("types", NotificationType.values());
        if (!model.containsAttribute("sendForm")) {
            model.addAttribute("sendForm", new NotificationSendForm());
        }
        return "admin/system/notification/list";
    }

    @GetMapping("/{notificationId}")
    public String detail(@PathVariable String notificationId, Model model, RedirectAttributes ra) {
        Notification n = service.get(notificationId);
        if (n == null) {
            ra.addFlashAttribute("error", "알림을 찾을 수 없습니다.");
            return "redirect:/admin/notification";
        }
        model.addAttribute("notification", n);
        return "admin/system/notification/detail";
    }

    @PostMapping("/send")
    public String send(@Valid @ModelAttribute("sendForm") NotificationSendForm form,
                        BindingResult br,
                        RedirectAttributes ra,
                        HttpServletResponse res) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("sendForm", form);
            ra.addFlashAttribute("error", "수신자/제목을 다시 확인하세요.");
            return "redirect:/admin/notification";
        }
        try {
            List<String> ids = parseCsv(form.getRecipientUserIdsCsv());
            if (ids.isEmpty()) {
                ra.addFlashAttribute("error", "수신자 user_seq 가 비어 있습니다.");
                return "redirect:/admin/notification";
            }
            int sent = service.sendToMany(ids,
                NotificationType.safeParse(form.getNotificationType()),
                form.getTitle(), form.getBody(), form.getLinkUrl(),
                "ADMIN_MANUAL", null);
            ra.addFlashAttribute("message", sent + "건을 발송했습니다.");
            res.setHeader("HX-Redirect", "/admin/notification");
            return "redirect:/admin/notification";
        } catch (Exception ex) {
            log.warn("NOTIFICATION_SEND_ADMIN error", ex);
            ra.addFlashAttribute("error", "발송 중 오류가 발생했습니다.");
            return "redirect:/admin/notification";
        }
    }

    @PostMapping("/{notificationId}/delete")
    public String delete(@PathVariable String notificationId,
                          RedirectAttributes ra, HttpServletResponse res) {
        try {
            int cnt = service.adminSoftDelete(notificationId);
            ra.addFlashAttribute("message", cnt > 0 ? "알림을 삭제했습니다." : "이미 삭제된 알림입니다.");
            res.setHeader("HX-Redirect", "/admin/notification");
            return "redirect:/admin/notification";
        } catch (Exception ex) {
            log.warn("NOTIFICATION_DELETE_ADMIN error", ex);
            ra.addFlashAttribute("error", "삭제 중 오류가 발생했습니다.");
            return "redirect:/admin/notification";
        }
    }

    // -------------------------------------------------------------------
    // 채널 preference 관리자 조회/변경 (Phase 2)
    // -------------------------------------------------------------------

    @GetMapping("/pref/{userId}")
    public String prefList(@PathVariable String userId, Model model) {
        List<NotificationPref> rows = prefService.findByUser(userId);
        model.addAttribute("targetUserId", userId);
        model.addAttribute("prefs", rows);
        model.addAttribute("types", NotificationType.values());
        return "admin/system/notification/pref";
    }

    @PostMapping("/pref/{userId}")
    public String prefSave(@PathVariable String userId,
                            @ModelAttribute NotificationPrefForm form,
                            RedirectAttributes ra, HttpServletResponse res) {
        form.setUserId(userId);
        try {
            prefService.save(form);
            ra.addFlashAttribute("message", "사용자 알림 설정을 변경했습니다.");
        } catch (Exception ex) {
            log.warn("NOTIFICATION_PREF_ADMIN_SAVE_FAIL user={} reason={}",
                userId, ex.getMessage());
            ra.addFlashAttribute("error", "저장 중 오류가 발생했습니다.");
        }
        String redirect = "/admin/notification/pref/" + userId;
        res.setHeader("HX-Redirect", redirect);
        return "redirect:" + redirect;
    }

    // -------------------------------------------------------------------
    // 보존 정책 — preview + 수동 트리거 (Phase 2 D 보완)
    // -------------------------------------------------------------------

    @GetMapping("/retention")
    public String retentionPreview(Model model) {
        model.addAttribute("readDays",  retentionService.readDays());
        model.addAttribute("purgeDays", retentionService.purgeDays());
        model.addAttribute("preview",   retentionService.preview());
        return "admin/system/notification/retention";
    }

    @PostMapping("/retention/run")
    public String retentionRun(RedirectAttributes ra, HttpServletResponse res) {
        try {
            NotificationRetentionService.Result r = retentionService.runNow();
            ra.addFlashAttribute("message",
                "보존 정책 실행 완료 — soft delete " + r.softDeleted() + "건, 물리 정리 " + r.purged() + "건");
        } catch (Exception ex) {
            log.error("NOTIFICATION_RETENTION_MANUAL_FAIL", ex);
            ra.addFlashAttribute("error", "실행 중 오류가 발생했습니다.");
        }
        res.setHeader("HX-Redirect", "/admin/notification/retention");
        return "redirect:/admin/notification/retention";
    }

    private static List<String> parseCsv(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) return out;
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
}
