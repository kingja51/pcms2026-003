package com.gonet.primary.system.mail.controller;

import com.gonet.common.dto.PageResponse;
import com.gonet.common.mail.MailService;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import com.gonet.primary.system.mail.dto.MailTemplate;
import com.gonet.primary.system.mail.dto.MailTemplateSaveForm;
import com.gonet.primary.system.mail.dto.MailTemplateSearch;
import com.gonet.primary.system.mail.service.MailTemplateService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 메일 템플릿 관리 — {@code /admin/system/mail-template}.
 *
 * <p>기능:
 * <ul>
 *   <li>CRUD — 코드/제목/본문 HTML + 발신자/설명/변수 힌트</li>
 *   <li>미리보기 — 저장 전 샘플 변수로 렌더링 결과 확인 (POST /preview, HTML iframe 대상)</li>
 *   <li>테스트 발송 — 관리자 본인 이메일로 실발송 (차후 기능, 본 구현 1차에는 스킵)</li>
 * </ul>
 */
@Controller
@RequestMapping("/admin/system/mail-template")
public class MailTemplateMngController {

    private static final Logger log = LoggerFactory.getLogger(MailTemplateMngController.class);

    private final MailTemplateService service;
    private final MailService          mailService;

    public MailTemplateMngController(MailTemplateService service, MailService mailService) {
        this.service = service;
        this.mailService = mailService;
    }

