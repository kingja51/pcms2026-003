package com.gonet.primary.notification.controller;

import com.gonet.common.dto.PageResponse;
import com.gonet.primary.notification.dto.Notification;
import com.gonet.primary.notification.dto.NotificationPref;
import com.gonet.primary.notification.dto.NotificationPrefForm;
import com.gonet.primary.notification.dto.NotificationSearch;
import com.gonet.primary.notification.dto.NotificationType;
import com.gonet.primary.notification.service.NotificationPrefService;
import com.gonet.primary.notification.service.NotificationService;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 회원/직원 인박스 — {@code /notification}.
 *
 * <p>본인 알림만 조회/처리 가능. recipient 검증은 Service/Mapper 단에서 강제 — 본인 외 알림은
 * UPDATE 0 row 로 묵음 폴백.
 */
@Controller
@RequestMapping("/notification")
public class NotificationUsrController {

    private static final Logger log = LoggerFactory.getLogger(NotificationUsrController.class);

    private final NotificationService     service;
    private final NotificationPrefService prefService;

    public NotificationUsrController(NotificationService service,
                                       NotificationPrefService prefService) {
        this.service     = service;
        this.prefService = prefService;
    }

    @GetMapping
    public String inbox(@ModelAttribute("search") NotificationSearch search,
                          @AuthenticationPrincipal CustomUserDetails user,
                          Model model) {
        if (user == null) return "redirect:/member/login";
        search.setRecipientUserId(user.getUserId());
        List<Notification> rows = service.search(search);
        int total = service.count(search);
        model.addAttribute("page",
            PageResponse.of(rows, search.getPage(), search.getPageSize(), total));
        model.addAttribute("unreadCount", service.unreadCount(user.getUserId()));
        model.addAttribute("types", NotificationType.values());
        return "front/notification/inbox";
    }

    @GetMapping("/{notificationId}")
    public String detail(@PathVariable String notificationId,
                          @AuthenticationPrincipal CustomUserDetails user,
                          RedirectAttributes ra) {
        if (user == null) return "redirect:/member/login";
        Notification n = service.get(notificationId);
        if (n == null || !user.getUserId().equals(n.getRecipientUserId())) {
            ra.addFlashAttribute("error", "알림을 찾을 수 없습니다.");
            return "redirect:/notification";
        }
        // read 처리 (멱등) → linkUrl 있으면 redirect, 없으면 인박스로
        service.markRead(notificationId, user.getUserId());
        if (n.getLinkUrl() != null && !n.getLinkUrl().isBlank()) {
            return "redirect:" + n.getLinkUrl();
        }
        return "redirect:/notification";
    }

    @PostMapping("/{notificationId}/read")
    public String markRead(@PathVariable String notificationId,
                            @AuthenticationPrincipal CustomUserDetails user,
                            HttpServletResponse res) {
        if (user == null) return "redirect:/member/login";
        service.markRead(notificationId, user.getUserId());
        res.setHeader("HX-Redirect", "/notification");
        return "redirect:/notification";
    }

    @PostMapping("/read-all")
    public String markAllRead(@AuthenticationPrincipal CustomUserDetails user,
                                RedirectAttributes ra,
                                HttpServletResponse res) {
        if (user == null) return "redirect:/member/login";
        int cnt = service.markAllRead(user.getUserId());
        ra.addFlashAttribute("message", cnt + "건을 읽음 처리했습니다.");
        res.setHeader("HX-Redirect", "/notification");
        return "redirect:/notification";
    }

    @PostMapping("/{notificationId}/delete")
    public String softDelete(@PathVariable String notificationId,
                              @AuthenticationPrincipal CustomUserDetails user,
                              RedirectAttributes ra,
                              HttpServletResponse res) {
        if (user == null) return "redirect:/member/login";
        service.softDelete(notificationId, user.getUserId());
        ra.addFlashAttribute("message", "알림을 삭제했습니다.");
        res.setHeader("HX-Redirect", "/notification");
        return "redirect:/notification";
    }

    // -------------------------------------------------------------------
    // 채널 preference 마이페이지 (Phase 2)
    // -------------------------------------------------------------------

    @GetMapping("/pref")
    public String prefForm(@AuthenticationPrincipal CustomUserDetails user, Model model) {
        if (user == null) return "redirect:/member/login";
        List<NotificationPref> rows = prefService.findByUser(user.getUserId());
        model.addAttribute("prefs", rows);
        model.addAttribute("types", NotificationType.values());
        return "front/notification/pref";
    }

    @PostMapping("/pref")
    public String prefSave(@AuthenticationPrincipal CustomUserDetails user,
                            @ModelAttribute NotificationPrefForm form,
                            RedirectAttributes ra,
                            HttpServletResponse res) {
        if (user == null) return "redirect:/member/login";
        // 본인 user_id 강제 — 타인 pref 변경 차단
        form.setUserId(user.getUserId());
        try {
            prefService.save(form);
            ra.addFlashAttribute("message", "알림 설정을 저장했습니다.");
        } catch (Exception ex) {
            log.warn("NOTIFICATION_PREF_SAVE_FAIL user={} reason={}",
                user.getUserId(), ex.getMessage());
            ra.addFlashAttribute("error", "저장 중 오류가 발생했습니다.");
        }
        res.setHeader("HX-Redirect", "/notification/pref");
        return "redirect:/notification/pref";
    }
}