    @GetMapping
    public String list(@ModelAttribute("search") MailTemplateSearch search, Model model) {
        List<MailTemplate> rows = service.search(search);
        int total = service.count(search);
        model.addAttribute("page",
            PageResponse.of(rows, search.getPage(), search.getPageSize(), total));
        return "admin/system/mail-template/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable String id, Model model, RedirectAttributes ra) {
        MailTemplate t = service.get(id);
        if (t == null) {
            ra.addFlashAttribute("error", "템플릿을 찾을 수 없습니다.");
            return "redirect:/admin/system/mail-template";
        }
        model.addAttribute("template", t);
        model.addAttribute("form",     toForm(t));
        model.addAttribute("mode",     "edit");
        return "admin/system/mail-template/form";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new MailTemplateSaveForm());
        }
        model.addAttribute("mode", "create");
        return "admin/system/mail-template/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") MailTemplateSaveForm form,
                          BindingResult br, RedirectAttributes ra, HttpServletResponse res) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/mail-template/new";
        }
        try {
            String id = service.create(form);
            log.info("===MAIL_TEMPLATE_CREATE ok id={} code={}", id, form.getTemplateCode());
            ra.addFlashAttribute("message", "메일 템플릿을 등록했습니다.");
            res.setHeader("HX-Redirect", "/admin/system/mail-template/" + id);
            return "redirect:/admin/system/mail-template/" + id;
        } catch (IllegalArgumentException ex) {
            log.warn("MAIL_TEMPLATE_CREATE fail code={} reason={}", form.getTemplateCode(), ex.getMessage());
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/mail-template/new";
        } catch (Exception ex) {
            log.warn("MAIL_TEMPLATE_CREATE error code={} reason={}", form.getTemplateCode(), ex.getMessage(), ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "등록 중 오류가 발생했습니다.");
            return "redirect:/admin/system/mail-template/new";
        }
    }

    @PostMapping("/{id}")
    public String update(@PathVariable String id,
                          @Valid @ModelAttribute("form") MailTemplateSaveForm form,
                          BindingResult br, RedirectAttributes ra, HttpServletResponse res) {
        form.setMailTemplateId(id);
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/mail-template/" + id;
        }
        try {
            service.update(form);
            log.info("===MAIL_TEMPLATE_UPDATE ok id={}", id);
            ra.addFlashAttribute("message", "메일 템플릿을 수정했습니다.");
            res.setHeader("HX-Redirect", "/admin/system/mail-template/" + id);
            return "redirect:/admin/system/mail-template/" + id;
        } catch (IllegalArgumentException ex) {
            log.warn("MAIL_TEMPLATE_UPDATE fail id={} reason={}", id, ex.getMessage());
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/mail-template/" + id;
        } catch (Exception ex) {
            log.warn("MAIL_TEMPLATE_UPDATE error id={} reason={}", id, ex.getMessage(), ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "수정 중 오류가 발생했습니다.");
            return "redirect:/admin/system/mail-template/" + id;
        }
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable String id, RedirectAttributes ra, HttpServletResponse res) {
        try {
            service.softDelete(id);
            log.info("===MAIL_TEMPLATE_DELETE ok id={}", id);
            ra.addFlashAttribute("message", "메일 템플릿을 삭제했습니다.");
            res.setHeader("HX-Redirect", "/admin/system/mail-template");
            return "redirect:/admin/system/mail-template";
        } catch (IllegalArgumentException ex) {
            log.warn("MAIL_TEMPLATE_DELETE fail id={} reason={}", id, ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/mail-template/" + id;
        } catch (Exception ex) {
            log.warn("MAIL_TEMPLATE_DELETE error id={} reason={}", id, ex.getMessage(), ex);
            ra.addFlashAttribute("error", "삭제 중 오류가 발생했습니다.");
            return "redirect:/admin/system/mail-template/" + id;
        }
    }

    // ==================================================================
    // 미리보기 — iframe srcdoc 으로 사용
    // ==================================================================

    /**
     * 저장되지 않은 subject/bodyHtml 을 즉시 렌더링해 HTML 로 반환.
     * 샘플 변수는 코드별 기본값을 자동 적용.
     */
    @PostMapping("/preview")
    public String preview(@RequestParam(required = false) String templateCode,
                           @RequestParam String subject,
                           @RequestParam String bodyHtml,
                           Model model) {
        Map<String, Object> sample = sampleModel(templateCode);
        MailService.RenderedMail r = mailService.renderRaw(subject, bodyHtml, sample);
        model.addAttribute("subject",  r.subject());
        model.addAttribute("bodyHtml", r.bodyHtml());
        return "admin/system/mail-template/preview";
    }

    // ==================================================================
    // 테스트 발송 — 현재 로그인한 관리자 본인 이메일로 실제 발송
    // ==================================================================

    @PostMapping("/{id}/send-test")
    public String sendTest(@PathVariable String id,
                            @AuthenticationPrincipal CustomUserDetails principal,
                            RedirectAttributes ra, HttpServletResponse res) {
        MailTemplate t = service.get(id);
        if (t == null) {
            ra.addFlashAttribute("error", "템플릿을 찾을 수 없습니다.");
            return "redirect:/admin/system/mail-template";
        }
        // 수신자: 본인 관리자 이메일 (CustomUserDetails 에 이메일이 없으므로 username 기반 fallback 은
        // 주소록 조회 필요. 단순 구현: 관리자 이메일이 필요하면 향후 AdminService.get(ud.getUserId())
        // 를 호출해 email 을 가져오도록 확장. 1차에는 시스템 기본 발신자에게 본인 발송 — 설정 검증용)
        String toSelf = t.getSenderEmail();  // 발신자에게 보내는 self-test (설정 확인용)
        if (toSelf == null || toSelf.isBlank()) {
            toSelf = "kingja51@gmail.com";   // 시스템 기본값 — 운영 배포 시 관리자 이메일로 교체
        }
        try {
            mailService.sendFromTemplate(t.getTemplateCode(), toSelf, sampleModel(t.getTemplateCode()));
            log.info("===MAIL_TEMPLATE_TEST ok id={} code={} to={}", id, t.getTemplateCode(), toSelf);
            ra.addFlashAttribute("message", "테스트 메일을 " + toSelf + " 로 발송했습니다.");
        } catch (Exception ex) {
            log.warn("MAIL_TEMPLATE_TEST fail id={} reason={}", id, ex.getMessage(), ex);
            ra.addFlashAttribute("error", "테스트 발송 실패: " + ex.getMessage());
        }
        res.setHeader("HX-Redirect", "/admin/system/mail-template/" + id);
        return "redirect:/admin/system/mail-template/" + id;
    }

    // ==================================================================
    // 내부 — 템플릿 코드별 샘플 변수
    // ==================================================================

    private Map<String, Object> sampleModel(String code) {
        Map<String, Object> m = new HashMap<>();
        m.put("memberName",        "홍길동");
        m.put("loginId",           "example_user");
        m.put("siteName",          "기본 사이트");
        m.put("sentAt",            LocalDateTime.now());
        m.put("changedAt",         LocalDateTime.now());
        m.put("withdrawAt",        LocalDateTime.now());
        m.put("dormantAt",         LocalDateTime.now().plusDays(30));
        m.put("lastLoginAt",       LocalDateTime.now().minusMonths(11));
        m.put("retentionExpireAt", LocalDateTime.now().plusYears(5));
        m.put("clientIp",          "192.168.*.*");
        m.put("userAgent",         "Mozilla/5.0 (Sample) Preview");
        m.put("tempPassword",      "Sample!Pw0rd");
        m.put("reason",            "USER_REQUEST");
        m.put("stage",             "NOTICE");
        m.put("loginUrl",          "/member/login");
        m.put("reactivateUrl",     "/member/reactivate");
        m.put("rejoinUrl",         "/member/join");
        m.put("supportUrl",        "mailto:kingja51@gmail.com");
        return m;
    }

    private MailTemplateSaveForm toForm(MailTemplate t) {
        MailTemplateSaveForm f = new MailTemplateSaveForm();
        f.setMailTemplateId(t.getMailTemplateId());
        f.setTemplateCode(t.getTemplateCode());
        f.setTemplateName(t.getTemplateName());
        f.setSubject(t.getSubject());
        f.setBodyHtml(t.getBodyHtml());
        f.setSenderEmail(t.getSenderEmail());
        f.setSenderName(t.getSenderName());
        f.setDescription(t.getDescription());
        f.setVariablesHint(t.getVariablesHint());
        f.setUseYn(t.getUseYn());
        return f;
    }
}
